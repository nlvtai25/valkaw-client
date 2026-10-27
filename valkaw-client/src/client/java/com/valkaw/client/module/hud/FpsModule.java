package com.valkaw.client.module.hud;

import com.valkaw.client.hud.Hud;
import com.valkaw.client.module.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class FpsModule extends HudModule {
	public FpsModule() {
		super("fps", "FPS", 4, 4);
		setEnabled(true);
	}

	@Override
	public void render(GuiGraphics graphics, Font font) {
		w = Hud.chip(graphics, font, x, y, "FPS", Integer.toString(Minecraft.getInstance().getFps()));
		h = 11;
	}
}
