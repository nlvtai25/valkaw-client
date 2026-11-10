package com.valkaw.client.module.utility;

import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;
import org.lwjgl.glfw.GLFW;

/** Gamma override is applied by LightTextureMixin without touching the saved options. */
public class FullbrightModule extends Module {
	public FullbrightModule() {
		super("fullbright", "Fullbright", Category.UTILITY);
		setKey(GLFW.GLFW_KEY_G);
	}
}
