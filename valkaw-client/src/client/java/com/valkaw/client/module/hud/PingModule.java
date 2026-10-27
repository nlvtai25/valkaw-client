package com.valkaw.client.module.hud;

import com.valkaw.client.hud.Hud;
import com.valkaw.client.module.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;

public class PingModule extends HudModule {
	public PingModule() {
		super("ping", "Ping", 4, 30);
	}

	@Override
	public void render(GuiGraphics graphics, Font font) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		ClientPacketListener connection = mc.getConnection();
		String value = "0ms";
		if (player != null && connection != null) {
			PlayerInfo info = connection.getPlayerInfo(player.getUUID());
			if (info != null) {
				value = info.getLatency() + "ms";
			}
		}
		w = Hud.chip(graphics, font, x, y, "PING", value);
		h = 11;
	}
}
