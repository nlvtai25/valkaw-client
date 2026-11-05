package com.valkaw.client.module.hud;

import com.valkaw.client.hud.Hud;
import com.valkaw.client.module.HudModule;
import com.valkaw.client.module.ModuleManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Shows the Toggle Sprint state as an ON/OFF chip, always visible while enabled. */
public class SprintHudModule extends HudModule {
	public SprintHudModule() {
		super("sprint-hud", "Sprint HUD", 4, 199);
		setEnabled(true);
	}

	@Override
	public boolean editorWhenEmpty() {
		return false;
	}

	@Override
	public void render(GuiGraphics graphics, Font font) {
		boolean on = ModuleManager.isOn("toggle-sprint");
		w = Hud.chip(graphics, font, x, y, "Sprint", on ? "ON" : "OFF");
		h = 11;
	}
}
