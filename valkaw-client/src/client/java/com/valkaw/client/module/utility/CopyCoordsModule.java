package com.valkaw.client.module.utility;

import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;
import com.valkaw.client.module.hud.CoordinatesModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class CopyCoordsModule extends Module {
	public CopyCoordsModule() {
		super("copy-coords", "Copy Coordinates", Category.UTILITY, true);
		setKey(GLFW.GLFW_KEY_F7);
	}

	@Override
	public String status() {
		return "READY";
	}

	/** Copies exactly the XYZ string the Coordinates HUD shows and confirms it on the action bar. */
	@Override
	public void onKey() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player != null) {
			mc.keyboardHandler.setClipboard(CoordinatesModule.xyz(player));
			mc.gui.setOverlayMessage(Component.literal("Coords Copied"), false);
		}
	}
}
