package com.valkaw.client.module.hud;

import com.google.gson.JsonObject;
import com.valkaw.client.hud.Hud;
import com.valkaw.client.module.Anchor;
import com.valkaw.client.module.HudModule;
import com.valkaw.client.module.Layout;
import com.valkaw.client.module.Setting;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** CPU/GPU load and the memory allocated to this JVM. Samples twice a second. */
public class PcInfoModule extends HudModule {
	private static final long SAMPLE_MS = 500L;
	private static final Path DRM = Path.of("/sys/class/drm");
	private static Path gpuPath;
	private static boolean gpuResolved;

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
		if (layout == Layout.HORIZONTAL) {
			int cursor = x;
			cursor += Hud.chip(graphics, font, cursor, y, "CPU", cpu) + 2;
			cursor += Hud.chipRaw(graphics, font, cursor, y, "GPU", gpu) + 2;
			w = cursor - x + Hud.chipRaw(graphics, font, cursor, y, "RAM", ram);
			h = 11;
			return;
		}
		// Rows share one width so the labels line up on the left and the values
		// line up on the right, whichever side of the screen the HUD sits on.
		int labelWidth = Math.max(Hud.width(font, cpuLabel), Math.max(Hud.width(font, "GPU"), Hud.width(font, "RAM")));
		int valueWidth = Math.max(Hud.width(font, cpu), Math.max(Hud.width(font, gpu), Hud.width(font, ram)));
		int rowWidth = 14 + labelWidth + valueWidth;
		boolean flip = anchor() == Anchor.RIGHT;
		row(graphics, font, x, y, rowWidth, cpuLabel, cpu, flip);
		row(graphics, font, x, y + 11, rowWidth, "GPU", gpu, flip);
		row(graphics, font, x, y + 22, rowWidth, "RAM", ram, flip);
		w = rowWidth;
		h = 33;
	}

	/** One "LABEL value" line; the value is pushed to the opposite edge of the row. */
	private void row(GuiGraphics graphics, Font font, int x, int y, int width, String label, String value, boolean flip) {
		graphics.fill(x, y, x + width, y + 11, Hud.BG);
		if (flip) {
			graphics.fill(x + width - 1, y, x + width, y + 11, Hud.color());
		} else {
			graphics.fill(x, y, x + 1, y + 11, Hud.color());
		}
		Hud.text(graphics, font, label, x + 4, y + 2);
		Hud.text(graphics, font, value, x + width - 4 - Hud.width(font, value), y + 2);
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
		gpu = busy < 0 ? "N/A" : busy + "%";
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
