package com.valkaw.client.module.hud;

import com.google.gson.JsonObject;
import com.valkaw.client.hud.Hud;
import com.valkaw.client.module.HudModule;
import com.valkaw.client.module.Layout;
import com.valkaw.client.module.Setting;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Local machine date/time with validated Java DateTimeFormatter patterns. */
public class TimeModule extends HudModule {
	private static final String DEFAULT_DAY = "dd/MM/yyyy";
	private static final String DEFAULT_TIME = "HH:mm:ss";

	private Layout layout = Layout.VERTICAL;
	private String dayPattern = DEFAULT_DAY;
	private String timePattern = DEFAULT_TIME;
	private DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern(DEFAULT_DAY);
	private DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(DEFAULT_TIME);
	private long lastClock;
	private String day = "--/--/----";
	private String clock = "--:--:--";

	public TimeModule() {
		super("time", "Time", 4, 160);
		setEnabled(true);
	}

	@Override
	protected int previewWidth() {
		return 110;
	}

	@Override
	protected int previewHeight() {
		return 22;
	}

	@Override
	public void render(GuiGraphics graphics, Font font) {
		long now = System.currentTimeMillis();
		if (now - lastClock >= 1000L) {
			lastClock = now;
			day = LocalDate.now().format(dayFormatter);
			clock = LocalTime.now().format(timeFormatter);
		}
		if (layout == Layout.HORIZONTAL) {
			int first = Hud.chip(graphics, font, x, y, "DAY", day);
			w = first + 2 + Hud.chipRaw(graphics, font, x + first + 2, y, "TIME", clock);
			h = 11;
		} else {
			w = Hud.chip(graphics, font, x, y, "DAY", day);
			w = Math.max(w, Hud.chipRaw(graphics, font, x, y + 11, "TIME", clock));
			h = 22;
		}
	}

	private void setDayPattern(String pattern) {
		try {
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
			dayPattern = pattern;
			dayFormatter = formatter;
		} catch (IllegalArgumentException ignored) {
			// Keep the previous valid pattern.
		}
	}

	private void setTimePattern(String pattern) {
		try {
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
			timePattern = pattern;
			timeFormatter = formatter;
		} catch (IllegalArgumentException ignored) {
			// Keep the previous valid pattern.
		}
	}

	private static final String[] DAY_PRESETS = {"dd/MM/yyyy", "MM/dd/yyyy", "yyyy-MM-dd"};
	private static final String[] TIME_PRESETS = {"HH:mm:ss", "hh:mm:ss a", "HH:mm"};

	@Override
	public List<Setting> settings() {
		return List.of(
				Setting.choice("LAYOUT", () -> layout.name(), () -> layout = layout.next()),
				Setting.choice("DAY PRESET", () -> dayPattern, () -> setDayPattern(nextPreset(DAY_PRESETS, dayPattern))),
				Setting.choice("TIME PRESET", () -> timePattern,
						() -> setTimePattern(nextPreset(TIME_PRESETS, timePattern))),
				Setting.text("DAY FORMAT", () -> dayPattern, this::setDayPattern),
				Setting.text("TIME FORMAT", () -> timePattern, this::setTimePattern));
	}

	private static String nextPreset(String[] presets, String current) {
		for (int i = 0; i < presets.length; i++) {
			if (presets[i].equals(current)) {
				return presets[(i + 1) % presets.length];
			}
		}
		return presets[0];
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
		json.addProperty("dayPattern", dayPattern);
		json.addProperty("timePattern", timePattern);
	}

	@Override
	public void readExtra(JsonObject json) {
		super.readExtra(json);
		if (json.has("layout")) {
			layout = Layout.parse(json.get("layout").getAsString());
		}
		if (json.has("dayPattern")) {
			setDayPattern(json.get("dayPattern").getAsString());
		}
		if (json.has("timePattern")) {
			setTimePattern(json.get("timePattern").getAsString());
		}
	}
}
