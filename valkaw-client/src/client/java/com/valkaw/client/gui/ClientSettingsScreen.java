package com.valkaw.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;
import com.valkaw.client.module.ModuleManager;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class ClientSettingsScreen extends Screen {
	private static final int CYAN = 0xFF7DF9FF;
	private static final int PURPLE = 0xFFB18CFF;
	private static final int TEXT = 0xFFD7E3FF;
	private static final int MUTED = 0xFF7183A8;
	private static final int GREEN = 0xFF63F5B0;
	private static final int BG_TOP = 0xFF04060D;
	private static final int BG_BOTTOM = 0xFF0A1020;
	private static final int CARD_H = 28;
	private static final int GAP = 5;
	private static final Category[] TABS = Category.values();

	private final Screen parent;
	private Category tab = Category.HUD;
	private boolean bindingEditor;
	private int scroll;
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int tabsY;
	private int tabW;
	private int listTop;
	private int listBottom;
	private int cardW;
	private int colGap = 6;
	private int[] buttonX = new int[4];
	private int buttonY;
	private int buttonW;

	public ClientSettingsScreen(Screen parent) {
		super(Component.literal("Client Settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		panelW = Math.min(600, width - 20);
		panelH = Math.min(340, height - 16);
		panelX = (width - panelW) / 2;
		panelY = (height - panelH) / 2;
		tabsY = panelY + 46;
		tabW = (panelW - 32 - 8) / 3;
		listTop = panelY + 68;
		buttonY = panelY + panelH - 26;
		listBottom = buttonY - 8;
		cardW = (panelW - 32 - colGap) / 2;
		buttonW = (panelW - 32 - 18) / 4;
		for (int i = 0; i < 4; i++) {
			buttonX[i] = panelX + 16 + i * (buttonW + 6);
		}
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fillGradient(0, 0, width, height, BG_TOP, BG_BOTTOM);
		graphics.fillGradient(0, 0, width, 90, 0x0022164A, 0x3322164A);
		graphics.fillGradient(0, height - 100, width, height, 0x001E7FFF, 0x181E7FFF);
		graphics.fillGradient(0, 0, width, 44, 0xCC000000, 0x00000000);
		graphics.fillGradient(0, height - 44, width, height, 0x00000000, 0xCC000000);
		for (int y = 80; y < height; y += 24) {
			graphics.fill(0, y, width, y + 1, 0x067DF9FF);
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE20A1120);
		graphics.fill(panelX, panelY, panelX + panelW, panelY + 1, 0xAA7DF9FF);
		graphics.fill(panelX, panelY, panelX + 1, panelY + panelH, 0x667DF9FF);
		graphics.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, 0x667DF9FF);
		graphics.fill(panelX + 2, panelY, panelX + 18, panelY + 2, PURPLE);
		graphics.fill(panelX + panelW - 18, panelY + panelH - 2, panelX + panelW - 2,
				panelY + panelH, PURPLE);

		graphics.drawString(font, "CLIENT SETTINGS", panelX + 16, panelY + 10, 0xFFFFFFFF, false);
		graphics.drawString(font, "MODULE CONFIGURATION // VALKAW", panelX + 16, panelY + 23, CYAN, false);
		graphics.fill(panelX + 16, panelY + 38, panelX + panelW - 16, panelY + 39, 0x337DF9FF);
		drawTabs(graphics);

		List<Module> list = ModuleManager.get().byCategory(tab);
		int rows = (list.size() + 1) / 2;
		int visible = Math.max(1, (listBottom - listTop) / (CARD_H + GAP));
		scroll = Math.max(0, Math.min(scroll, Math.max(0, rows - visible)));
		graphics.enableScissor(panelX + 8, listTop, panelX + panelW - 8, listBottom);
		for (int r = 0; r < visible; r++) {
			int rowIndex = scroll + r;
			int y = listTop + r * (CARD_H + GAP);
			for (int c = 0; c < 2; c++) {
				int index = rowIndex * 2 + c;
				if (index < list.size()) {
					int x = panelX + 16 + c * (cardW + colGap);
					drawCard(graphics, list.get(index), x, y, mouseX, mouseY);
				}
			}
		}
		graphics.disableScissor();

		if (rows > visible) {
			int trackX = panelX + panelW - 10;
			graphics.fill(trackX, listTop, trackX + 2, listBottom, 0x337DF9FF);
			int thumbH = Math.max(12, (listBottom - listTop) * visible / rows);
			int thumbY = listTop + (listBottom - listTop - thumbH) * scroll / (rows - visible);
			graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, CYAN);
		}

		drawButton(graphics, buttonX[0], buttonY, buttonW, "< BACK", CYAN, mouseX, mouseY);
		drawButton(graphics, buttonX[1], buttonY, buttonW, "HUD EDITOR", PURPLE, mouseX, mouseY);
		drawButton(graphics, buttonX[2], buttonY, buttonW, "RESET POS", PURPLE, mouseX, mouseY);
		drawButton(graphics, buttonX[3], buttonY, buttonW, "EDITOR KEY", PURPLE, mouseX, mouseY);

		if (bindingEditor) {
			String hint = "PRESS A KEY  //  ESC CLEARS";
			graphics.drawString(font, hint, panelX + panelW - 16 - font.width(hint), panelY + 10,
					0xFFFFFFFF, false);
		}
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void drawTabs(GuiGraphics graphics) {
		for (int i = 0; i < TABS.length; i++) {
			Category category = TABS[i];
			int x = panelX + 16 + i * (tabW + 4);
			boolean active = tab == category;
			String label = category.name();
			graphics.fill(x, tabsY, x + tabW, tabsY + 15, active ? 0xCC1B2C48 : 0x66131D30);
			if (active) {
				graphics.fill(x, tabsY + 15, x + tabW, tabsY + 17, CYAN);
			}
			graphics.drawString(font, label, x + (tabW - font.width(label)) / 2, tabsY + 4,
					active ? 0xFFFFFFFF : MUTED, false);
		}
	}

	private void drawCard(GuiGraphics graphics, Module module, int x, int y, int mouseX, int mouseY) {
		boolean hover = mouseX >= x && mouseX < x + cardW && mouseY >= y && mouseY < y + CARD_H;
		boolean active = module.isEnabled();
		boolean lit = active || module.isAction();
		graphics.fill(x, y, x + cardW, y + CARD_H, hover ? 0xCC16233A : 0x66131D30);
		graphics.fill(x, y, x + 2, y + CARD_H, module.isAction() ? PURPLE : active ? CYAN : 0x337DF9FF);
		String name = module.name().toUpperCase();
		while (font.width(name) > cardW - 78 && name.length() > 3) {
			name = name.substring(0, name.length() - 1);
		}
		graphics.drawString(font, name, x + 9, y + 5, lit ? TEXT : MUTED, false);
		String state = module.isAction() ? module.status() : active ? "ON" : "OFF";
		int stateColor = module.isAction() ? PURPLE : active ? GREEN : MUTED;
		graphics.drawString(font, state, x + 9, y + 16, stateColor, false);

		int ow = 52;
		int ox = x + cardW - ow - 6;
		boolean optionHover = over(mouseX, mouseY, ox, y + 6, ow, 16);
		graphics.fill(ox, y + 6, ox + ow, y + 22, optionHover ? 0xCC2C3F5E : 0x442C3F5E);
		graphics.drawString(font, "OPTIONS", ox + (ow - font.width("OPTIONS")) / 2, y + 11,
				optionHover ? 0xFFFFFFFF : CYAN, false);
	}

	private void drawButton(GuiGraphics graphics, int x, int y, int w, String label, int accent, int mouseX,
			int mouseY) {
		boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 18;
		graphics.fill(x, y, x + w, y + 18, hover ? 0xD0162942 : 0xA00E1626);
		graphics.fill(x, y, x + 2, y + 18, accent);
		graphics.drawString(font, label, x + 7, y + 5, hover ? 0xFFFFFFFF : TEXT, false);
	}

	private String keyName(int key) {
		if (key < 0) {
			return "NONE";
		}
		String name = InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString().toUpperCase();
		while (name.length() > 1 && font.width(name) > 60) {
			name = name.substring(0, name.length() - 1);
		}
		return name;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (my >= buttonY && my < buttonY + 18) {
			if (mx >= buttonX[0] && mx < buttonX[0] + buttonW) {
				click();
				onClose();
				return true;
			}
			if (mx >= buttonX[1] && mx < buttonX[1] + buttonW) {
				click();
				minecraft.setScreen(new HudEditorScreen(this));
				return true;
			}
			if (mx >= buttonX[2] && mx < buttonX[2] + buttonW) {
				click();
				ModuleManager.get().resetPositions();
				return true;
			}
			if (mx >= buttonX[3] && mx < buttonX[3] + buttonW) {
				bindingEditor = true;
				return true;
			}
		}

		if (my >= tabsY && my < tabsY + 17) {
			for (int i = 0; i < TABS.length; i++) {
				int x = panelX + 16 + i * (tabW + 4);
				if (mx >= x && mx < x + tabW) {
					tab = TABS[i];
					scroll = 0;
					click();
					return true;
				}
			}
		}

		if (my >= listTop && my < listBottom && mx >= panelX + 16 && mx < panelX + panelW - 16) {
			int row = (int) ((my - listTop) / (CARD_H + GAP)) + scroll;
			int column = mx < panelX + 16 + cardW + colGap / 2 ? 0 : 1;
			List<Module> list = ModuleManager.get().byCategory(tab);
			int index = row * 2 + column;
			if (index < list.size()) {
				Module module = list.get(index);
				int x = panelX + 16 + column * (cardW + colGap);
				int y = listTop + (row - scroll) * (CARD_H + GAP);
				int ow = 52;
				int ox = x + cardW - ow - 6;
				if (over(mx, my, ox, y + 6, ow, 16)) {
					click();
					minecraft.setScreen(new ModuleOptionsScreen(this, module));
					return true;
				}
				if (over(mx, my, x, y, cardW, CARD_H)) {
					if (module.isAction()) {
						module.onKey();
					} else {
						module.toggle();
					}
					ModuleManager.get().save();
					click();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseY >= listTop && mouseY < listBottom) {
			List<Module> list = ModuleManager.get().byCategory(tab);
			int rows = (list.size() + 1) / 2;
			int visible = Math.max(1, (listBottom - listTop) / (CARD_H + GAP));
			int max = Math.max(0, rows - visible);
			scroll = (int) Math.max(0, Math.min(max, scroll - Math.signum(scrollY)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (bindingEditor) {
			ModuleManager.get().setEditorKey(event.key() == GLFW.GLFW_KEY_ESCAPE ? -1 : event.key());
			bindingEditor = false;
			return true;
		}
		return super.keyPressed(event);
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
