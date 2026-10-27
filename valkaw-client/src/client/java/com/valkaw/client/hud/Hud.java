package com.valkaw.client.hud;

import com.valkaw.client.module.Module;
import com.valkaw.client.module.ModuleManager;
import com.valkaw.client.module.TextStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Shared HUD drawing primitives (sci-fi chips, no per-frame component allocation). */
public final class Hud {
	public static final int CYAN = 0xFF7DF9FF;
	public static final int TEXT = 0xFFD7E3FF;
	public static final int DIM = 0xFF7183A8;
	public static final int BG = 0x66101828;

	private static TextStyle style = new TextStyle(CYAN);
	private static TextStyle effective = style;
	private static Font cachedFont;
	private static final java.util.HashMap<String, Integer> WIDTHS = new java.util.HashMap<>();

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

	public static TextStyle effective() {
		return effective;
	}

	public static int color() {
		return effective.color();
	}

	public static String label(String fallback) {
		return style.prefix().isBlank() ? fallback : style.prefix();
	}

	/** Draws a "LABEL value" chip and returns its width. */
	public static int chip(GuiGraphics graphics, Font font, int x, int y, String label, String value) {
		return chipRaw(graphics, font, x, y, label(label), value);
	}

	/** Draws a chip whose label is not replaced by the custom prefix. */
	public static int chipRaw(GuiGraphics graphics, Font font, int x, int y, String label, String value) {
		int labelWidth = width(font, label);
		int width = 9 + labelWidth + 3 + width(font, value);
		graphics.fill(x, y, x + width, y + 11, BG);
		graphics.fill(x, y, x + 1, y + 11, color());
		text(graphics, font, label, x + 4, y + 2);
		text(graphics, font, value, x + 4 + labelWidth + 3, y + 2);
		return width;
	}

	/** Draws a module prefix chip and returns its width. */
	public static int prefix(GuiGraphics graphics, Font font, int x, int y, String label) {
		String text = label(label);
		int width = 8 + width(font, text);
		graphics.fill(x, y, x + width, y + 11, BG);
		graphics.fill(x, y, x + 1, y + 11, color());
		text(graphics, font, text, x + 4, y + 2);
		return width;
	}

	/** Draws a key cap. */
	public static void key(GuiGraphics graphics, Font font, int x, int y, int w, String label, boolean down) {
		graphics.fill(x, y, x + w, y + 11, down ? 0xD0122A3A : BG);
		graphics.renderOutline(x, y, w, 11, down ? 0xFF7DF9FF : 0x557DF9FF);
		int color = down ? 0xFFEAF6FF : color();
		graphics.drawString(font, label, x + (w - font.width(label)) / 2, y + 2, color, false);
	}

	/** Raw value chip with an explicit colour, used by status icons. */
	public static int value(GuiGraphics graphics, Font font, int x, int y, String value, int color) {
		int width = 9 + width(font, value);
		graphics.fill(x, y, x + width, y + 11, BG);
		graphics.fill(x, y, x + 1, y + 11, color);
		graphics.drawString(font, value, x + 4, y + 2, color, false);
		return width;
	}

	public static int width(Font font, String text) {
		String plain = strip(text);
		if (plain.isEmpty()) {
			return 0;
		}
		if (font != cachedFont) {
			WIDTHS.clear();
			cachedFont = font;
		}
		Integer cached = WIDTHS.get(plain);
		if (cached != null) {
			return cached;
		}
		int value = font.width(plain);
		if (WIDTHS.size() > 256) {
			WIDTHS.clear();
		}
		WIDTHS.put(plain, value);
		return value;
	}

	/** Draws using the active style, including &-codes in a custom prefix. */
	public static void text(GuiGraphics graphics, Font font, String text, int x, int y) {
		if (text == null || text.isEmpty()) {
			return;
		}
		if (hasCodes(text)) {
			graphics.drawString(font, component(text, effective), x, y, effective.color(), false);
			return;
		}
		// shortcut: per-char draw allocates a String and re-measures the glyph; only animated styles pay it.
		if (effective.mode() == TextStyle.Mode.RAINBOW || effective.mode() == TextStyle.Mode.GRADIENT
				|| effective.mode() == TextStyle.Mode.PULSE) {
			long now = System.currentTimeMillis();
			int cx = x;
			for (int i = 0; i < text.length(); i++) {
				String ch = text.substring(i, i + 1);
				int color = effective.colorAt(i, text.length(), now);
				graphics.drawString(font, ch, cx, y, color, false);
				cx += font.width(ch);
			}
			return;
		}
		graphics.drawString(font, text, x, y, effective.color(), false);
	}

	public static String strip(String text) {
		if (text == null || text.indexOf('&') < 0 && text.indexOf('§') < 0) {
			return text == null ? "" : text;
		}
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if ((c == '&' || c == '§') && i + 1 < text.length()) {
				i++;
				continue;
			}
			out.append(c);
		}
		return out.toString();
	}

	private static boolean hasCodes(String text) {
		return text.indexOf('&') >= 0 || text.indexOf('§') >= 0;
	}

	private static Component component(String text, TextStyle style) {
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
