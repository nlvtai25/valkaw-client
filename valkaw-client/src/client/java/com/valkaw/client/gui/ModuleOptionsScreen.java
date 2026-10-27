package com.valkaw.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.valkaw.client.module.Anchor;
import com.valkaw.client.module.HudModule;
import com.valkaw.client.module.Module;
import com.valkaw.client.module.ModuleManager;
import com.valkaw.client.module.Setting;
import com.valkaw.client.module.TextStyle;
import com.valkaw.client.module.hud.CrosshairModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Per-module Options page: text style on the left, module rows on the right. */
public class ModuleOptionsScreen extends Screen {
	private static final int CYAN = 0xFF7DF9FF;
	private static final int PURPLE = 0xFFB18CFF;
	private static final int TEXT = 0xFFD7E3FF;
	private static final int MUTED = 0xFF7183A8;
	private static final int GREEN = 0xFF63F5B0;
	private static final int BG_TOP = 0xFF04060D;
	private static final int BG_BOTTOM = 0xFF0A1020;
	private static final int ROW_H = 18;

	private final Screen parent;
	private final Module module;
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int styleX;
	private int styleY;
	private int optionsX;
	private int optionsY;
	private int optionsW;
	private int optionsH;
	private int footerY;
	private int scroll;
	private Setting editing;
	private String editBuffer = "";
	private boolean binding;
	private boolean color2;
	private Setting slider;

	public ModuleOptionsScreen(Screen parent, Module module) {
		super(Component.literal(module.name() + " Options"));
		this.parent = parent;
		this.module = module;
	}

	@Override
	protected void init() {
		panelW = Math.min(600, width - 20);
		panelH = Math.min(340, height - 16);
		panelX = (width - panelW) / 2;
		panelY = (height - panelH) / 2;
		styleX = panelX + 14;
		styleY = panelY + 54;
		optionsX = panelX + 226;
		optionsY = panelY + 54;
		optionsW = panelW - 240;
		optionsH = panelH - 84;
		footerY = panelY + panelH - 26;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fillGradient(0, 0, width, height, BG_TOP, BG_BOTTOM);
		graphics.fillGradient(0, 0, width, 90, 0x0022164A, 0x3322164A);
		graphics.fillGradient(0, height - 100, width, height, 0x001E7FFF, 0x181E7FFF);
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

		graphics.drawString(font, module.name().toUpperCase(), panelX + 16, panelY + 10, 0xFFFFFFFF, false);
		graphics.drawString(font, module.category().name() + " // OPTIONS", panelX + 16, panelY + 23, CYAN, false);
		graphics.fill(panelX + 16, panelY + 38, panelX + panelW - 16, panelY + 39, 0x337DF9FF);

		drawStyle(graphics, mouseX, mouseY);
		drawOptions(graphics, mouseX, mouseY);

		boolean backHover = over(mouseX, mouseY, panelX + 14, footerY, 74, 18);
		graphics.fill(panelX + 14, footerY, panelX + 88, footerY + 18, backHover ? 0xD0162942 : 0xA00E1626);
		graphics.fill(panelX + 14, footerY, panelX + 16, footerY + 18, CYAN);
		graphics.drawString(font, "< BACK", panelX + 22, footerY + 5, backHover ? 0xFFFFFFFF : TEXT, false);
		graphics.drawString(font, "ENTER SAVES  //  ESC CLOSES", panelX + panelW - 16
				- font.width("ENTER SAVES  //  ESC CLOSES"), footerY + 5, MUTED, false);

		if (editing != null) {
			drawEditor(graphics);
		}
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void drawStyle(GuiGraphics graphics, int mouseX, int mouseY) {
		TextStyle style = module.style();
		TextStyle target = style.useGlobal() ? ModuleManager.get().globalStyle() : style;
		graphics.drawString(font, "TEXT STYLE", styleX, styleY - 12, PURPLE, false);
		row(graphics, styleX, styleY, 198, "MODE", target.mode().name(), CYAN,
				over(mouseX, mouseY, styleX, styleY, 198, 15));
		row(graphics, styleX, styleY + 17, 198, "GLOBAL COLOUR", style.useGlobal() ? "ON" : "OFF",
				style.useGlobal() ? GREEN : MUTED, over(mouseX, mouseY, styleX, styleY + 17, 198, 15));

		int tx = styleX;
		int ty = styleY + 36;
		graphics.fill(tx, ty, tx + 96, ty + 14, color2 ? 0xCC2C3F5E : 0x66131D30);
		graphics.drawString(font, "COLOUR 1", tx + 5, ty + 3, color2 ? MUTED : CYAN, false);
		graphics.fill(tx + 102, ty, tx + 198, ty + 14, color2 ? 0xCC2C3F5E : 0x66131D30);
		graphics.drawString(font, "COLOUR 2", tx + 107, ty + 3, color2 ? CYAN : MUTED, false);

		int py = ty + 18;
		String codes = "0123456789abcdef";
		for (int i = 0; i < 16; i++) {
			int col = i % 8;
			int row = i / 8;
			int px = tx + col * 25;
			int yy = py + row * 15;
			Integer rgb = ChatFormatting.getByCode(codes.charAt(i)) == null ? null
					: ChatFormatting.getByCode(codes.charAt(i)).getColor();
			int color = rgb == null ? 0xFFFFFFFF : 0xFF000000 | rgb;
			graphics.fill(px, yy, px + 22, yy + 12, color);
			int selected = target.mode() == TextStyle.Mode.BASIC ? target.color() : color2
					? target.secondary() : target.primary();
			if ((selected & 0xFFFFFF) == (color & 0xFFFFFF)) {
				graphics.renderOutline(px - 1, yy - 1, 24, 14, 0xFFFFFFFF);
			}
		}

		int cy = py + 32;
		row(graphics, styleX, cy, 198, "HEX", target.mode() == TextStyle.Mode.BASIC ? target.basic()
				: TextStyle.hex(color2 ? target.secondary() : target.primary()), TEXT,
				over(mouseX, mouseY, styleX, cy, 198, 15));
		if (hasPrefix()) {
			row(graphics, styleX, cy + 17, 198, "PREFIX", style.prefix().isBlank() ? "(default)" : style.prefix(),
					TEXT, over(mouseX, mouseY, styleX, cy + 17, 198, 15));
			if (style.prefix().isBlank()) {
				graphics.drawString(font, "Custom prefix supports &codes", styleX, cy + 35, MUTED, false);
			}
		}
	}

	private void drawOptions(GuiGraphics graphics, int mouseX, int mouseY) {
		List<Setting> settings = settings();
		int rows = settings.size() + (module.keybindable() ? 1 : 0);
		int visible = Math.max(1, optionsH / ROW_H);
		scroll = Math.max(0, Math.min(scroll, Math.max(0, rows - visible)));
		graphics.enableScissor(optionsX, optionsY, optionsX + optionsW, optionsY + optionsH);
		for (int i = 0; i < visible; i++) {
			int index = scroll + i;
			int y = optionsY + i * ROW_H;
			if (index < settings.size()) {
				drawSetting(graphics, settings.get(index), y, mouseX, mouseY);
			} else if (index == settings.size() && module.keybindable()) {
				boolean hover = over(mouseX, mouseY, optionsX, y, optionsW, 15);
				graphics.fill(optionsX, y, optionsX + optionsW, y + 15, hover ? 0xCC16233A : 0x66131D30);
				graphics.fill(optionsX, y, optionsX + 2, y + 15, PURPLE);
				graphics.drawString(font, "KEYBIND", optionsX + 8, y + 4, TEXT, false);
				String key = binding ? "PRESS A KEY" : keyName(module.key());
				graphics.drawString(font, key, optionsX + optionsW - 8 - font.width(key), y + 4,
						binding ? CYAN : MUTED, false);
			}
		}
		graphics.disableScissor();
		if (rows > visible) {
			int trackX = optionsX + optionsW - 3;
			graphics.fill(trackX, optionsY, trackX + 2, optionsY + optionsH, 0x337DF9FF);
			int thumbH = Math.max(12, optionsH * visible / rows);
			int thumbY = optionsY + (optionsH - thumbH) * scroll / (rows - visible);
			graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, CYAN);
		}
	}

	private void drawSetting(GuiGraphics graphics, Setting setting, int y, int mouseX, int mouseY) {
		boolean hover = over(mouseX, mouseY, optionsX, y, optionsW, 15);
		graphics.fill(optionsX, y, optionsX + optionsW, y + 15, hover ? 0xCC16233A : 0x66131D30);
		graphics.fill(optionsX, y, optionsX + 2, y + 15, hover ? CYAN : 0x557DF9FF);
		graphics.drawString(font, setting.label(), optionsX + 8, y + 4, TEXT, false);
		int right = optionsX + optionsW - 8;
		if (setting.type() == Setting.Type.SLIDER) {
			int barW = sliderBarWidth();
			int barX = right - barW;
			double t = setting.max() <= setting.min() ? 0
					: (setting.number() - setting.min()) / (setting.max() - setting.min());
			t = Math.max(0, Math.min(1, t));
			int knobX = barX + (int) (barW * t);
			graphics.fill(barX, y + 6, barX + barW, y + 10, 0x553B4E70);
			graphics.fill(barX, y + 6, knobX, y + 10, CYAN);
			graphics.fill(knobX - 2, y + 3, knobX + 3, y + 13, 0xFFD7E3FF);
			graphics.renderOutline(knobX - 2, y + 3, 5, 10, CYAN);
			String value = setting.value();
			graphics.drawString(font, value, barX - 6 - font.width(value), y + 4, MUTED, false);
			return;
		}
		String value = setting.value();
		if (setting.type() == Setting.Type.CHOICE || setting.type() == Setting.Type.BUTTON) {
			int w = Math.max(50, font.width(value) + 14);
			graphics.fill(right - w, y + 2, right, y + 13, setting.type() == Setting.Type.BUTTON ? 0xCC2C3F5E : 0x663B4E70);
			graphics.drawString(font, value, right - w + 7, y + 4,
					setting.type() == Setting.Type.BUTTON ? CYAN : GREEN, false);
		} else {
			graphics.drawString(font, value, right - font.width(value), y + 4, MUTED, false);
		}
	}

	private void drawEditor(GuiGraphics graphics) {
		int w = Math.min(360, panelW - 60);
		int x = panelX + (panelW - w) / 2;
		int y = panelY + panelH / 2 - 26;
		graphics.fill(x - 2, y - 2, x + w + 2, y + 44, 0xEE040812);
		graphics.renderOutline(x - 2, y - 2, w + 4, 46, CYAN);
		graphics.drawString(font, editing.label(), x + 8, y + 7, CYAN, false);
		graphics.fill(x + 8, y + 20, x + w - 8, y + 36, 0x66131D30);
		String visible = editBuffer;
		int max = w - 34;
		while (font.width(visible) > max && visible.length() > 1) {
			visible = visible.substring(1);
		}
		graphics.drawString(font, visible, x + 12, y + 25, TEXT, false);
		graphics.fill(x + 12 + font.width(visible), y + 23,
				x + 13 + font.width(visible), y + 34, CYAN);
		graphics.drawString(font, "ENTER SAVE  //  ESC CANCEL", x + 8, y + 39, MUTED, false);
	}

	private void row(GuiGraphics graphics, int x, int y, int w, String label, String value, int valueColor,
			boolean hover) {
		graphics.fill(x, y, x + w, y + 15, hover ? 0xCC16233A : 0x66131D30);
		graphics.fill(x, y, x + 2, y + 15, hover ? CYAN : 0x557DF9FF);
		graphics.drawString(font, label, x + 7, y + 4, TEXT, false);
		String shown = value;
		while (font.width(shown) > w - 60 && shown.length() > 3) {
			shown = shown.substring(0, shown.length() - 1);
		}
		graphics.drawString(font, shown, x + w - 7 - font.width(shown), y + 4, valueColor, false);
	}

	private List<Setting> settings() {
		List<Setting> list = new ArrayList<>();
		if (module instanceof HudModule hud) {
			list.add(Setting.choice("SIDE", () -> hud.anchor().name(), () -> {
				hud.linkTo(hud.anchor() == Anchor.RIGHT ? Anchor.LEFT : Anchor.RIGHT,
						minecraft.getWindow().getGuiScaledWidth());
				ModuleManager.get().save();
			}));
		}
		list.addAll(module.settings());
		if (module.id().equals("crosshair")) {
			list.add(Setting.button("PATTERN EDITOR", () -> minecraft.setScreen(
					new CrosshairEditorScreen(this, (CrosshairModule) module))));
		}
		return list;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (mx >= panelX + 14 && mx < panelX + 88 && my >= footerY && my < footerY + 18) {
			AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
			onClose();
			return true;
		}
		if (editing != null) {
			return true;
		}
		TextStyle style = module.style();
		TextStyle target = style.useGlobal() ? ModuleManager.get().globalStyle() : style;
		if (over(mx, my, styleX, styleY, 198, 15)) {
			target.setMode(target.mode().next());
			save();
			return true;
		}
		if (over(mx, my, styleX, styleY + 17, 198, 15)) {
			style.setUseGlobal(!style.useGlobal());
			save();
			return true;
		}
		if (over(mx, my, styleX, styleY + 36, 96, 14)) {
			color2 = false;
			return true;
		}
		if (over(mx, my, styleX + 102, styleY + 36, 96, 14)) {
			color2 = true;
			return true;
		}
		int paletteY = styleY + 54;
		for (int i = 0; i < 16; i++) {
			int px = styleX + (i % 8) * 25;
			int py = paletteY + (i / 8) * 15;
			if (over(mx, my, px, py, 22, 12)) {
				Character code = "0123456789abcdef".charAt(i);
				ChatFormatting formatting = ChatFormatting.getByCode(code);
				Integer rgb = formatting == null ? null : formatting.getColor();
				if (target.mode() == TextStyle.Mode.BASIC) {
					target.setBasic("&" + code);
				} else if (color2 && target.mode() == TextStyle.Mode.GRADIENT) {
					target.setSecondary(rgb == null ? 0xFFFFFFFF : 0xFF000000 | rgb);
				} else {
					target.setPrimary(rgb == null ? 0xFFFFFFFF : 0xFF000000 | rgb);
				}
				save();
				return true;
			}
		}
		int hexY = paletteY + 32;
		if (over(mx, my, styleX, hexY, 198, 15)) {
			editing = Setting.text("HEX COLOUR", () -> target.mode() == TextStyle.Mode.BASIC ? target.basic()
					: TextStyle.hex(color2 ? target.secondary() : target.primary()),
					value -> {
						if (target.mode() == TextStyle.Mode.BASIC) {
							target.setBasic(value);
						} else {
							int parsed = TextStyle.parseColor(value, color2 ? target.secondary() : target.primary());
							if (color2 && target.mode() == TextStyle.Mode.GRADIENT) {
								target.setSecondary(parsed);
							} else {
								target.setPrimary(parsed);
							}
						}
						save();
					});
			editBuffer = editing.value();
			return true;
		}
		if (hasPrefix() && over(mx, my, styleX, hexY + 17, 198, 15)) {
			editing = Setting.text("PREFIX", () -> style.prefix(),
					value -> {
						style.setPrefix(value);
						save();
					});
			editBuffer = editing.value();
			return true;
		}

		if (my >= optionsY && my < optionsY + optionsH && mx >= optionsX && mx < optionsX + optionsW) {
			List<Setting> settings = settings();
			int index = (int) ((my - optionsY) / ROW_H) + scroll;
			if (index < settings.size()) {
				Setting setting = settings.get(index);
				if (setting.type() == Setting.Type.TEXT) {
					editing = setting;
					editBuffer = setting.value();
				} else if (setting.type() == Setting.Type.SLIDER) {
					slider = setting;
					setSlider(setting, mx);
					return true;
				} else if (setting.type() == Setting.Type.BUTTON) {
					setting.cycle();
				} else {
					setting.cycle();
					save();
				}
				return true;
			}
			if (index == settings.size() && module.keybindable()) {
				binding = true;
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (slider != null) {
			setSlider(slider, event.x());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (slider != null) {
			slider = null;
			save();
			return true;
		}
		return super.mouseReleased(event);
	}

	private int sliderBarWidth() {
		return Math.min(90, optionsW / 2);
	}

	private void setSlider(Setting setting, double mouseX) {
		int barX = optionsX + optionsW - 8 - sliderBarWidth();
		double t = Math.max(0.0, Math.min(1.0, (mouseX - barX) / sliderBarWidth()));
		double value = setting.min() + t * (setting.max() - setting.min());
		if (setting.step() > 0) {
			value = Math.round(value / setting.step()) * setting.step();
		}
		setting.setNumber(value);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= optionsX && mouseY >= optionsY && mouseY < optionsY + optionsH) {
			List<Setting> settings = settings();
			int rows = settings.size() + (module.keybindable() ? 1 : 0);
			int visible = Math.max(1, optionsH / ROW_H);
			scroll = (int) Math.max(0, Math.min(Math.max(0, rows - visible),
					scroll - Math.signum(scrollY)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (editing != null) {
			if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
				editing = null;
				return true;
			}
			if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
				editing.setText(editBuffer);
				editing = null;
				save();
				return true;
			}
			if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
				if (!editBuffer.isEmpty()) {
					editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
				}
				return true;
			}
			return false;
		}
		if (binding) {
			module.setKey(event.key() == GLFW.GLFW_KEY_ESCAPE ? -1 : event.key());
			binding = false;
			save();
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (editing != null) {
			if (event.codepoint() >= 32 && event.codepoint() != 127 && editBuffer.length() < 64) {
				editBuffer += event.codepointAsString();
			}
			return true;
		}
		return super.charTyped(event);
	}

	private String keyName(int key) {
		if (key < 0) {
			return "NONE";
		}
		String name = InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString().toUpperCase();
		while (name.length() > 1 && font.width(name) > 86) {
			name = name.substring(0, name.length() - 1);
		}
		return name;
	}

	private void save() {
		ModuleManager.get().save();
	}

	private boolean hasPrefix() {
		return !module.id().equals("crosshair") && !module.id().equals("block-outline");
	}

	private static boolean over(double mx, double my, int x, int y, int w, int h) {
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
