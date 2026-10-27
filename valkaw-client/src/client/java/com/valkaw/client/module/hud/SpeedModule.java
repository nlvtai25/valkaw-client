package com.valkaw.client.module.hud;

import com.valkaw.client.hud.Hud;
import com.valkaw.client.module.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

public class SpeedModule extends HudModule {
	private double speed;
	private double lastX;
	private double lastZ;
	private boolean sampled;

	public SpeedModule() {
		super("speed", "Speed", 4, 56);
	}

	@Override
	public void onEnable() {
		sampled = false;
		speed = 0;
	}

	@Override
	public void onTick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			sampled = false;
			speed = 0;
			return;
		}
		if (!sampled) {
			lastX = player.getX();
			lastZ = player.getZ();
			sampled = true;
			return;
		}
		double delta = Math.hypot(player.getX() - lastX, player.getZ() - lastZ);
		lastX = player.getX();
		lastZ = player.getZ();
		speed = speed * 0.75 + delta * 20.0 * 0.25;
	}

	@Override
	public void render(GuiGraphics graphics, Font font) {
		int scaled = (int) Math.round(speed * 10.0);
		String value = (scaled / 10) + "." + Math.abs(scaled % 10) + " b/s";
		w = Hud.chip(graphics, font, x, y, "SPD", value);
		h = 11;
	}
}
