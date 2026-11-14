package com.valkaw.client.module.utility;

import com.google.gson.JsonObject;
import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;
import com.valkaw.client.keybinds.Keybinds;
import com.valkaw.client.module.ModuleManager;
import com.valkaw.client.module.Setting;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Freelook unlocks the third-person camera: the view turns on its own while the
 * player body keeps facing where it was. {@code CameraMixin} puts the camera
 * behind (or in front of) the player using the freelook rotation. To make the
 * player model render, the camera type is forced to third person for as long as
 * the module is active and restored afterwards.
 */
public class FreelookModule extends Module {
	private static float yaw;
	private static float pitch;
	private boolean front;
	private boolean toggleMode;
	private CameraType savedCameraType;

	public FreelookModule() {
		super("freelook", "Freelook", Category.UTILITY);
		setKey(GLFW.GLFW_KEY_LEFT_ALT);
	}

	@Override
	public boolean keyOnly() {
		return true;
	}

	private static FreelookModule self() {
		Module module = ModuleManager.get().byId("freelook");
		return module instanceof FreelookModule freelook ? freelook : null;
	}

	public static boolean active() {
		FreelookModule freelook = self();
		return freelook != null && freelook.isEnabled() && (freelook.toggleMode || freelook.holding());
	}

	/** True for the mirrored third-person view (in front of the player, looking back). */
	public static boolean front() {
		FreelookModule freelook = self();
		return freelook != null && freelook.front;
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
		return mc.screen == null && Keybinds.down(this);
	}

	@Override
	public List<Setting> settings() {
		return List.of(
				Setting.segment("VIEW", List.of("BACK", "FRONT"),
						() -> front ? 1 : 0, i -> {
							front = i == 1;
							if (isEnabled()) {
								applyCameraType();
							}
						}),
				Setting.choice("MODE", () -> toggleMode ? "TOGGLE" : "PRESSING", () -> toggleMode = !toggleMode));
	}

	/** Force third person so the player body renders while freelook is active. */
	private void applyCameraType() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options != null) {
			mc.options.setCameraType(front ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
		}
	}

	@Override
	public void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player != null) {
			yaw = player.getYRot();
			pitch = player.getXRot();
		}
		savedCameraType = mc.options == null ? null : mc.options.getCameraType();
		applyCameraType();
	}

	@Override
	public void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (savedCameraType != null && mc.options != null) {
			mc.options.setCameraType(savedCameraType);
		}
		savedCameraType = null;
	}

	@Override
	public void onTick() {
		if (!toggleMode && isEnabled() && !holding()) {
			setEnabled(false);
			ModuleManager.get().save();
		}
	}

	@Override
	public void writeExtra(JsonObject json) {
		json.addProperty("front", front);
		json.addProperty("toggleMode", toggleMode);
	}

	@Override
	public void readExtra(JsonObject json) {
		if (json.has("front")) {
			front = json.get("front").getAsBoolean();
		}
		if (json.has("toggleMode")) {
			toggleMode = json.get("toggleMode").getAsBoolean();
		}
	}
}
