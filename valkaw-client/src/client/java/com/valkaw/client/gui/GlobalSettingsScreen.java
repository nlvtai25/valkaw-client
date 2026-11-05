package com.valkaw.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.valkaw.client.module.ModuleManager;
import com.valkaw.client.module.TextStyle;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class GlobalSettingsScreen extends Screen {
	private static final int ROW_H = 32;
	private static final int ROW_GAP = 3;
	private static final char[] FORMAT_CHARS = { 'l', 'n', 'o', 'm', 'r' };
	private static final String[] COLOR_CODES = {
			"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f" };
	private Screen parent;
	private int winX;
	private int winY;
	private int winW;
	private int winH;
	private int listX;
	private int listW;
	private int bodyY;
	private int bodyH;
	private int footerY;
	private boolean color2;

	public GlobalSettingsScreen(Screen parent) {
		super(Component.literal("Global Settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int pad = Math.min(28, width / 10);
		winW = Math.min(560, width - pad * 2);
		winH = Math.min(320, height - pad * 2);
		winX = (width - winW) / 2;
		winY = (height - winH) / 2;
		listX = winX + 16;
		listW = winW - 32;
		bodyY = winY + 72;
		bodyH = winH - 130;
		footerY = winY + winH - 28;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fillGradient(0, 0, width, height, Ui.BG_TOP, Ui.BG_BOTTOM);
		Ui.planet(graphics, Math.round(width * 0.74F), Math.round(height * 0.60F),
				Math.max(40, Math.min(Math.round(width * 0.26F), Math.round(height * 0.22F))), (float)(System.currentTimeMillis() * 0.001));
		graphics.fillGradient(0, 0, width, 96, 0x99000000, 0x00000000);
		graphics.fillGradient(0, height - 110, width, height, 0x00000000, 0x88000000);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		Ui.panel(graphics, winX, winY, winW, winH, Ui.R_PANEL, 0xFF0B1220);
		Ui.round(graphics, winX + 1, winY + 1, winW - 2, 56, 14, 0x141A2438);
		Ui.logo(graphics, winX + 24, winY + 28, 26);
		graphics.drawString(font, "Global Settings", winX + 48, winY + 20, Ui.TEXT, false);
		graphics.drawString(font, "Shared style applied when 'Use global' is enabled", winX + 48, winY + 32, Ui.MUTED, false);
		Ui.closeIcon(graphics, winX + winW - 16, winY + 18, over(mouseX, mouseY, winX + winW - 34, winY + 14, 24, 24) ? Ui.TEXT : Ui.MUTED);

		drawStyle(graphics, mouseX, mouseY);

		Ui.divider(graphics, winX + 14, footerY - 12, winW - 28);
		graphics.drawString(font, "Changes save automatically", winX + 16, footerY, Ui.MUTED);
		int w = Ui.width(font, "Done") + 18;
		Ui.button(graphics, font, winX + winW - 16 - w, footerY - 5, "Done", Ui.ACCENT,
				over(mouseX, mouseY, winX + winW - 16 - w, footerY - 5, w, 18), true);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void drawStyle(GuiGraphics graphics, int mouseX, int mouseY) {
		TextStyle style = ModuleManager.get().globalStyle();
		graphics.drawString(font, "TEXT STYLE", listX, bodyY - 4, Ui.MUTED);

		int y = bodyY + 12;
		int styleW = listW;
		boolean hotMode = over(mouseX, mouseY, listX, y, styleW, 18);
		Ui.round(graphics, listX, y, styleW, 18, 5, hotMode ? 0xFF16203A : 0xF00F1727);
		graphics.drawString(font, "Mode", listX + 8, y + 5, Ui.TEXT_2, false);
		String mode = style.mode().name();
		Ui.textRight(graphics, font, mode, listX + styleW - 8, y + 5, Ui.ACCENT);

		y += 22;
		boolean hotUse = over(mouseX, mouseY, listX, y, styleW, 18);
		Ui.round(graphics, listX, y, styleW, 18, 5, hotUse ? 0xFF16203A : 0xF00F1727);
		graphics.drawString(font, "Use global (default)", listX + 8, y + 5, Ui.TEXT_2, false);
		String on = style.useGlobal() ? "ON" : "OFF";
		Ui.textRight(graphics, font, on, listX + styleW - 8, y + 5, style.useGlobal() ? Ui.GREEN : Ui.MUTED);

		if (style.mode() == TextStyle.Mode.BASIC) {
			y += 28;
			graphics.drawString(font, "FORMAT", listX, y, Ui.MUTED);
			int cell = Math.max(14, (styleW - 7 * 3) / 8);
			int px = listX;
			int py = y + 6;
			for (char code : FORMAT_CHARS) {
				int cx = px;
				int cy = py;
				String s = "&" + code;
				net.minecraft.ChatFormatting formatting = net.minecraft.ChatFormatting.getByCode(code);
				int color = formatting == null || formatting.getColor() == null ? 0xFFFFFFFF : 0xFF000000 | formatting.getColor();
				Ui.round(graphics, cx, cy, cell, cell - 2, 3, color);
				if (style.basic().indexOf(code) >= 0) {
					graphics.renderOutline(cx - 1, cy - 1, cell + 2, cell, 0xFFFFFFFF);
				}
				px += cell + 3;
				if ((px - listX) > styleW / 2) {
					px = listX;
					py += cell + 2;
				}
			}
			py += cell + 10;
			graphics.drawString(font, "Color", listX, py, Ui.TEXT_2, false);
			py += 14;
			int ccell = Math.max(14, (styleW - 9 * 3) / 10);
			String[] codes = COLOR_CODES;
			for (int i = 0; i < codes.length; i++) {
				int cx = listX + (i % 10) * (ccell + 3);
				int cy = py + (i / 10) * (ccell + 3);
				net.minecraft.ChatFormatting formatting = net.minecraft.ChatFormatting.getByCode(codes[i].charAt(0));
				int color = formatting == null || formatting.getColor() == null ? 0xFFFFFFFF : 0xFF000000 | formatting.getColor();
				Ui.round(graphics, cx, cy, ccell, ccell - 2, 3, color);
				int selected = style.color();
				if ((selected & 0xFFFFFF) == (color & 0xFFFFFF)) {
					graphics.renderOutline(cx - 1, cy - 1, ccell + 2, ccell, 0xFFFFFFFF);
				}
			}
			return;
		}

		y += 28;
		int tabY = y;
		int tabW = (styleW - 6) / 2;
		Ui.round(graphics, listX, tabY, tabW, 16, 5, !color2 ? Ui.alpha(Ui.ACCENT, 0.18F) : 0x0AFFFFFF);
		graphics.drawString(font, "Colour 1", listX + 8, tabY + 4, !color2 ? Ui.ACCENT : Ui.MUTED, false);
		Ui.round(graphics, listX + tabW + 6, tabY, tabW, 16, 5, color2 ? Ui.alpha(Ui.ACCENT, 0.18F) : 0x0AFFFFFF);
		graphics.drawString(font, "Colour 2", listX + tabW + 6 + 8, tabY + 4, color2 ? Ui.ACCENT : Ui.MUTED, false);

		int paletteY = tabY + 22;
		int cell = Math.max(14, (styleW - 9 * 3) / 10);
		String[] codes = COLOR_CODES;
		for (int i = 0; i < codes.length; i++) {
			int cx = listX + (i % 10) * (cell + 3);
			int cy = paletteY + (i / 10) * (cell + 3);
			net.minecraft.ChatFormatting formatting = net.minecraft.ChatFormatting.getByCode(codes[i].charAt(0));
			int color = formatting == null || formatting.getColor() == null ? 0xFFFFFFFF : 0xFF000000 | formatting.getColor();
			Ui.round(graphics, cx, cy, cell, cell - 2, 3, color);
			int selected = color2 ? style.secondary() : style.primary();
			if ((selected & 0xFFFFFF) == (color & 0xFFFFFF)) {
				graphics.renderOutline(cx - 1, cy - 1, cell + 2, cell, 0xFFFFFFFF);
			}
		}
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();

		if (over(mx, my, winX + winW - 34, winY + 14, 24, 24)) {
			click();
			onClose();
			return true;
		}

		TextStyle style = ModuleManager.get().globalStyle();
		int y = bodyY + 12;
		int styleW = listW;
		if (over(mx, my, listX, y, styleW, 18)) {
			style.setMode(style.mode().next());
			save();
			return true;
		}
		y += 22;
		if (over(mx, my, listX, y, styleW, 18)) {
			style.setUseGlobal(!style.useGlobal());
			save();
			return true;
		}

		if (style.mode() == TextStyle.Mode.BASIC) {
			y += 28;
			int cell = Math.max(14, (styleW - 7 * 3) / 8);
			int px = listX;
			int py = y + 6;
			for (char code : FORMAT_CHARS) {
				if (over(mx, my, px, py, cell, cell - 2)) {
					style.setBasic(toggleBasic(style.basic(), code));
					save();
					return true;
				}
				px += cell + 3;
				if ((px - listX) > styleW / 2) {
					px = listX;
					py += cell + 2;
				}
			}
			py += cell + 10 + 14;
			int ccell = Math.max(14, (styleW - 9 * 3) / 10);
			String[] codes = COLOR_CODES;
			for (int i = 0; i < codes.length; i++) {
				int cx = listX + (i % 10) * (ccell + 3);
				int cy = py + (i / 10) * (ccell + 3);
				if (over(mx, my, cx, cy, ccell, ccell - 2)) {
					net.minecraft.ChatFormatting formatting = net.minecraft.ChatFormatting.getByCode(codes[i].charAt(0));
					if (formatting != null && formatting.getColor() != null) {
						style.setPrimary(0xFF000000 | formatting.getColor());
						save();
					}
					return true;
				}
			}
			return true;
		}

		y += 28;
		int tabY = y;
		int tabW = (styleW - 6) / 2;
		if (over(mx, my, listX, tabY, tabW, 16)) {
			color2 = false;
			return true;
		}
		if (over(mx, my, listX + tabW + 6, tabY, tabW, 16)) {
			color2 = true;
			return true;
		}

		int paletteY = tabY + 22;
		int cell = Math.max(14, (styleW - 9 * 3) / 10);
		String[] codes = COLOR_CODES;
		for (int i = 0; i < codes.length; i++) {
			int cx = listX + (i % 10) * (cell + 3);
			int cy = paletteY + (i / 10) * (cell + 3);
			if (over(mx, my, cx, cy, cell, cell - 2)) {
				net.minecraft.ChatFormatting formatting = net.minecraft.ChatFormatting.getByCode(codes[i].charAt(0));
				if (formatting != null && formatting.getColor() != null) {
					int c = 0xFF000000 | formatting.getColor();
					if (color2) {
						style.setSecondary(c);
					} else {
						style.setPrimary(c);
					}
					save();
				}
				return true;
			}
		}

		int doneW = Ui.width(font, "Done") + 18;
		if (over(mx, my, winX + winW - 16 - doneW, footerY - 5, doneW, 18)) {
			click();
			onClose();
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	private String toggleBasic(String basic, char code) {
		if (basic.indexOf(code) >= 0) {
			StringBuilder sb = new StringBuilder(basic.length() - 1);
			for (int i = 0; i < basic.length(); i++) {
				if (basic.charAt(i) == code) {
					continue;
				}
				sb.append(basic.charAt(i));
			}
			return sb.toString();
		}
		return basic + code;
	}

	private void save() {
		ModuleManager.get().save();
	}

	private void click() {
		AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
	}

	private boolean over(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	@Override
	public void onClose() {
		ModuleManager.get().save();
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
