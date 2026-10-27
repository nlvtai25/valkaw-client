package com.valkaw.client.module.hud;

import com.valkaw.client.hud.Hud;
import com.valkaw.client.module.HudModule;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class ComboModule extends HudModule {
	private static final long RESET_MS = 2000L;
	private static int combo;
	private static long lastHit;

	public ComboModule() {
		super("combo", "Combo", 60, 4);
	}

	/** Called from the attack hook. */
	public static void hit() {
		combo++;
		lastHit = System.currentTimeMillis();
	}

	@Override
	public void onEnable() {
		combo = 0;
	}

	@Override
	public void onTick() {
		if (combo > 0 && System.currentTimeMillis() - lastHit > RESET_MS) {
			combo = 0;
		}
	}

	@Override
	public void render(GuiGraphics graphics, Font font) {
		if (combo <= 0) {
			w = 0;
			h = 0;
			return;
		}
		w = Hud.chip(graphics, font, x, y, "COMBO", Integer.toString(combo));
		h = 11;
	}
}
