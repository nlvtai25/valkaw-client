package com.valkaw.client.module;

import com.google.gson.JsonObject;
import java.awt.Color;
import net.minecraft.ChatFormatting;

/** Per-module HUD colour and prefix. Colours are ARGB; BASIC uses a vanilla colour code. */
public final class TextStyle {
	public enum Mode {
		BASIC, HEX, RAINBOW, GRADIENT, PULSE;

		public Mode next() {
			return values()[(ordinal() + 1) % values().length];
		}

		public static Mode parse(String name) {
			for (Mode mode : values()) {
				if (mode.name().equalsIgnoreCase(name)) {
					return mode;
				}
			}
			return HEX;
		}
	}

	private Mode mode = Mode.HEX;
	private String basic = "&b";
	private int color;
	private int color2 = 0xFFB18CFF;
	private String prefix = "";
	private boolean useGlobal;

	public TextStyle(int color) {
		this.color = color | 0xFF000000;
	}

	public Mode mode() {
		return mode;
	}

	public void setMode(Mode mode) {
		this.mode = mode;
	}

	public String basic() {
		return basic;
	}

	public void setBasic(String basic) {
		String value = basic == null ? "" : basic.trim();
		if (value.length() >= 2 && (value.charAt(0) == '&' || value.charAt(0) == '§')) {
			char code = Character.toLowerCase(value.charAt(1));
			if (ChatFormatting.getByCode(code) != null) {
				this.basic = "&" + code;
				Integer rgb = ChatFormatting.getByCode(code).getColor();
				if (rgb != null) {
					this.color = 0xFF000000 | rgb;
				}
			}
		}
	}

	public int color() {
		return color(System.currentTimeMillis());
	}

	public int color(long millis) {
		return switch (mode) {
			case BASIC -> basicColor();
			case RAINBOW -> rainbow(millis, 0);
			case GRADIENT -> color;
			case PULSE -> pulse(color, millis);
			case HEX -> color;
		};
	}

	public int colorAt(int index, int length, long millis) {
		return switch (mode) {
			case RAINBOW -> rainbow(millis, index);
			case GRADIENT -> blend(color, color2, length <= 1 ? 0.0F : (float) index / (length - 1));
			case PULSE -> pulse(index % 2 == 0 ? color : color2, millis);
			default -> color();
		};
	}

	public int primary() {
		return color;
	}

	public void setPrimary(int color) {
		this.color = color | 0xFF000000;
	}

	public int secondary() {
		return color2;
	}

	public void setSecondary(int color) {
		this.color2 = color | 0xFF000000;
	}

	public String prefix() {
		return prefix;
	}

	public void setPrefix(String prefix) {
		String value = prefix == null ? "" : prefix.replace('\n', ' ').trim();
		this.prefix = value.length() > 24 ? value.substring(0, 24) : value;
	}

	public boolean useGlobal() {
		return useGlobal;
	}

	public void setUseGlobal(boolean useGlobal) {
		this.useGlobal = useGlobal;
	}

	public static String hex(int argb) {
		return String.format("#%06X", argb & 0xFFFFFF);
	}

	public static int parseColor(String value, int fallback) {
		if (value == null) {
			return fallback;
		}
		String text = value.trim();
		if (text.startsWith("#")) {
			text = text.substring(1);
		}
		try {
			if (text.length() == 6) {
				return 0xFF000000 | Integer.parseInt(text, 16);
			}
			if (text.length() == 8) {
				return (int) Long.parseLong(text, 16);
			}
		} catch (NumberFormatException ignored) {
			// Invalid input: keep the previous colour.
		}
		return fallback;
	}

	private int basicColor() {
		char code = basic.length() > 1 ? Character.toLowerCase(basic.charAt(1)) : 'f';
		ChatFormatting formatting = ChatFormatting.getByCode(code);
		Integer rgb = formatting == null ? null : formatting.getColor();
		return rgb == null ? color : 0xFF000000 | rgb;
	}

	private static int rainbow(long millis, int offset) {
		float hue = ((millis % 6000L) / 6000.0F + offset * 0.045F) % 1.0F;
		return 0xFF000000 | Color.HSBtoRGB(hue, 0.65F, 1.0F);
	}

	private static int pulse(int argb, long millis) {
		float wave = (float) ((Math.sin(millis / 420.0) + 1.0) * 0.5);
		float scale = 0.62F + wave * 0.38F;
		int r = (int) (((argb >> 16) & 0xFF) * scale);
		int g = (int) (((argb >> 8) & 0xFF) * scale);
		int b = (int) ((argb & 0xFF) * scale);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	private static int blend(int from, int to, float t) {
		int a = (int) (((from >>> 24) & 0xFF) * (1.0F - t) + ((to >>> 24) & 0xFF) * t);
		int r = (int) (((from >> 16) & 0xFF) * (1.0F - t) + ((to >> 16) & 0xFF) * t);
		int g = (int) (((from >> 8) & 0xFF) * (1.0F - t) + ((to >> 8) & 0xFF) * t);
		int b = (int) ((from & 0xFF) * (1.0F - t) + (to & 0xFF) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	public void write(JsonObject json) {
		json.addProperty("mode", mode.name());
		json.addProperty("basic", basic);
		json.addProperty("color", color);
		json.addProperty("color2", color2);
		json.addProperty("prefix", prefix);
		json.addProperty("useGlobal", useGlobal);
	}

	public void read(JsonObject json) {
		if (json.has("mode")) {
			mode = Mode.parse(json.get("mode").getAsString());
		}
		if (json.has("basic")) {
			setBasic(json.get("basic").getAsString());
		}
		if (json.has("color")) {
			color = json.get("color").getAsInt() | 0xFF000000;
		}
		if (json.has("color2")) {
			color2 = json.get("color2").getAsInt() | 0xFF000000;
		}
		if (json.has("prefix")) {
			setPrefix(json.get("prefix").getAsString());
		}
		if (json.has("useGlobal")) {
			useGlobal = json.get("useGlobal").getAsBoolean();
		}
	}
}
