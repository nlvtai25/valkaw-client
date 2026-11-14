package com.valkaw.client.hud;

import com.valkaw.client.module.Module;
import com.valkaw.client.module.ModuleManager;
import com.valkaw.client.module.TextStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Shared HUD drawing primitives (sci-fi chips, no per-frame component allocation). */
public final class Hud {
	public static final int CYAN = 0xFF9EC7F0;
	public static final int TEXT = 0xFFD7E3FF;
	public static final int DIM = 0xFF7183A8;
	public static final int BG = 0x66101828;

	private static TextStyle style = new TextStyle(CYAN);
	private static TextStyle effective = style;
	private static Font cachedFont;
	private static final java.util.HashMap<String, Integer> WIDTHS = new java.util.HashMap<>();
	private static final java.util.HashMap<String, int[]> CUMW = new java.util.HashMap<>();
	private static final String[] ASCII = new String[128];
	static {
		for (int i = 0; i < 128; i++) {
			ASCII[i] = String.valueOf((char) i);
		}
	}

	private Hud() {
	}

	/** Selects the active module. Called once before each module render, never per chip. */
	public static void use(Module module) {
		style = module.style();
		effective = style.useGlobal() ? ModuleManager.get().globalStyle() : style;
	}

	public static TextStyle style() {
		return style;
	}

	public static int color() {
		return effective.color();
	}

	/** Module prefix in "[NAME]" form; a custom prefix keeps its own brackets. */
	public static String label(String fallback) {
		String text = style.prefix().isBlank() ? fallback : style.prefix();
		return text.startsWith("[") ? text : "[" + text + "]";
	}

	/**
	 * Left edge a chip plate actually draws at: the plate starts flush at the
	 * module anchor (clamped to the screen edge), while module positions
	 * (and the editor) stay untouched.
	 */
	public static final int BLEED = 0;

	public static int plateX(int x) {
		return Math.max(0, x - BLEED);
	}

	/** Draws a "LABEL value" chip and returns its width. */
	public static int chip(GuiGraphicsExtractor graphics, Font font, int x, int y, String label, String value) {
		return chipRaw(graphics, font, x, y, label(label), value);
	}

	/** Draws a chip whose label is not replaced by the custom prefix. */
	public static int chipRaw(GuiGraphicsExtractor graphics, Font font, int x, int y, String label, String value) {
		int labelWidth = width(font, label);
		int width = 2 + labelWidth + 3 + width(font, value);
		int bx = plateX(x);
		graphics.fill(bx, y, bx + width, y + 11, BG);
		text(graphics, font, label, bx + 1, y + 2);
		text(graphics, font, value, bx + 1 + labelWidth + 3, y + 2);
		return width;
	}

	/** Draws a module prefix chip and returns its width. */
	public static int prefix(GuiGraphicsExtractor graphics, Font font, int x, int y, String label) {
		String text = label(label);
		int width = 2 + width(font, text);
		int bx = plateX(x);
		graphics.fill(bx, y, bx + width, y + 11, BG);
		text(graphics, font, text, bx + 1, y + 2);
		return width;
	}

	/** Draws a key cap. */
	public static void key(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, String label, boolean down) {
		graphics.fill(x, y, x + w, y + 11, down ? 0xD0122A3A : BG);
		graphics.outline(x, y, w, 11, down ? 0xFF9EC7F0 : 0x559EC7F0);
		int color = down ? 0xFFEAF6FF : color();
		graphics.text(font, label, x + (w - font.width(label)) / 2, y + 2, color, false);
	}

	/** Raw value chip with an explicit colour, used by status icons. */
	public static int value(GuiGraphicsExtractor graphics, Font font, int x, int y, String value, int color) {
		int width = 2 + width(font, value);
		int bx = plateX(x);
		graphics.fill(bx, y, bx + width, y + 11, BG);
		graphics.text(font, value, bx + 1, y + 2, color, false);
		return width;
	}

	public static int width(Font font, String text) {
		if (text == null || text.isEmpty()) {
			return 0;
		}
		if (font != cachedFont) {
			WIDTHS.clear();
			CUMW.clear();
			cachedFont = font;
		}
		// Transient numeric strings (FPS, CPS, ping, coords) change every frame
		// and only pollute the cache, so measure them directly instead.
		if (isNumeric(text)) {
			return font.width(text);
		}
		Integer cached = WIDTHS.get(text);
		if (cached != null) {
			return cached;
		}
		// Measure the styled component so bold &-codes widen the chip like they
		// do when drawn, instead of under-measuring the plain text.
		int value = hasCodes(text) ? font.width(component(text)) : font.width(text);
		if (WIDTHS.size() > 256) {
			WIDTHS.clear();
		}
		WIDTHS.put(text, value);
		return value;
	}

	/** Draws using the active style, including &-codes in a custom prefix. */
	public static void text(GuiGraphicsExtractor graphics, Font font, String text, int x, int y) {
		if (text == null || text.isEmpty()) {
			return;
		}
		if (hasCodes(text)) {
			graphics.text(font, component(text), x, y, effective.color(), false);
			return;
		}
		// shortcut: per-char draw allocates a String and re-measures the glyph; only animated styles pay it.
		if (effective.mode() == TextStyle.Mode.RAINBOW || effective.mode() == TextStyle.Mode.GRADIENT
				|| effective.mode() == TextStyle.Mode.PULSE) {
			int[] cw = cumWidths(font, text);
			long now = System.currentTimeMillis();
			for (int i = 0; i < text.length(); i++) {
				char ch = text.charAt(i);
				String s = ch < 128 ? ASCII[ch] : text.substring(i, i + 1);
				int color = effective.colorAt(i, text.length(), now);
				graphics.text(font, s, x + cw[i], y, color, false);
			}
			return;
		}
		graphics.text(font, text, x, y, effective.color(), false);
	}

	/** Cached per-char cumulative X offsets so animated text never re-measures glyphs. */
	private static int[] cumWidths(Font font, String text) {
		if (font != cachedFont) {
			WIDTHS.clear();
			CUMW.clear();
			cachedFont = font;
		}
		int[] cw = CUMW.get(text);
		if (cw != null) {
			return cw;
		}
		cw = new int[text.length() + 1];
		for (int i = 0; i < text.length(); i++) {
			cw[i + 1] = cw[i] + font.width(text.substring(i, i + 1));
		}
		if (CUMW.size() > 256) {
			CUMW.clear();
		}
		CUMW.put(text, cw);
		return cw;
	}

	private static boolean isNumeric(String text) {
		for (int i = 0; i < text.length(); i++) {
			if (!Character.isDigit(text.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	private static boolean hasCodes(String text) {
		return text.indexOf('&') >= 0 || text.indexOf('§') >= 0;
	}

	private static Component component(String text) {
		MutableComponent root = Component.empty();
		StringBuilder part = new StringBuilder();
		ChatFormatting current = null;
		boolean any = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if ((c == '&' || c == '§') && i + 1 < text.length()) {
				if (part.length() > 0) {
					root.append(styled(part.toString(), current));
					part.setLength(0);
					any = true;
				}
				current = ChatFormatting.getByCode(Character.toLowerCase(text.charAt(++i)));
				continue;
			}
			part.append(c);
		}
		if (part.length() > 0) {
			root.append(styled(part.toString(), current));
			any = true;
		}
		if (!any) {
			return Component.literal(text);
		}
		return root;
	}

	private static MutableComponent styled(String text, ChatFormatting formatting) {
		MutableComponent component = Component.literal(text);
		if (formatting != null) {
			component.withStyle(formatting);
		}
		return component;
	}
}
