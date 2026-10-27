package com.valkaw.client.module.hud;

import com.valkaw.client.hud.Hud;
import com.valkaw.client.hud.InputState;
import com.valkaw.client.module.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class KeystrokesModule extends HudModule {
	public KeystrokesModule() {
		super("keystrokes", "Keystrokes", 4, 106);
		setEnabled(true);
	}

	@Override
	protected int previewWidth() {
		return 46;
	}

	@Override
	protected int previewHeight() {
		return 60;
	}

	@Override
	public void render(GuiGraphics graphics, Font font) {
		Options options = Minecraft.getInstance().options;
		int prefixW = Hud.prefix(graphics, font, x, y, "KEYS");
		w = Math.max(46, prefixW);
		h = 60;
		Hud.key(graphics, font, x + 16, y + 13, 14, "W", options.keyUp.isDown());
		Hud.key(graphics, font, x, y + 25, 14, "A", options.keyLeft.isDown());
		Hud.key(graphics, font, x + 16, y + 25, 14, "S", options.keyDown.isDown());
		Hud.key(graphics, font, x + 32, y + 25, 14, "D", options.keyRight.isDown());
		Hud.key(graphics, font, x, y + 37, 46, "SPACE", options.keyJump.isDown());
		Hud.key(graphics, font, x, y + 49, 22, "LMB", InputState.left());
		Hud.key(graphics, font, x + 24, y + 49, 22, "RMB", InputState.right());
	}
}
