package com.valkaw.client.module.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;
import com.valkaw.client.module.Setting;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/** Replaces the vanilla crosshair while enabled; presets or a custom 15x15 pixel grid. */
public class CrosshairModule extends Module {
	public static final int GRID = 15;

	public enum Pattern {
		CROSS, DOT, CIRCLE, T, ARROW;

		public Pattern next() {
			return values()[(ordinal() + 1) % values().length];
		}

		public static Pattern parse(String name) {
			for (Pattern pattern : values()) {
				if (pattern.name().equalsIgnoreCase(name)) {
					return pattern;
				}
			}
			return CROSS;
		}
	}

	private Pattern pattern = Pattern.CROSS;
	private boolean custom;
	private final boolean[][] pixels = new boolean[GRID][GRID];

	public CrosshairModule() {
		super("crosshair", "Crosshair", Category.HUD);
		setEnabled(true);
	}

	@Override
	public boolean keybindable() {
		return false;
	}

	@Override
	public List<Setting> settings() {
		return List.of(
				Setting.choice("PATTERN", () -> custom ? "CUSTOM" : pattern.name(), () -> {
					custom = false;
					pattern = pattern.next();
				}),
				Setting.choice("CUSTOM", () -> custom ? "ON" : "OFF", () -> custom = !custom));
	}

	public boolean custom() {
		return custom;
	}

	public boolean pixel(int x, int y) {
		return x >= 0 && y >= 0 && x < GRID && y < GRID && pixels[y][x];
	}

	public void setCustom(boolean[][] source, boolean enabled) {
		for (int y = 0; y < GRID; y++) {
			for (int x = 0; x < GRID; x++) {
				pixels[y][x] = source[y][x];
			}
		}
		custom = enabled;
	}

	public void clearCustom() {
		for (boolean[] row : pixels) {
			java.util.Arrays.fill(row, false);
		}
	}

	public void togglePixel(int x, int y, boolean on) {
		if (x >= 0 && y >= 0 && x < GRID && y < GRID) {
			pixels[y][x] = on;
		}
	}

	public boolean[][] pixels() {
		return pixels;
	}

	public void renderCrosshair(GuiGraphics graphics) {
		int cx = graphics.guiWidth() / 2;
		int cy = graphics.guiHeight() / 2;
		int color = color();
		if (custom) {
			for (int y = 0; y < GRID; y++) {
				for (int x = 0; x < GRID; x++) {
					if (pixels[y][x]) {
						int px = cx - GRID / 2 + x;
						int py = cy - GRID / 2 + y;
						graphics.fill(px, py, px + 1, py + 1, color);
					}
				}
			}
			return;
		}
		int size = 5;
		int gap = 2;
		switch (pattern) {
			case CROSS -> {
				graphics.fill(cx - gap - size, cy, cx - gap, cy + 1, color);
				graphics.fill(cx + gap + 1, cy, cx + gap + size + 1, cy + 1, color);
				graphics.fill(cx, cy - gap - size, cx + 1, cy - gap, color);
				graphics.fill(cx, cy + gap + 1, cx + 1, cy + gap + size + 1, color);
			}
			case DOT -> graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, color);
			case CIRCLE -> {
				int radius = 4;
				for (int dy = -radius; dy <= radius; dy++) {
					int dx = (int) Math.round(Math.sqrt(radius * radius - dy * dy));
					graphics.fill(cx - dx, cy + dy, cx - dx + 1, cy + dy + 1, color);
					graphics.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
				}
			}
			case T -> {
				graphics.fill(cx - 4, cy - 4, cx + 4, cy - 3, color);
				graphics.fill(cx, cy - 3, cx + 1, cy + 5, color);
			}
			case ARROW -> {
				for (int i = 0; i <= 4; i++) {
					graphics.fill(cx - i, cy + i - 2, cx - i + 1, cy + i - 1, color);
					graphics.fill(cx + i, cy + i - 2, cx + i + 1, cy + i - 1, color);
				}
			}
		}
	}

	@Override
	public String option() {
		return custom ? "CUSTOM" : pattern.name();
	}

	@Override
	public void cycleOption() {
		custom = false;
		pattern = pattern.next();
	}

	@Override
	public void writeExtra(JsonObject json) {
		super.writeExtra(json);
		json.addProperty("pattern", pattern.name());
		json.addProperty("custom", custom);
		JsonArray rows = new JsonArray();
		for (boolean[] row : pixels) {
			StringBuilder bits = new StringBuilder(GRID);
			for (boolean cell : row) {
				bits.append(cell ? '1' : '0');
			}
			rows.add(bits.toString());
		}
		json.add("pixels", rows);
	}

	@Override
	public void readExtra(JsonObject json) {
		super.readExtra(json);
		if (json.has("pattern")) {
			pattern = Pattern.parse(json.get("pattern").getAsString());
		}
		if (json.has("custom")) {
			custom = json.get("custom").getAsBoolean();
		}
		if (json.has("pixels")) {
			JsonArray rows = json.getAsJsonArray("pixels");
			for (int y = 0; y < Math.min(GRID, rows.size()); y++) {
				JsonElement row = rows.get(y);
				String bits = row.getAsString();
				for (int x = 0; x < Math.min(GRID, bits.length()); x++) {
					pixels[y][x] = bits.charAt(x) == '1';
				}
			}
		}
	}
}
