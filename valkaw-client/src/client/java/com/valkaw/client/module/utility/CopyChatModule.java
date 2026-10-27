package com.valkaw.client.module.utility;

import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Copies the latest chat message; ChatComponentMixin keeps the plain text. */
public class CopyChatModule extends Module {
	private static String last = "";

	public CopyChatModule() {
		super("copy-chat", "Copy Chat", Category.UTILITY, true);
		setKey(GLFW.GLFW_KEY_F8);
	}

	public static void remember(Component message) {
		last = message.getString();
	}

	@Override
	public String status() {
		return "READY";
	}

	@Override
	public void onKey() {
		if (!last.isBlank()) {
			Minecraft.getInstance().keyboardHandler.setClipboard(last);
		}
	}
}
