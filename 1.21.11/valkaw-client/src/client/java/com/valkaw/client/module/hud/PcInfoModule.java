package com.valkaw.client.module.hud;

import com.google.gson.JsonObject;
import com.valkaw.client.hud.Hud;
import com.valkaw.client.module.HudModule;
import com.valkaw.client.module.Layout;
import com.valkaw.client.module.Setting;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.opengl.GL11;

/** CPU/GPU load and the memory allocated to this JVM. Samples twice a second. */
public class PcInfoModule extends HudModule {
	private static final long SAMPLE_MS = 500L;
	private static final Path DRM = Path.of("/sys/class/drm");
	private static Path gpuPath;
	private static boolean gpuResolved;
	private static String gpuName;
	private static final AtomicInteger WIN_BUSY = new AtomicInteger(-1);
	private static volatile boolean winStarted;

	private Layout layout = Layout.VERTICAL;
	private boolean ramPercent;
	private long lastSample;
	private String cpu = "0%";
	private String gpu = "N/A";
	private String ram = "0/0 MB";

	public PcInfoModule() {
		super("pc-info", "PC Info", 120, 70);
		setEnabled(true);
	}

	@Override
	protected int previewWidth() {
		return 140;
	}

	@Override
	protected int previewHeight() {
		return 33;
	}

	@Override
	public void render(GuiGraphics graphics, Font font) {
		sample();
		String cpuLabel = Hud.label("CPU");
		String gpuLabel = "[GPU]";
		String ramLabel = "[RAM]";
		if (layout == Layout.HORIZONTAL) {
			int cursor = x;
			cursor += Hud.chip(graphics, font, cursor, y, "CPU", cpu) + 2;
			cursor += Hud.chipRaw(graphics, font, cursor, y, gpuLabel, gpu) + 2;
			w = cursor - x + Hud.chipRaw(graphics, font, cursor, y, ramLabel, ram);
			h = 11;
			return;
		}
		// Each plate still hugs its own line, but all rows share one right edge:
		// the values stay in a single right-aligned column and short lines just
		// start further right.
		int cpuWidth = 8 + Hud.width(font, cpuLabel) + Hud.width(font, cpu);
		int gpuWidth = 8 + Hud.width(font, gpuLabel) + Hud.width(font, gpu);
		int ramWidth = 8 + Hud.width(font, ramLabel) + Hud.width(font, ram);
		int width = Math.max(cpuWidth, Math.max(gpuWidth, ramWidth));
		row(graphics, font, x + width - cpuWidth, y, cpuWidth, cpuLabel, cpu);
		row(graphics, font, x + width - gpuWidth, y + 11, gpuWidth, gpuLabel, gpu);
		row(graphics, font, x + width - ramWidth, y + 22, ramWidth, ramLabel, ram);
		w = width;
		h = 33;
	}

	/** One "LABEL value" line with the text right-aligned: 3px label pad, 3px
	 * gap, 2px right margin, same as before but the plate ends where this
	 * line does. */
	private void row(GuiGraphics graphics, Font font, int x, int y, int width, String label, String value) {
		int bx = Hud.plateX(x);
		graphics.fill(bx, y, bx + width, y + 11, Hud.BG);
		int valueX = bx + width - 2 - Hud.width(font, value);
		Hud.text(graphics, font, value, valueX, y + 2);
		Hud.text(graphics, font, label, valueX - 3 - Hud.width(font, label), y + 2);
	}

	private void sample() {
		long now = System.currentTimeMillis();
		if (now - lastSample < SAMPLE_MS) {
			return;
		}
		lastSample = now;
		double load = -1.0;
		try {
			java.lang.management.OperatingSystemMXBean base = ManagementFactory.getOperatingSystemMXBean();
			if (base instanceof com.sun.management.OperatingSystemMXBean os) {
				load = os.getCpuLoad();
			}
		} catch (Throwable ignored) {
			// Fall back to load average below.
		}
		if (load < 0.0) {
			double average = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
			int cores = Runtime.getRuntime().availableProcessors();
			load = average < 0.0 || cores <= 0 ? 0.0 : Math.min(1.0, average / cores);
		}
		cpu = String.format(Locale.ROOT, "%.0f%%", Math.max(0.0, Math.min(1.0, load)) * 100.0);

		Runtime runtime = Runtime.getRuntime();
		long allocated = runtime.totalMemory();
		long used = allocated - runtime.freeMemory();
		if (ramPercent) {
			ram = String.format(Locale.ROOT, "%.0f%%", allocated <= 0 ? 0.0 : used * 100.0 / allocated);
		} else {
			double mb = 1024.0 * 1024.0;
			ram = String.format(Locale.ROOT, "%.0f/%.0f MB", used / mb, allocated / mb);
		}

		int busy = gpuBusyPercent();
		if (busy < 0) {
			busy = windowsGpuBusyPercent();
		}
		gpu = busy < 0 ? rendererName() : busy + "%";
	}

	private static int gpuBusyPercent() {
		// shortcut: the card path is resolved once; a GPU hot-plugged later needs a restart.
		if (!gpuResolved) {
			gpuResolved = true;
			gpuPath = resolveGpuBusyPath();
		}
		if (gpuPath == null) {
			return -1;
		}
		try {
			return Integer.parseInt(Files.readString(gpuPath).trim());
		} catch (Exception ignored) {
			return -1;
		}
	}

	private static Path resolveGpuBusyPath() {
		if (!Files.isDirectory(DRM)) {
			return null;
		}
		try (Stream<Path> cards = Files.list(DRM)) {
			return cards.filter(path -> path.getFileName().toString().startsWith("card"))
					.map(path -> path.resolve("device/gpu_busy_percent"))
					.filter(Files::isReadable)
					.findFirst()
					.orElse(null);
		} catch (Exception ignored) {
			return null;
		}
	}

	/** Fallback when no utilization source exists (Windows / non-AMD): show the GPU name. */
	private static String rendererName() {
		if (gpuName != null) {
			return gpuName;
		}
		try {
			gpuName = GL11.glGetString(GL11.GL_RENDERER);
		} catch (Exception ignored) {
		}
		return gpuName != null ? gpuName : "N/A";
	}

	/** Windows-only: sample total GPU utilization from Windows performance counters, in the background. */
	private static int windowsGpuBusyPercent() {
		if (!winStarted) {
			winStarted = true;
			Executors.newSingleThreadExecutor().execute(() -> {
				while (true) {
					try {
						int pct = queryWindowsGpuBusy();
						WIN_BUSY.set(pct < 0 ? -1 : Math.min(pct, 100));
					} catch (Exception ignored) {
						WIN_BUSY.set(-1);
					}
					try {
						Thread.sleep(2000);
					} catch (InterruptedException e) {
						return;
					}
				}
			});
		}
		return WIN_BUSY.get();
	}

	// shortcut: sums every "GPU Engine" utilization counter (can overshoot 100% when
	// several engines run at once; capped at 100). Needs Windows perfmon; Linux uses sysfs.
	private static int queryWindowsGpuBusy() {
		try {
			Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command",
					"$s=(Get-Counter '\\GPU Engine(*)\\Utilization Percentage' -ErrorAction SilentlyContinue).CounterSamples;",
					"$m=$s | Measure-Object -Property CookedValue -Sum;",
					"if($m -and $m.Sum -ne $null){[math]::Round($m.Sum)}else{'-1'}").start();
			String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			p.waitFor();
			return Integer.parseInt(out);
		} catch (Exception e) {
			return -1;
		}
	}

	@Override
	public List<Setting> settings() {
		return List.of(
				Setting.choice("LAYOUT", () -> layout.name(), () -> layout = layout.next()),
				Setting.choice("RAM", () -> ramPercent ? "PERCENT" : "USED/ALLOC",
						() -> ramPercent = !ramPercent));
	}

	@Override
	public String option() {
		return layout.name();
	}

	@Override
	public void cycleOption() {
		layout = layout.next();
	}

	@Override
	public void writeExtra(JsonObject json) {
		super.writeExtra(json);
		json.addProperty("layout", layout.name());
		json.addProperty("ramPercent", ramPercent);
	}

	@Override
	public void readExtra(JsonObject json) {
		super.readExtra(json);
		if (json.has("layout")) {
			layout = Layout.parse(json.get("layout").getAsString());
		}
		if (json.has("ramPercent")) {
			ramPercent = json.get("ramPercent").getAsBoolean();
		}
	}
}
