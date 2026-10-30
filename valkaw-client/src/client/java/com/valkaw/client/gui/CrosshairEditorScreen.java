package com.valkaw.client.gui;

import com.valkaw.client.module.ModuleManager;
import com.valkaw.client.module.hud.CrosshairModule;
import java.util.Arrays;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** 15x15 pixel editor: left adds, right removes, save writes the custom crosshair. */
public class CrosshairEditorScreen extends Screen {
	private final Screen parent;
	private final CrosshairModule module;
	private final boolean[][] grid = new boolean[CrosshairModule.GRID][CrosshairModule.GRID];
	private int winX;
	private int winY;
	private int winW;
	private int winH;
	private int gridX;
	private int gridY;
	private int cell;
	private int previewX;
	private int footerY;

	public CrosshairEditorScreen(Screen parent, CrosshairModule module) {
		super(Component.literal("Crosshair Editor"));
		this.parent = parent;
		this.module = module;
		for (int y = 0; y < CrosshairModule.GRID; y++) {
			for (int x = 0; x < CrosshairModule.GRID; x++) {
				grid[y][x] = module.pixel(x, y);
			}
		}
	}

	@Override
	protected void init() {
		winW = Math.min(580, width - 24);
		winH = Math.min(340, height - 20);
		winX = (width - winW) / 2;
		winY = (height - winH) / 2;
		cell = Math.max(9, Math.min(16, (winH - 130) / CrosshairModule.GRID));
		gridX = winX + 22;
		gridY = winY + 68;
		previewX = gridX + CrosshairModule.GRID * cell + 28;
		footerY = winY + winH - 34;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		Ui.backdrop(graphics, width, height, true);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		Ui.window(graphics, winX, winY, winW, winH, Ui.COPPER);
		drawHeader(graphics);
		drawGrid(graphics, mouseX, mouseY);
		drawPreview(graphics);
		drawFooter(graphics, mouseX, mouseY);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void drawHeader(GuiGraphics graphics) {
		int x = winX + 22;
		int y = winY + 14;
		Ui.tracked(graphics, font, "CROSSHAIR", x, y, Ui.MUTED);
		graphics.drawString(font, "Pixel editor", x, y + 12, Ui.TEXT, false);
		graphics.drawString(font, "LEFT ADD  ·  RIGHT REMOVE", x, y + 24, Ui.MUTED, false);
		Ui.divider(graphics, winX + 14, winY + 48, winW - 28);
	}

	private void drawGrid(GuiGraphics graphics, int mouseX, int mouseY) {
		int size = CrosshairModule.GRID * cell;
		Ui.panel(graphics, gridX - 6, gridY - 6, size + 12, size + 12, Ui.R_CARD, 0xF00A1120);

		int center = CrosshairModule.GRID / 2;
		graphics.fill(gridX + center * cell, gridY - 4, gridX + center * cell + cell - 1, gridY + size + 4,
				0x157DF9FF);
		graphics.fill(gridX - 4, gridY + center * cell, gridX + size + 4, gridY + center * cell + cell - 1,
				0x157DF9FF);

		for (int y = 0; y < CrosshairModule.GRID; y++) {
			for (int x = 0; x < CrosshairModule.GRID; x++) {
				int px = gridX + x * cell;
				int py = gridY + y * cell;
				boolean hover = mouseX >= px && mouseX < px + cell - 1 && mouseY >= py && mouseY < py + cell - 1;
				int fill = grid[y][x] ? 0xFF1B3B54 : hover ? 0xFF1D2A44 : 0xFF141C30;
				Ui.round(graphics, px, py, cell - 1, cell - 1, 2, fill);
				if (grid[y][x]) {
					Ui.round(graphics, px + cell / 2 - 1, py + cell / 2 - 1, 2, 2, 1, module.color());
					graphics.renderOutline(px, py, cell - 1, cell - 1, Ui.alpha(module.color(), 0.7F));
				}
			}
		}
	}

	private void drawPreview(GuiGraphics graphics) {
		int areaW = Math.min(150, winX + winW - 18 - previewX);
		if (areaW < 60) {
			return;
		}
		int areaH = winH - 120;
		Ui.panel(graphics, previewX, gridY - 6, areaW, areaH, Ui.R_CARD, 0xF00A1120);
		graphics.drawString(font, "PREVIEW", previewX + 10, gridY + 2, Ui.MUTED, false);

		int cx = previewX + areaW / 2;
		int cy = gridY + areaH / 2;
		int scale = Math.max(2, Math.min(5, (areaH - 50) / CrosshairModule.GRID));
		for (int y = 0; y < CrosshairModule.GRID; y++) {
			for (int x = 0; x < CrosshairModule.GRID; x++) {
				if (grid[y][x]) {
					int px = cx - CrosshairModule.GRID * scale / 2 + x * scale;
					int py = cy - CrosshairModule.GRID * scale / 2 + y * scale;
					graphics.fill(px, py, px + scale, py + scale, module.color());
				}
			}
		}

		String note = "Overrides the preset";
		graphics.drawString(font, Ui.fit(font, note, areaW - 20), previewX + 10,
				gridY + areaH - 22, Ui.MUTED, false);
	}

	private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
		Ui.divider(graphics, winX + 14, footerY - 12, winW - 28);
		int x = winX + 18;
		x += Ui.button(graphics, font, x, footerY - 6, "Clear", Ui.COPPER,
				over(mouseX, mouseY, x, footerY - 6, Ui.width(font, "Clear") + 16, 18), false) + 6;
		Ui.button(graphics, font, x, footerY - 6, "Cancel", Ui.MUTED,
				over(mouseX, mouseY, x, footerY - 6, Ui.width(font, "Cancel") + 16, 18), false);

		int saveW = Ui.width(font, "Save") + 18;
		int saveX = winX + winW - 18 - saveW;
		Ui.button(graphics, font, saveX, footerY - 6, "Save", Ui.ACCENT,
				over(mouseX, mouseY, saveX, footerY - 6, saveW, 18), true);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();

		if (my >= footerY - 6 && my < footerY + 12) {
			int clearW = Ui.width(font, "Clear") + 16;
			int cancelW = Ui.width(font, "Cancel") + 16;
			int saveW = Ui.width(font, "Save") + 18;
			if (mx >= winX + 18 && mx < winX + 18 + clearW) {
				clear();
				click();
				return true;
			}
			if (mx >= winX + 24 + clearW && mx < winX + 24 + clearW + cancelW) {
				click();
				onClose();
				return true;
			}
			if (mx >= winX + winW - 18 - saveW && mx < winX + winW - 18) {
				module.setCustom(grid, true);
				ModuleManager.get().save();
				click();
				onClose();
				return true;
			}
		}

		int size = CrosshairModule.GRID * cell;
		if (mx >= gridX && mx < gridX + size && my >= gridY && my < gridY + size) {
			int x = (int) ((mx - gridX) / cell);
			int y = (int) ((my - gridY) / cell);
			if (x >= 0 && y >= 0 && x < CrosshairModule.GRID && y < CrosshairModule.GRID) {
				grid[y][x] = event.button() == 0;
				click();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void clear() {
		for (boolean[] row : grid) {
			Arrays.fill(row, false);
		}
	}

	private void click() {
		AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
	}

	private static boolean over(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
