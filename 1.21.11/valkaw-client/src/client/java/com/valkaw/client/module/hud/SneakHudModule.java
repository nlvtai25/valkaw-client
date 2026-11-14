package com.valkaw.client.module.hud;

import com.valkaw.client.hud.Hud;
import com.valkaw.client.module.HudModule;
import com.valkaw.client.module.ModuleManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Shows the Toggle Sneak state as an ON/OFF chip, always visible while enabled. */
public class SneakHudModule extends HudModule {
	public SneakHudModule() {
		super("sneak-hud", "Sneak HUD", 3, 212);
		setEnabled(true);
	}

	@Override
	public boolean editorWhenEmpty() {
		return false;
	}

	@Override
	public void render(GuiGraphics graphics, Font font) {
		boolean on = ModuleManager.isOn("toggle-sneak");
		w = Hud.chip(graphics, font, x, y, "Sneak", on ? "ON" : "OFF");
		h = 11;
	}
}
