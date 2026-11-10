package com.valkaw.client.module.utility;

import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;
import com.valkaw.client.module.hud.CoordinatesModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Copies the current coordinates to the clipboard on its shortcut (default F7) while enabled. */
public class CopyCoordsModule extends Module {
	public CopyCoordsModule() {
		super("copy-coords", "Copy Coordinates", Category.UTILITY);
		setKey(GLFW.GLFW_KEY_F7);
	}

	@Override
	public boolean triggerOnKeyPress() {
		return true;
	}

	@Override
	public void onKey() {
		if (!isEnabled()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player != null) {
			mc.keyboardHandler.setClipboard(CoordinatesModule.xyz(player));
			mc.gui.setOverlayMessage(Component.literal("Coords Copied"), false);
		}
	}
}
