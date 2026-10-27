package com.valkaw.client.module.hud;

import com.valkaw.client.hud.Hud;
import com.valkaw.client.hud.InputState;
import com.valkaw.client.module.HudModule;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class CpsModule extends HudModule {
	public CpsModule() {
		super("cps", "CPS", 4, 17);
	}

	@Override
	public void render(GuiGraphics graphics, Font font) {
		String value = InputState.leftCps() + "/" + InputState.rightCps();
		w = Hud.chip(graphics, font, x, y, "CPS", value);
		h = 11;
	}
}
