package com.valkaw.client.gui;

import com.valkaw.client.module.ModuleManager;
import com.valkaw.client.module.hud.CrosshairModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** 15x15 pixel editor: left adds, right removes, save writes the custom crosshair. */
public class CrosshairEditorScreen extends Screen {
	private static final int CYAN = 0xFF7DF9FF;
	private static final int PURPLE = 0xFFB18CFF;
	private static final int TEXT = 0xFFD7E3FF;
	private static final int MUTED = 0xFF7183A8;

	private final Screen parent;
	private final CrosshairModule module;
	private final boolean[][] grid = new boolean[CrosshairModule.GRID][CrosshairModule.GRID];
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int gridX;
	private int gridY;
	private int cell;
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
		panelW = Math.min(600, width - 20);
		panelH = Math.min(340, height - 16);
		panelX = (width - panelW) / 2;
		panelY = (height - panelH) / 2;
		cell = Math.max(8, Math.min(14, (panelH - 116) / CrosshairModule.GRID));
		gridX = panelX + 24;
		gridY = panelY + 58;
		footerY = panelY + panelH - 26;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fillGradient(0, 0, width, height, 0xFF04060D, 0xFF0A1020);
		graphics.fillGradient(0, 0, width, 90, 0x0022164A, 0x3322164A);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE20A1120);
		graphics.fill(panelX, panelY, panelX + panelW, panelY + 1, 0xAA7DF9FF);
		graphics.fill(panelX + 2, panelY, panelX + 18, panelY + 2, PURPLE);
		graphics.drawString(font, "CROSSHAIR EDITOR", panelX + 16, panelY + 10, 0xFFFFFFFF, false);
		graphics.drawString(font, "15 x 15 // LEFT ADD  RIGHT REMOVE", panelX + 16, panelY + 23, CYAN, false);
		graphics.fill(panelX + 16, panelY + 38, panelX + panelW - 16, panelY + 39, 0x337DF9FF);

		int size = CrosshairModule.GRID * cell;
		graphics.fill(gridX - 1, gridY - 1, gridX + size + 1, gridY + size + 1, 0x66131D30);
		for (int y = 0; y < CrosshairModule.GRID; y++) {
			for (int x = 0; x < CrosshairModule.GRID; x++) {
				int px = gridX + x * cell;
				int py = gridY + y * cell;
				boolean hover = mouseX >= px && mouseX < px + cell && mouseY >= py && mouseY < py + cell;
				graphics.fill(px, py, px + cell - 1, py + cell - 1,
						grid[y][x] ? CYAN : hover ? 0x553B4E70 : 0x22131D30);
				if (grid[y][x]) {
					graphics.renderOutline(px, py, cell - 1, cell - 1, 0xFFFFFFFF);
				}
			}
		}

		int previewX = gridX + size + 44;
		int previewY = gridY + size / 2;
		graphics.drawString(font, "PREVIEW", previewX, gridY - 14, MUTED, false);
		graphics.fill(previewX - 4, gridY, previewX + 70, gridY + size, 0x66131D30);
		graphics.renderOutline(previewX - 4, gridY, 74, size, 0x557DF9FF);
		int cx = previewX + 33;
		int cy = previewY;
		for (int y = 0; y < CrosshairModule.GRID; y++) {
			for (int x = 0; x < CrosshairModule.GRID; x++) {
				if (grid[y][x]) {
					int px = cx - CrosshairModule.GRID / 2 + x;
					int py = cy - CrosshairModule.GRID / 2 + y;
					graphics.fill(px, py, px + 1, py + 1, module.color());
				}
			}
		}
		graphics.drawString(font, "CUSTOM OVERRIDES THE PRESET", previewX, previewY + CrosshairModule.GRID / 2 + 8,
				MUTED, false);

		drawButton(graphics, panelX + 16, footerY, 80, "CLEAR", PURPLE, mouseX, mouseY);
		drawButton(graphics, panelX + 102, footerY, 80, "CANCEL", MUTED, mouseX, mouseY);
		drawButton(graphics, panelX + panelW - 96, footerY, 80, "SAVE", CYAN, mouseX, mouseY);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void drawButton(GuiGraphics graphics, int x, int y, int w, String label, int accent, int mouseX,
			int mouseY) {
		boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 18;
		graphics.fill(x, y, x + w, y + 18, hover ? 0xD0162942 : 0xA00E1626);
		graphics.fill(x, y, x + 2, y + 18, accent);
		graphics.drawString(font, label, x + 8, y + 5, hover ? 0xFFFFFFFF : TEXT, false);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (my >= footerY && my < footerY + 18) {
			if (mx >= panelX + 16 && mx < panelX + 96) {
				clear();
				click();
				return true;
			}
			if (mx >= panelX + 102 && mx < panelX + 182) {
				click();
				onClose();
				return true;
			}
			if (mx >= panelX + panelW - 96 && mx < panelX + panelW - 16) {
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
			java.util.Arrays.fill(row, false);
		}
	}

	private void click() {
		AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
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
