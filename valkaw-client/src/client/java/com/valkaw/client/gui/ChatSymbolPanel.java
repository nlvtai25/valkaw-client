package com.valkaw.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

/** Right-edge chat tab that inserts Unicode symbols and {@code &}-codes at the cursor. */
public final class ChatSymbolPanel {
	/** Symbol blocks, in display order. Generated so a whole block costs one line. */
	private static final int[][] BLOCKS = {
			{0x2190, 0x21FF}, {0x27F0, 0x27FF}, {0x2900, 0x297F}, {0x2B00, 0x2BFF},
			{0x25A0, 0x25FF}, {0x2500, 0x257F}, {0x2580, 0x259F}, {0x2460, 0x24FF},
			{0x2600, 0x26FF}, {0x2700, 0x27BF}, {0x2200, 0x22FF}, {0x2300, 0x23FF},
			{0x2100, 0x214F}, {0x2150, 0x218F}, {0x20A0, 0x20BF}, {0x0370, 0x03FF},
			{0x2000, 0x206F},
	};
	private static final String[] SYMBOLS = buildSymbols();
	private static final String[] COLOR_CODES = {
			"&0", "&1", "&2", "&3", "&4", "&5", "&6", "&7",
			"&8", "&9", "&a", "&b", "&c", "&d", "&e", "&f",
	};
	private static final int[] COLOR_RGB = {
			0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
			0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
	};
	private static final ChatFormatting[] FORMATS = {
			ChatFormatting.OBFUSCATED, ChatFormatting.BOLD, ChatFormatting.STRIKETHROUGH,
			ChatFormatting.UNDERLINE, ChatFormatting.ITALIC, ChatFormatting.RESET,
	};
	private static final int TAB_W = 44;
	private static final int TAB_H = 14;
	private static final int CELL = 16;
	private static final int COLS = 10;
	private static final int ROWS = 10;
	private static final int HEADER = 20;
	private static final int PAD = 5;
	private static final int PANEL_W = COLS * CELL + PAD * 2 + 4;
	private static final int PANEL_H = HEADER + ROWS * CELL + PAD * 2;
	private static final int COLOR_ROWS = (COLOR_CODES.length + COLS - 1) / COLS;
	private static final int SURFACE = 0xF80A111C;

	private static boolean open;
	private static boolean colorTab;
	private static int scrollRow;

	private ChatSymbolPanel() {
	}

	private static String[] buildSymbols() {
		StringBuilder out = new StringBuilder();
		for (int[] block : BLOCKS) {
			for (int cp = block[0]; cp <= block[1]; cp++) {
				int type = Character.getType(cp);
				// skip unassigned + invisible marks so the grid has no blank cells
				if (type == Character.UNASSIGNED || type == Character.CONTROL || type == Character.FORMAT
						|| type == Character.SURROGATE || type == Character.PRIVATE_USE) {
					continue;
				}
				out.appendCodePoint(cp);
			}
		}
		return out.toString().codePoints().mapToObj(cp -> new String(Character.toChars(cp)))
				.toArray(String[]::new);
	}

	public static void render(ChatScreen screen, GuiGraphics graphics, int mouseX, int mouseY) {
		Font font = Minecraft.getInstance().font;
		int tx = tabX(screen.width);
		int ty = tabY(screen.height);
		boolean tabHot = inside(mouseX, mouseY, tx, ty, TAB_W, TAB_H);
		Ui.round(graphics, tx, ty, TAB_W, TAB_H, Ui.R_PILL, open || tabHot ? 0xFF16243C : 0xEE0B1220);
		graphics.renderOutline(tx, ty, TAB_W, TAB_H, open || tabHot ? Ui.alpha(Ui.ACCENT, 0.7F) : Ui.LINE_SOFT);
		graphics.drawString(font, "\u2756", tx + (TAB_W - font.width("\u2756")) / 2, ty + 3,
				open || tabHot ? Ui.ACCENT : Ui.TEXT_2, false);
		if (tabHot) {
			drawTooltip(graphics, "Custom symbols", mouseX, mouseY);
		}
		if (!open) {
			return;
		}

		int px = panelX(screen.width);
		int py = panelY(screen.height);
		Ui.panel(graphics, px, py, PANEL_W, PANEL_H, Ui.R_PANEL, SURFACE);
		drawHeader(graphics, px, py, mouseX, mouseY);

		int gx = px + PAD + 2;
		int gy = py + HEADER + PAD;
		graphics.enableScissor(gx, gy, gx + COLS * CELL, gy + ROWS * CELL);
		if (colorTab) {
			drawColors(graphics, gx, gy, mouseX, mouseY);
		} else {
			drawSymbols(graphics, gx, gy, mouseX, mouseY);
		}
		graphics.disableScissor();

		if (!colorTab && maxScroll() > 0) {
			int rows = (SYMBOLS.length + COLS - 1) / COLS;
			float visible = (float) ROWS / rows;
			float offset = (float) scrollRow / maxScroll();
			Ui.scrollbar(graphics, px + PANEL_W - 5, gy, ROWS * CELL, offset, visible, Ui.ACCENT);
		}
	}

	private static void drawHeader(GuiGraphics graphics, int px, int py, int mouseX, int mouseY) {
		Font font = Minecraft.getInstance().font;
		int x = px + PAD + 2;
		int w = (PANEL_W - (PAD + 2) * 2 - 4) / 2;
		for (int i = 0; i < 2; i++) {
			int tabX = x + i * (w + 4);
			boolean active = (i == 1) == colorTab;
			boolean hot = inside(mouseX, mouseY, tabX, py + 5, w, HEADER - 7);
			Ui.round(graphics, tabX, py + 5, w, HEADER - 7, 5,
					active ? Ui.alpha(Ui.ACCENT, 0.16F) : hot ? 0x14FFFFFF : 0x00000000);
			String label = i == 0 ? "SYMBOLS" : "COLOUR";
			graphics.drawString(font, label, tabX + (w - font.width(label)) / 2, py + 9,
					active ? Ui.ACCENT : Ui.MUTED, false);
		}
	}

	private static void drawTooltip(GuiGraphics graphics, String text, int mouseX, int mouseY) {
		Font font = Minecraft.getInstance().font;
		int w = font.width(text);
		int x = mouseX - w - 10;
		int y = mouseY - 12;
		Ui.round(graphics, x - 4, y - 4, w + 8, 15, 4, 0xF20A111C);
		graphics.renderOutline(x - 4, y - 4, w + 8, 15, Ui.LINE_SOFT);
		graphics.drawString(font, text, x, y, Ui.TEXT, false);
	}

	private static void drawSymbols(GuiGraphics graphics, int gx, int gy, int mouseX, int mouseY) {
		Font font = Minecraft.getInstance().font;
		for (int i = 0; i < SYMBOLS.length; i++) {
			int col = i % COLS;
			int row = i / COLS - scrollRow;
			if (row < 0 || row >= ROWS) {
				continue;
			}
			int x = gx + col * CELL;
			int y = gy + row * CELL;
			boolean hot = inside(mouseX, mouseY, x, y, CELL - 2, CELL - 2);
			Ui.round(graphics, x, y, CELL - 2, CELL - 2, 3, hot ? 0xFF1B2C46 : 0xFF121B2C);
			if (hot) {
				graphics.renderOutline(x, y, CELL - 2, CELL - 2, Ui.alpha(Ui.ACCENT, 0.6F));
			}
			graphics.drawString(font, SYMBOLS[i], x + (CELL - 2 - font.width(SYMBOLS[i])) / 2, y + 4,
					hot ? 0xFFFFFFFF : Ui.TEXT_2, false);
		}
	}

	private static void drawColors(GuiGraphics graphics, int gx, int gy, int mouseX, int mouseY) {
		Font font = Minecraft.getInstance().font;
		for (int i = 0; i < COLOR_CODES.length; i++) {
			int x = gx + (i % COLS) * CELL;
			int y = gy + (i / COLS) * CELL;
			int rgb = COLOR_RGB[i];
			boolean hot = inside(mouseX, mouseY, x, y, CELL - 2, CELL - 2);
			Ui.round(graphics, x, y, CELL - 2, CELL - 2, 3, 0xFF000000 | rgb);
			if (hot) {
				graphics.renderOutline(x, y, CELL - 2, CELL - 2, Ui.ACCENT);
			}
			graphics.drawString(font, COLOR_CODES[i], x + (CELL - 2 - font.width(COLOR_CODES[i])) / 2, y + 4,
					luma(rgb) > 0x80 ? 0xFF101010 : 0xFFF2F5FA, false);
		}

		// format rows: code on the left, live sample on the right so the effect is visible
		for (int i = 0; i < FORMATS.length; i++) {
			int y = gy + (COLOR_ROWS + i) * CELL;
			boolean hot = inside(mouseX, mouseY, gx, y, COLS * CELL, CELL - 2);
			Ui.round(graphics, gx, y, COLS * CELL, CELL - 2, 3, hot ? 0xFF1B2C46 : 0xFF121B2C);
			String code = "&" + FORMATS[i].getChar();
			graphics.drawString(font, code, gx + 4, y + 4, Ui.ACCENT, false);
			graphics.drawString(font, Component.literal("Sample").withStyle(FORMATS[i]),
					gx + 4 + font.width(code) + 6, y + 4, Ui.TEXT_2, false);
		}
	}

	public static boolean click(ChatScreen screen, double mouseX, double mouseY, int button) {
		if (button != 0) {
			return false;
		}
		int tx = tabX(screen.width);
		int ty = tabY(screen.height);
		if (inside(mouseX, mouseY, tx, ty, TAB_W, TAB_H)) {
			open = !open;
			return true;
		}
		if (!open) {
			return false;
		}
		int px = panelX(screen.width);
		int py = panelY(screen.height);
		if (!inside(mouseX, mouseY, px, py, PANEL_W, PANEL_H)) {
			return false;
		}

		int x = px + PAD + 2;
		int w = (PANEL_W - (PAD + 2) * 2 - 4) / 2;
		for (int i = 0; i < 2; i++) {
			if (inside(mouseX, mouseY, x + i * (w + 4), py + 5, w, HEADER - 7)) {
				colorTab = i == 1;
				scrollRow = 0;
				return true;
			}
		}

		int gx = px + PAD + 2;
		int gy = py + HEADER + PAD;
		int col = (int) ((mouseX - gx) / CELL);
		int row = (int) ((mouseY - gy) / CELL);
		if (col < 0 || col >= COLS || row < 0 || row >= ROWS) {
			return true;
		}
		if (colorTab) {
			if (row < COLOR_ROWS) {
				screen.insertText(COLOR_CODES[row * COLS + col], false);
			} else if (row - COLOR_ROWS < FORMATS.length) {
				screen.insertText("&" + FORMATS[row - COLOR_ROWS].getChar(), false);
			}
			return true;
		}
		int index = (row + scrollRow) * COLS + col;
		if (index < SYMBOLS.length) {
			screen.insertText(SYMBOLS[index], false);
		}
		return true;
	}

	public static boolean scroll(ChatScreen screen, double mouseX, double mouseY, double delta) {
		if (!open || colorTab) {
			return false;
		}
		int px = panelX(screen.width);
		int py = panelY(screen.height);
		if (!inside(mouseX, mouseY, px, py, PANEL_W, PANEL_H)) {
			return false;
		}
		scrollRow = Math.max(0, Math.min(maxScroll(), scrollRow - (int) Math.signum(delta) * 3));
		return true;
	}

	private static int maxScroll() {
		return Math.max(0, (SYMBOLS.length + COLS - 1) / COLS - ROWS);
	}

	private static int tabX(int width) {
		return width - TAB_W - 2;
	}

	/** Chat input box sits at y = height - 12 with height 12; tab rides just above it. */
	private static int tabY(int height) {
		return height - 12 - TAB_H - 2;
	}

	private static int panelX(int width) {
		// right edge lines up with the chat bar's right edge, box sits directly above the bar
		return width - PANEL_W - 4;
	}

	private static int panelY(int height) {
		return tabY(height) - PANEL_H - 2;
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	private static int luma(int rgb) {
		return ((rgb >> 16 & 0xFF) * 299 + (rgb >> 8 & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
	}
}
