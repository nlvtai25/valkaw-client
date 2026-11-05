package com.valkaw.client.module.hud;

import com.valkaw.client.hud.Hud;
import com.valkaw.client.module.HudModule;
import com.valkaw.client.module.ModuleManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class ComboModule extends HudModule {
	private static final long RESET_MS = 2000L;
	private static int combo;
	private static long lastHit;

	public ComboModule() {
		super("combo", "Combo", 60, 3);
	}

	/** Called from the attack hook. */
	public static void hit() {
		// Ignore hits while the HUD is off so a re-enabled module never shows a
		// combo left over from before it was turned on.
		if (!ModuleManager.isOn("combo")) {
			return;
		}
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
		w = Hud.chip(graphics, font, x, y, "Combo", Integer.toString(combo));
		h = 11;
	}
}
