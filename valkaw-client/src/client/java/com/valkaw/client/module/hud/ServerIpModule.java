package com.valkaw.client.module.hud;

import com.valkaw.client.hud.Hud;
import com.valkaw.client.module.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ServerData;

public class ServerIpModule extends HudModule {
	public ServerIpModule() {
		super("server-ip", "Server IP", 4, 186);
		setEnabled(true);
	}

	@Override
	public void render(GuiGraphics graphics, Font font) {
		Minecraft mc = Minecraft.getInstance();
		String value = "--";
		ServerData server = mc.getCurrentServer();
		if (server != null && server.ip != null && !server.ip.isBlank()) {
			value = server.ip;
		} else if (mc.hasSingleplayerServer()) {
			value = "SINGLEPLAYER";
		}
		w = Hud.chip(graphics, font, x, y, "SERVER", value);
		h = 11;
	}
}
