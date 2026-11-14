package com.valkaw.client.module.hud;

import com.valkaw.client.hud.Hud;
import com.valkaw.client.module.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class FpsModule extends HudModule {
	private int cachedFps = -1;
	private String cachedValue = "";

	public FpsModule() {
		super("fps", "FPS", 3, 3);
		setEnabled(true);
	}

	@Override
	public void render(GuiGraphics graphics, Font font) {
		int fps = Minecraft.getInstance().getFps();
		if (fps != cachedFps) {
			cachedFps = fps;
			cachedValue = Integer.toString(fps);
		}
		w = Hud.chip(graphics, font, x, y, "FPS", cachedValue);
		h = 11;
	}
}
