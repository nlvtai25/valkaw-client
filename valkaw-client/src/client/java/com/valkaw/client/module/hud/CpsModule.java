package com.valkaw.client.module.hud;

import com.valkaw.client.hud.Hud;
import com.valkaw.client.hud.InputState;
import com.valkaw.client.module.HudModule;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class CpsModule extends HudModule {
	private int cachedLeft = -1;
	private int cachedRight = -1;
	private String cachedValue = "";

	public CpsModule() {
		super("cps", "CPS", 3, 17);
	}

	@Override
	public void render(GuiGraphics graphics, Font font) {
		int left = InputState.leftCps();
		int right = InputState.rightCps();
		if (left != cachedLeft || right != cachedRight) {
			cachedLeft = left;
			cachedRight = right;
			cachedValue = left + "/" + right;
		}
		w = Hud.chip(graphics, font, x, y, "CPS", cachedValue);
		h = 11;
	}
}
