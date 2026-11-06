package com.valkaw.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.valkaw.client.module.Anchor;
import com.valkaw.client.module.HudModule;
import com.valkaw.client.keybinds.Keybinds;
import com.valkaw.client.module.Module;
import com.valkaw.client.module.ModuleManager;
import com.valkaw.client.module.Setting;
import com.valkaw.client.module.TextStyle;
import com.valkaw.client.module.hud.CrosshairModule;
import com.valkaw.client.module.utility.AutoTextModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Options for one module: text style on the left, settings on the right. */
public class ModuleOptionsScreen extends Screen {
	private static final int ROW_H = 32;
	private static final int ROW_GAP = 3;
	private static final int SEGMENT_GAP = 4;
	private static final String CODES = "0123456789abcdef";

	private final Screen parent;
	private final Module module;
	private final Ui.Anim toggleAnim = new Ui.Anim(0.0F);
	private int winX;
	private int winY;
	private int winW;
	private int winH;
	private int bodyY;
	private int bodyH;
	private int styleX;
	private int styleY;
	private int styleW;
	private int listX;
	private int listW;
	private int footerY;
	private int scroll;
	private Setting editing;
	private String editBuffer = "";
	private boolean binding;
	private boolean color2;
	private Setting slider;
	private List<Setting> rows;
	private List<AutoTextModule.Entry> atEntries;
	private int atBinding = -1;
	private int atEditing = -1;
	private String atBuffer = "";
	private final java.util.Map<Integer, String> keyNames = new java.util.HashMap<>();
	private final java.util.Map<Integer, String> atKeyNames = new java.util.HashMap<>();

	public ModuleOptionsScreen(Screen parent, Module module) {
		super(Component.literal(module.name() + " Options"));
		this.parent = parent;
		this.module = module;
		toggleAnim.to(module.isEnabled() ? 1.0F : 0.0F);
	}

	@Override
	protected void init() {
		winW = Math.min(740, width - 28);
		winH = Math.min(470, height - 24);
		winX = (width - winW) / 2;
		winY = (height - winH) / 2;
		bodyY = winY + 58;
		bodyH = winH - 58 - 44;
		footerY = winY + winH - 30;

		boolean styled = styled();
		styleW = Math.min(220, winW / 2 - 20);
		styleX = winX + 16;
		styleY = bodyY + 8;
		listX = styled ? styleX + styleW + 14 : winX + 16;
		listW = winX + winW - 16 - listX;
		rows = buildSettings();
		if (module instanceof AutoTextModule at) {
			atEntries = new ArrayList<>();
			for (AutoTextModule.Entry entry : at.entries()) {
				atEntries.add(new AutoTextModule.Entry(entry.text, entry.key, entry.mods));
			}
			atBinding = -1;
			atEditing = -1;
		}
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		Ui.backdrop(graphics, width, height, false);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		float dt = Ui.dt();
		Ui.window(graphics, winX, winY, winW, winH, accent());
		drawHeader(graphics, mouseX, mouseY, dt);
		if (styled()) {
			drawStyle(graphics, mouseX, mouseY);
		}
		drawSettings(graphics, mouseX, mouseY);
		drawFooter(graphics, mouseX, mouseY);
		if (editing != null) {
			drawEditor(graphics);
		}
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void drawHeader(GuiGraphics graphics, int mouseX, int mouseY, float dt) {
		int x = winX + 16;
		int y = winY + 14;
		boolean backHover = over(mouseX, mouseY, x, y + 4, 18, 18);
		Ui.round(graphics, x, y + 4, 18, 18, 5, backHover ? 0x1FFFFFFF : 0x0CFFFFFF);
		Ui.chevron(graphics, x + 6, y + 9, backHover ? Ui.ACCENT : Ui.TEXT_2, false);

		graphics.drawString(font, Ui.fit(font, module.name(), winW - 260), x + 28, y + 2, Ui.TEXT, false);
		// Actions and key-held modules have no on/off state, so the header shows
		// what they currently hold instead of ENABLED/DISABLED.
		String state;
		if (module.isAction()) {
			state = module.status();
		} else if (module.keyOnly()) {
			state = "HOLD " + keyName(module.key());
		} else {
			state = module.isEnabled() ? "ENABLED" : "DISABLED";
		}
		boolean switchable = !module.isAction() && !module.keyOnly();
		String sub = module.category().name() + "  ·  " + state;
		graphics.drawString(font, sub, x + 28, y + 15,
				switchable ? (module.isEnabled() ? accent() : Ui.MUTED) : Ui.MUTED, false);

		if (switchable) {
			toggleAnim.to(module.isEnabled() ? 1.0F : 0.0F).speed(18.0F);
			int toggleX = winX + winW - 78;
			Ui.toggle(graphics, toggleX, y + 5, toggleAnim.update(dt), accent());
		}

		boolean closeHover = over(mouseX, mouseY, winX + winW - 34, y + 4, 18, 18);
		Ui.round(graphics, winX + winW - 34, y + 4, 18, 18, 5, closeHover ? Ui.alpha(Ui.RED, 0.18F) : 0x0CFFFFFF);
		Ui.closeIcon(graphics, winX + winW - 28, y + 10, closeHover ? Ui.RED : Ui.MUTED);
		Ui.divider(graphics, winX + 14, winY + 44, winW - 28);
	}

	private void drawStyle(GuiGraphics graphics, int mouseX, int mouseY) {
		TextStyle style = module.style();
		TextStyle target = style.useGlobal() ? ModuleManager.get().globalStyle() : style;

		Ui.tracked(graphics, font, "TEXT STYLE", styleX, styleY, Ui.MUTED);
		int y = styleY + 16;
		row(graphics, styleX, y, styleW, "Mode", target.mode().name(), Ui.ACCENT, mouseX, mouseY);
		row(graphics, styleX, y + 20, styleW, "Global colour", style.useGlobal() ? "On" : "Off",
				style.useGlobal() ? Ui.GREEN : Ui.MUTED, mouseX, mouseY);

		int tabY = y + 44;
		int tabW = (styleW - 6) / 2;
		pick(graphics, styleX, tabY, tabW, "Colour 1", !color2, mouseX, mouseY);
		pick(graphics, styleX + tabW + 6, tabY, tabW, "Colour 2", color2, mouseX, mouseY);

		int paletteY = tabY + 22;
		int cell = Math.max(14, (styleW - 7 * 3) / 8);
		for (int i = 0; i < 16; i++) {
			int px = styleX + (i % 8) * (cell + 3);
			int py = paletteY + (i / 8) * (cell + 3);
			ChatFormatting formatting = ChatFormatting.getByCode(CODES.charAt(i));
			int color = formatting == null || formatting.getColor() == null ? 0xFFFFFFFF
					: 0xFF000000 | formatting.getColor();
			Ui.round(graphics, px, py, cell, cell - 2, 3, color);
			int selected = target.mode() == TextStyle.Mode.BASIC ? target.color()
					: color2 ? target.secondary() : target.primary();
			if ((selected & 0xFFFFFF) == (color & 0xFFFFFF)) {
				graphics.renderOutline(px - 1, py - 1, cell + 2, cell, 0xFFFFFFFF);
			}
		}

		int hexY = paletteY + 2 * (cell + 3) + 6;
		row(graphics, styleX, hexY, styleW, "Hex",
				target.mode() == TextStyle.Mode.BASIC ? target.basic()
						: TextStyle.hex(color2 ? target.secondary() : target.primary()),
				Ui.TEXT, mouseX, mouseY);
		if (hasPrefix()) {
			String prefix = style.prefix().isBlank() ? "(default)" : style.prefix();
			row(graphics, styleX, hexY + 20, styleW, "Prefix", prefix, Ui.TEXT, mouseX, mouseY);
		}
	}

	private void drawSettings(GuiGraphics graphics, int mouseX, int mouseY) {
		List<Setting> settings = rows;
		boolean at = module instanceof AutoTextModule;
		int base = settings.size() + (module.keybindable() ? 1 : 0);
		int rows = base + (at ? atEntries.size() + 1 : 0);
		graphics.drawString(font, "OPTIONS", listX, bodyY - 4, Ui.MUTED);

		int top = bodyY + 8;
		int visible = Math.max(1, (bodyH - 8) / (ROW_H + ROW_GAP));
		scroll = Math.max(0, Math.min(scroll, Math.max(0, rows - visible)));

		graphics.enableScissor(listX - 2, top, listX + listW + 2, top + bodyH - 8);
		for (int i = 0; i < visible; i++) {
			int index = scroll + i;
			int y = top + i * (ROW_H + ROW_GAP);
			if (index < settings.size()) {
				drawRow(graphics, settings.get(index), y, mouseX, mouseY);
			} else if (index == settings.size() && module.keybindable()) {
				drawKeyRow(graphics, y, mouseX, mouseY);
			} else if (at) {
				int ai = index - base;
				if (ai == 0 && atEntries.size() < AutoTextModule.MAX_ENTRIES) {
					drawAddRow(graphics, y, mouseX, mouseY);
				} else {
					int ei = ai - 1;
					if (ei >= 0 && ei < atEntries.size()) {
						drawAtRow(graphics, ei, y, mouseX, mouseY);
					}
				}
			}
		}
		graphics.disableScissor();

		if (rows > visible) {
			Ui.scrollbar(graphics, listX + listW + 5, top, bodyH - 12,
					(float) scroll / (rows - visible), (float) visible / rows, accent());
		}
	}

	private void drawRow(GuiGraphics graphics, Setting setting, int y, int mouseX, int mouseY) {
		boolean hot = over(mouseX, mouseY, listX, y, listW, ROW_H - 4);
		Ui.round(graphics, listX, y, listW, ROW_H - 4, Ui.R_CARD, hot ? 0xFF16203A : 0xF00F1727);
		graphics.drawString(font, Ui.fit(font, setting.label(), listW - 120), listX + 10, y + 9,
				hot ? Ui.TEXT : Ui.TEXT_2, false);

		int right = listX + listW - 10;
		if (setting.type() == Setting.Type.SLIDER) {
			int barW = Math.min(120, Math.max(60, listW / 3));
			int barX = right - barW;
			double t = setting.max() <= setting.min() ? 0
					: (setting.number() - setting.min()) / (setting.max() - setting.min());
			Ui.slider(graphics, barX, y + 15, barW, (float) Math.max(0, Math.min(1, t)), accent());
			Ui.textRight(graphics, font, setting.value(), barX - 8, y + 9, Ui.TEXT);
			return;
		}

		String value = setting.value();
		if (setting.type() == Setting.Type.BUTTON) {
			int w = Ui.width(font, value) + 20;
			Ui.round(graphics, right - w, y + 6, w, 17, Ui.R_PILL, hot ? Ui.alpha(Ui.ACCENT, 0.8F) : 0x2A7DF9FF);
			graphics.drawString(font, value, right - w + 10, y + 10, hot ? Ui.INK : Ui.ACCENT, false);
		} else if (setting.type() == Setting.Type.SEGMENT) {
			int x = right;
			for (int i = setting.options().size() - 1; i >= 0; i--) {
				int w = segmentWidth(setting, i);
				x -= w;
		pick(graphics, x, y + 6, w, setting.options().get(i), setting.selected() == i, mouseX, mouseY);
				x -= SEGMENT_GAP;
			}
			return;
		} else if (setting.type() == Setting.Type.CHOICE) {
			int w = Ui.width(font, value) + 22;
			Ui.round(graphics, right - w, y + 6, w, 17, Ui.R_PILL, hot ? 0x22FFFFFF : 0x14FFFFFF);
			graphics.drawString(font, value, right - w + 8, y + 10, Ui.GREEN, false);
			Ui.chevron(graphics, right - 9, y + 11, Ui.MUTED, true);
		} else {
			Ui.textRight(graphics, font, Ui.fit(font, value, listW / 2), right, y + 10, Ui.MUTED);
		}
	}

	private void drawKeyRow(GuiGraphics graphics, int y, int mouseX, int mouseY) {
		boolean hot = over(mouseX, mouseY, listX, y, listW, ROW_H - 4);
		Ui.round(graphics, listX, y, listW, ROW_H - 4, Ui.R_CARD, hot ? 0xFF16203A : 0xF00F1727);
		graphics.drawString(font, "Shortcut", listX + 10, y + 9, hot ? Ui.TEXT : Ui.TEXT_2, false);
		String key = binding ? "PRESS A KEY" : keyName(module.key());
		int w = Ui.width(font, key) + 20;
		Ui.round(graphics, listX + listW - 10 - w, y + 6, w, 17, Ui.R_PILL,
				binding ? Ui.alpha(Ui.ACCENT, 0.22F) : 0x14FFFFFF);
		graphics.drawString(font, key, listX + listW - 10 - w + 10, y + 10, binding ? Ui.ACCENT : Ui.MUTED, false);
	}

	private void drawAtRow(GuiGraphics graphics, int ai, int y, int mouseX, int mouseY) {
		AutoTextModule.Entry entry = atEntries.get(ai);
		boolean hot = over(mouseX, mouseY, listX, y, listW, ROW_H - 4);
		Ui.round(graphics, listX, y, listW, ROW_H - 4, Ui.R_CARD, hot ? 0xFF16203A : 0xF00F1727);
		graphics.drawString(font, (ai + 1) + ".", listX + 10, y + 9, Ui.MUTED, false);
		boolean editingHere = atEditing == ai;
		String text = editingHere ? atBuffer : (entry.text.isEmpty() ? "(empty)" : entry.text);
		int textX = listX + 34;
		graphics.drawString(font, Ui.fit(font, text, listW - 190), textX, y + 9,
				editingHere ? Ui.ACCENT : (entry.text.isEmpty() ? Ui.MUTED : Ui.TEXT), false);
		if (editingHere && (System.currentTimeMillis() / 500) % 2 == 0) {
			int caretX = textX + Ui.width(font, Ui.fit(font, text, listW - 190));
			graphics.fill(caretX + 1, y + 6, caretX + 2, y + 18, Ui.ACCENT);
		}

		boolean bindingHere = atBinding == ai;
		String key = bindingHere ? "PRESS KEY..." : keyNameAt(entry);
		int w = Ui.width(font, key) + 20;
		Ui.round(graphics, listX + listW - 40 - w, y + 6, w, 17, Ui.R_PILL,
				bindingHere ? Ui.alpha(Ui.ACCENT, 0.22F) : 0x14FFFFFF);
		graphics.drawString(font, key, listX + listW - 40 - w + 10, y + 10,
				bindingHere ? Ui.ACCENT : Ui.MUTED, false);

		boolean delHover = over(mouseX, mouseY, listX + listW - 30, y + 5, 18, 18);
		Ui.round(graphics, listX + listW - 30, y + 5, 18, 18, 5,
				delHover ? Ui.alpha(Ui.RED, 0.18F) : 0x0CFFFFFF);
		Ui.closeIcon(graphics, listX + listW - 25, y + 11, delHover ? Ui.RED : Ui.MUTED);
	}

	private void drawAddRow(GuiGraphics graphics, int y, int mouseX, int mouseY) {
		boolean hot = over(mouseX, mouseY, listX, y, listW, ROW_H - 4);
		Ui.round(graphics, listX, y, listW, ROW_H - 4, Ui.R_CARD, hot ? 0xFF16203A : 0xF00F1727);
		graphics.drawString(font, "+ ADD LINE", listX + (listW - Ui.width(font, "+ ADD LINE")) / 2,
				y + 9, hot ? Ui.ACCENT : Ui.MUTED, false);
	}

	private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
		Ui.divider(graphics, winX + 14, footerY - 12, winW - 28);
		graphics.drawString(font, "Changes save automatically", winX + 16, footerY, Ui.MUTED);
		int w = Ui.width(font, "Done") + 18;
		Ui.button(graphics, font, winX + winW - 16 - w, footerY - 5, "Done", accent(),
				over(mouseX, mouseY, winX + winW - 16 - w, footerY - 5, w, 18), true);
	}

	private void drawEditor(GuiGraphics graphics) {
		int w = Math.min(340, winW - 60);
		int x = winX + (winW - w) / 2;
		int y = winY + winH / 2 - 34;
		Ui.panel(graphics, x, y, w, 62, Ui.R_PANEL, 0xFF0B1220);
		graphics.drawString(font, editing.label(), x + 12, y + 9, Ui.ACCENT, false);
		Ui.round(graphics, x + 12, y + 22, w - 24, 20, 5, 0x66131D30);
		String visible = editBuffer;
		while (Ui.width(font, visible) > w - 46 && visible.length() > 1) {
			visible = visible.substring(1);
		}
		graphics.drawString(font, visible, x + 18, y + 28, Ui.TEXT, false);
		if ((System.currentTimeMillis() / 500) % 2 == 0) {
			int caret = x + 18 + Ui.width(font, visible);
			graphics.fill(caret + 1, y + 25, caret + 2, y + 37, Ui.ACCENT);
		}
		graphics.drawString(font, "Enter saves  ·  Esc cancels", x + 12, y + 47, Ui.MUTED, false);
	}

	private void row(GuiGraphics graphics, int x, int y, int w, String label, String value, int valueColor,
			double mouseX, double mouseY) {
		boolean hot = over(mouseX, mouseY, x, y, w, 18);
		Ui.round(graphics, x, y, w, 18, 5, hot ? 0xFF16203A : 0xF00F1727);
		graphics.drawString(font, label, x + 8, y + 5, Ui.TEXT_2, false);
		Ui.textRight(graphics, font, Ui.fit(font, value, w - 90), x + w - 8, y + 5, valueColor);
	}

	private void pick(GuiGraphics graphics, int x, int y, int w, String label, boolean active,
			double mouseX, double mouseY) {
		boolean hot = over(mouseX, mouseY, x, y, w, 16);
		Ui.round(graphics, x, y, w, 16, 5, active ? Ui.alpha(Ui.ACCENT, 0.18F) : hot ? 0x14FFFFFF : 0x0AFFFFFF);
		int tw = Ui.width(font, label);
		if (tw > w - 6) {
			label = Ui.fit(font, label, w - 6);
			tw = Ui.width(font, label);
		}
		graphics.drawString(font, label, x + (w - tw) / 2, y + 4, active ? Ui.ACCENT : Ui.MUTED, false);
	}

	// ------------------------------------------------------------ interaction

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();

		if (over(mx, my, winX + 16, winY + 18, 18, 18)) {
			click();
			onClose();
			return true;
		}
		if (over(mx, my, winX + winW - 34, winY + 18, 18, 18)) {
			click();
			onClose();
			return true;
		}
		if (!module.isAction() && !module.keyOnly() && over(mx, my, winX + winW - 78, winY + 19, 22, 12)) {
			module.toggle();
			click();
			save();
			return true;
		}
		if (editing != null) {
			return true;
		}
		if (over(mx, my, winX + winW - 16 - Ui.width(font, "Done") - 18, footerY - 5, 60, 18)) {
			click();
			onClose();
			return true;
		}

		if (styled() && styleX <= mx && mx < styleX + styleW && styleY <= my && my < styleY + 200) {
			return clickStyle(mx, my);
		}
		return clickSetting(mx, my) || super.mouseClicked(event, doubleClick);
	}

	private boolean clickStyle(double mx, double my) {
		TextStyle style = module.style();
		TextStyle target = style.useGlobal() ? ModuleManager.get().globalStyle() : style;
		int y = styleY + 16;
		if (over(mx, my, styleX, y, styleW, 18)) {
			target.setMode(target.mode().next());
			save();
			return true;
		}
		if (over(mx, my, styleX, y + 20, styleW, 18)) {
			style.setUseGlobal(!style.useGlobal());
			save();
			return true;
		}

		int tabY = y + 44;
		int tabW = (styleW - 6) / 2;
		if (over(mx, my, styleX, tabY, tabW, 16)) {
			color2 = false;
			return true;
		}
		if (over(mx, my, styleX + tabW + 6, tabY, tabW, 16)) {
			color2 = true;
			return true;
		}

		int paletteY = tabY + 22;
		int cell = Math.max(14, (styleW - 7 * 3) / 8);
		for (int i = 0; i < 16; i++) {
			int px = styleX + (i % 8) * (cell + 3);
			int py = paletteY + (i / 8) * (cell + 3);
			if (over(mx, my, px, py, cell, cell - 2)) {
				Character code = CODES.charAt(i);
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

		int hexY = paletteY + 2 * (cell + 3) + 6;
		if (over(mx, my, styleX, hexY, styleW, 18)) {
			editing = Setting.text("HEX COLOUR", () -> target.mode() == TextStyle.Mode.BASIC ? target.basic()
					: TextStyle.hex(color2 ? target.secondary() : target.primary()), value -> {
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
		if (hasPrefix() && over(mx, my, styleX, hexY + 20, styleW, 18)) {
			editing = Setting.text("PREFIX", () -> style.prefix(), value -> {
				style.setPrefix(value);
				save();
			});
			editBuffer = editing.value();
			return true;
		}
		return false;
	}

	private boolean clickSetting(double mx, double my) {
		int top = bodyY + 8;
		if (my < top || my > top + bodyH - 8 || mx < listX || mx > listX + listW) {
			return false;
		}
		int index = (int) ((my - top) / (ROW_H + ROW_GAP)) + scroll;
		List<Setting> settings = rows;
		if (index < settings.size()) {
			Setting setting = settings.get(index);
			if (setting.type() == Setting.Type.TEXT) {
				editing = setting;
				editBuffer = setting.value();
			} else if (setting.type() == Setting.Type.SLIDER) {
				slider = setting;
				setSlider(setting, mx);
			} else if (setting.type() == Setting.Type.SEGMENT) {
				int sx = listX + listW - 10;
				int sy = bodyY + 8 + (index - scroll) * (ROW_H + ROW_GAP);
				for (int i = setting.options().size() - 1; i >= 0; i--) {
					int w = segmentWidth(setting, i);
					sx -= w;
					if (over(mx, my, sx, sy + 6, w, 16)) {
						setting.pick(i);
						save();
						return true;
					}
					sx -= SEGMENT_GAP;
				}
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
		if (module instanceof AutoTextModule) {
			int base = settings.size() + (module.keybindable() ? 1 : 0);
			int ai = index - base;
			int sy = top + (index - scroll) * (ROW_H + ROW_GAP);
			if (ai == 0 && atEntries.size() < AutoTextModule.MAX_ENTRIES) {
				atEntries.add(0, new AutoTextModule.Entry());
				atEditing = 0;
				atBuffer = "";
				atBinding = -1;
				return true;
			}
			int ei = ai - 1;
			if (ei >= 0 && ei < atEntries.size()) {
				AutoTextModule.Entry entry = atEntries.get(ei);
				if (over(mx, my, listX + listW - 30, sy + 5, 18, 18)) {
					atEntries.remove(ei);
					if (atBinding == ei) {
						atBinding = -1;
					}
					if (atEditing == ei) {
						atEditing = -1;
					}
					applyAt();
					save();
					return true;
				}
				String key = keyNameAt(entry);
				int w = Ui.width(font, key) + 20;
				if (over(mx, my, listX + listW - 40 - w, sy + 6, w, 17)) {
					atBinding = atBinding == ei ? -1 : ei;
					atEditing = -1;
					return true;
				}
				atEditing = ei;
				atBuffer = entry.text;
				atBinding = -1;
				return true;
			}
		}
		return false;
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

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= listX && mouseY >= bodyY && mouseY < bodyY + bodyH) {
			scroll = (int) Math.max(0, scroll - Math.signum(scrollY));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (atEditing >= 0) {
			if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
				atEditing = -1;
				return true;
			}
			if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
				atEntries.get(atEditing).text = atBuffer;
				atEditing = -1;
				applyAt();
				save();
				return true;
			}
			if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
				if (!atBuffer.isEmpty()) {
					atBuffer = atBuffer.substring(0, atBuffer.length() - 1);
				}
				return true;
			}
			return false;
		}
		if (atBinding >= 0) {
			if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
				AutoTextModule.Entry entry = atEntries.get(atBinding);
				entry.key = -1;
				entry.mods = 0;
				atBinding = -1;
				applyAt();
				save();
				return true;
			}
			if (isModifierKey(event.key())) {
				return true;
			}
			AutoTextModule.Entry entry = atEntries.get(atBinding);
			entry.key = event.key();
			entry.mods = currentMods();
			atBinding = -1;
			applyAt();
			save();
			return true;
		}
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
			Keybinds.apply(module);
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
		if (atEditing >= 0) {
			if (event.codepoint() >= 32 && event.codepoint() != 127 && atBuffer.length() < 64) {
				atBuffer += event.codepointAsString();
			}
			return true;
		}
		if (editing != null) {
			if (event.codepoint() >= 32 && event.codepoint() != 127 && editBuffer.length() < 64) {
				editBuffer += event.codepointAsString();
			}
			return true;
		}
		return super.charTyped(event);
	}

	// --------------------------------------------------------------- helpers

	private List<Setting> buildSettings() {
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

	private int segmentWidth(Setting setting, int index) {
		List<String> options = setting.options();
		if (options == null || options.isEmpty()) {
			return Ui.width(font, setting.value()) + 18;
		}
		String s = options.get(index);
		return Math.min(Ui.width(font, s) + 26, (int) (listW * 0.48));
	}

	private void setSlider(Setting setting, double mouseX) {
		int barW = Math.min(120, Math.max(60, listW / 3));
		int barX = listX + listW - 10 - barW;
		double t = Math.max(0.0, Math.min(1.0, (mouseX - barX) / barW));
		double value = setting.min() + t * (setting.max() - setting.min());
		if (setting.step() > 0) {
			value = Math.round(value / setting.step()) * setting.step();
		}
		setting.setNumber(value);
	}

	private String keyName(int key) {
		String cached = keyNames.get(key);
		if (cached != null) {
			return cached;
		}
		String name;
		if (key < 0) {
			name = "NONE";
		} else {
			name = InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString()
					.toUpperCase(Locale.ROOT);
		}
		name = Ui.fit(font, name, 100);
		keyNames.put(key, name);
		return name;
	}

	private String keyNameAt(AutoTextModule.Entry entry) {
		// key * 8 + mods is unique: mods is a 3-bit mask.
		int cacheKey = entry.key < 0 ? -1 : entry.key * 8 + entry.mods;
		String cached = atKeyNames.get(cacheKey);
		if (cached != null) {
			return cached;
		}
		String name;
		if (entry.key < 0) {
			name = "NONE";
		} else {
			StringBuilder sb = new StringBuilder();
			if ((entry.mods & AutoTextModule.MOD_CTRL) != 0) {
				sb.append("CTRL+");
			}
			if ((entry.mods & AutoTextModule.MOD_SHIFT) != 0) {
				sb.append("SHIFT+");
			}
			if ((entry.mods & AutoTextModule.MOD_ALT) != 0) {
				sb.append("ALT+");
			}
			sb.append(InputConstants.Type.KEYSYM.getOrCreate(entry.key).getDisplayName().getString()
					.toUpperCase(Locale.ROOT));
			name = sb.toString();
		}
		name = Ui.fit(font, name, 100);
		atKeyNames.put(cacheKey, name);
		return name;
	}

	private static boolean isModifierKey(int key) {
		return key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL
				|| key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT
				|| key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT;
	}

	private int currentMods() {
		com.mojang.blaze3d.platform.Window window = minecraft.getWindow();
		int mods = 0;
		if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
				|| InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL)) {
			mods |= AutoTextModule.MOD_CTRL;
		}
		if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
				|| InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT)) {
			mods |= AutoTextModule.MOD_SHIFT;
		}
		if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT)
				|| InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT)) {
			mods |= AutoTextModule.MOD_ALT;
		}
		return mods;
	}

	private void applyAt() {
		((AutoTextModule) module).setEntries(atEntries);
	}

	private int accent() {
		return module.category() == com.valkaw.client.module.Category.HUD ? Ui.ACCENT
				: module.category() == com.valkaw.client.module.Category.PERFORMANCE ? Ui.GREEN : Ui.COPPER;
	}

	private boolean hasPrefix() {
		return !module.id().equals("crosshair") && !module.id().equals("block-outline");
	}

	private boolean styled() {
		return module.category() == com.valkaw.client.module.Category.HUD
				|| module.id().equals("block-outline");
	}

	private void save() {
		ModuleManager.get().save();
	}

	private void click() {
		AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
	}

	private static boolean over(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	@Override
	public void onClose() {
		if (module instanceof AutoTextModule) {
			applyAt();
		}
		ModuleManager.get().save();
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
