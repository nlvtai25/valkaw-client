package com.valkaw.client.gui;

import com.valkaw.client.module.ModuleManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

/** The per-line Copy buttons drawn over the chat, both focused and on the HUD. */
public final class ChatCopyButtons {
	private static final String LABEL = "Copy";
	private static final int HEIGHT = 9;
	private static final List<Entry> ENTRIES = new ArrayList<>();
	/** Prefix added by the Chat Timestamps module, dropped from the copied text. */
	private static final Pattern STAMP = Pattern.compile("^(?:[&§].)*\\[\\d{1,2}:\\d{2}(:\\d{2})?(\\s?[AP]M)?\\]\\s?");
	/** Color value to legacy code, so a copied line keeps the colors the server sent. */
	private static final Map<Integer, ChatFormatting> BY_COLOR = new HashMap<>();
	private static final String HEX = "0123456789ABCDEF";
	/** codes() result per style; chat lines reuse the same handful of styles every frame. */
	private static final Map<Style, String> CODE_CACHE = new HashMap<>();

	static {
		for (ChatFormatting formatting : ChatFormatting.values()) {
			Integer rgb = formatting.getColor();
			if (rgb != null) {
				BY_COLOR.put(rgb, formatting);
			}
		}
	}

	private ChatCopyButtons() {
	}

	public static int labelWidth(Font font) {
		return Ui.width(font, LABEL) + 6;
	}

	public static void clear() {
		ENTRIES.clear();
	}

	/** Draws the chip for one chat line; the pose stack is already in chat space. */
	public static void drawLine(GuiGraphics graphics, int y, float opacity, FormattedCharSequence content) {
		// Vanilla only fades a line out, it keeps calling this, so stop with the text.
		if (!ModuleManager.isOn("copy-chat") || opacity < 0.05F) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		String text = plain(content);
		float scale = minecraft.options.chatScale().get().floatValue();
		draw(graphics, minecraft.font, minecraft.font.width(content) + 6, y, scale, text, opacity);
	}

	/**
	 * Draws the chip in chat space (the pose stack is already scaled by the chat scale) and
	 * remembers its screen-space rectangle for hit testing.
	 */
	public static void draw(GuiGraphics graphics, Font font, int x, int y, float scale, String text, float opacity) {
		if (ENTRIES.size() >= 128 || text.isBlank()) {
			return;
		}
		int w = labelWidth(font);
		Ui.round(graphics, x, y, w, HEIGHT, 3, Ui.alpha(Ui.SURFACE_3, opacity));
		graphics.renderOutline(x, y, w, HEIGHT, Ui.alpha(Ui.LINE, opacity));
		Ui.text(graphics, font, LABEL, x + 3, y + 1, Ui.alpha(Ui.ACCENT, opacity));
		ENTRIES.add(new Entry(
				(int) ((x + 4) * scale),
				(int) (y * scale),
				(int) Math.ceil(w * scale),
				(int) Math.ceil(HEIGHT * scale),
				text));
	}

	/** Copies the line under the cursor; false when no chip was hit. */
	public static boolean click(double mouseX, double mouseY) {
		for (Entry entry : ENTRIES) {
			if (mouseX >= entry.x() && mouseX < entry.x() + entry.w()
					&& mouseY >= entry.y() && mouseY < entry.y() + entry.h()) {
				Minecraft minecraft = Minecraft.getInstance();
				minecraft.keyboardHandler.setClipboard(STAMP.matcher(entry.text()).replaceFirst(""));
				minecraft.gui.setOverlayMessage(Component.literal("Chat Copied"), false);
				return true;
			}
		}
		return false;
	}

	/** Rebuilds the text with the & codes the line was rendered with. */
	private static String plain(FormattedCharSequence text) {
		StringBuilder out = new StringBuilder();
		String[] last = { "" };
		boolean[] started = { false };
		text.accept((index, style, codePoint) -> {
			String codes = codes(style);
			if (!started[0]) {
				out.append(codes);
				started[0] = true;
			} else if (!codes.equals(last[0])) {
				// Legacy codes are additive, so reset before switching style.
				out.append("&r").append(codes);
			}
			last[0] = codes;
			out.appendCodePoint(codePoint);
			return true;
		});
		return out.toString();
	}

	private static String codes(Style style) {
		String cached = CODE_CACHE.get(style);
		if (cached != null) {
			return cached;
		}
		StringBuilder out = new StringBuilder();
		TextColor color = style.getColor();
		if (color != null) {
			ChatFormatting formatting = BY_COLOR.get(color.getValue());
			if (formatting != null) {
				out.append('&').append(formatting.getChar());
			} else {
				// One "&#RRGGBB" token instead of the six-codepoint vanilla form.
				out.append("&#");
				appendHex(out, color.getValue() & 0xFFFFFF);
			}
		}
		if (style.isBold()) {
			out.append("&l");
		}
		if (style.isItalic()) {
			out.append("&o");
		}
		if (style.isUnderlined()) {
			out.append("&n");
		}
		if (style.isStrikethrough()) {
			out.append("&m");
		}
		if (style.isObfuscated()) {
			out.append("&k");
		}
		String result = out.toString();
		if (CODE_CACHE.size() > 256) {
			CODE_CACHE.clear();
		}
		CODE_CACHE.put(style, result);
		return result;
	}

	private static void appendHex(StringBuilder out, int rgb) {
		for (int shift = 20; shift >= 0; shift -= 4) {
			out.append(HEX.charAt((rgb >> shift) & 0xF));
		}
	}

	private record Entry(int x, int y, int w, int h, String text) {
	}
}
