package com.valkaw.client.module.utility;

import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/** Hold-to-look: moves only the camera, leaves the player rotation untouched. */
public class FreelookModule extends Module {
	private static float yaw;
	private static float pitch;

	public FreelookModule() {
		super("freelook", "Freelook", Category.UTILITY);
		setKey(GLFW.GLFW_KEY_LEFT_ALT);
	}

	public static boolean active() {
		Module module = com.valkaw.client.module.ModuleManager.get().byId("freelook");
		return module instanceof FreelookModule freelook && freelook.holding();
	}

	public static float yaw() {
		return yaw;
	}

	public static float pitch() {
		return pitch;
	}

	/** Mouse deltas in pixels, converted the same way vanilla turns the player. */
	public static void rotate(double dx, double dy) {
		Minecraft mc = Minecraft.getInstance();
		double scale = mc.options.sensitivity().get() * 0.6 + 0.2;
		double factor = scale * scale * scale * 8.0 * 0.15;
		float yawDelta = (float) ((mc.options.invertMouseX().get() ? -dx : dx) * factor);
		float pitchDelta = (float) ((mc.options.invertMouseY().get() ? -dy : dy) * factor);
		yaw = Mth.wrapDegrees(yaw + yawDelta);
		pitch = Mth.clamp(pitch + pitchDelta, -90.0F, 90.0F);
	}

	private boolean holding() {
		Minecraft mc = Minecraft.getInstance();
		return isEnabled() && key() >= 0 && mc.screen == null
				&& InputConstants.isKeyDown(mc.getWindow(), key());
	}

	@Override
	public void onEnable() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			yaw = player.getYRot();
			pitch = player.getXRot();
		}
	}

	@Override
	public void onTick() {
		if (isEnabled() && !holding()) {
			setEnabled(false);
			com.valkaw.client.module.ModuleManager.get().save();
		}
	}
}
