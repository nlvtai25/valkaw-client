package com.valkaw.client.module.utility;

import com.google.gson.JsonObject;
import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;
import com.valkaw.client.keybinds.Keybinds;
import com.valkaw.client.module.ModuleManager;
import com.valkaw.client.module.Setting;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Hold-to-zoom: overrides the FOV through {@code GameRendererMixin}, does not touch options.
 * The override is live only while the key is physically held, so a stale enabled flag
 * (world join, config load) can never leave the game zoomed in.
 */
public class ZoomModule extends Module {
	/** Saved zoom point, restored on the next hold. */
	private double zoomFov = 30.0;
	/** Scroll offset for the current hold only; 0 means "not scrolled yet". */
	private double scrollFov;

	public ZoomModule() {
		super("zoom", "Zoom", Category.UTILITY);
		setKey(GLFW.GLFW_KEY_C);
	}

	@Override
	public boolean keyOnly() {
		return true;
	}

	public static float fovOverride() {
		Module module = ModuleManager.get().byId("zoom");
		return module instanceof ZoomModule zoom && zoom.holding() ? (float) zoom.activeFov() : -1.0F;
	}

	/** The scrolled value only lives while the key is held, so the saved point always wins. */
	private double activeFov() {
		return scrollFov > 0.0 ? scrollFov : zoomFov;
	}

	private boolean holding() {
		Minecraft mc = Minecraft.getInstance();
		return isEnabled() && mc.screen == null && Keybinds.down(this);
	}

	@Override
	public void onTick() {
		if (!holding()) {
			// Release drops the scroll offset: the next zoom starts at the saved point.
			scrollFov = 0.0;
			setEnabled(false);
			ModuleManager.get().save();
		}
	}

	/** Scroll while zooming changes the FOV for this hold only; consumes the wheel only while active. */
	public static boolean adjustScroll(double delta) {
		Module module = ModuleManager.get().byId("zoom");
		if (!(module instanceof ZoomModule zoom) || !zoom.holding()) {
			return false;
		}
		double from = zoom.activeFov();
		zoom.scrollFov = Math.max(10.0, Math.min(90.0, from - delta * 2.0));
		return true;
	}

	public void cycleZoom() {
		zoomFov = zoomFov <= 20 ? 30 : zoomFov <= 30 ? 45 : 20;
	}

	@Override
	public List<Setting> settings() {
		return List.of(
				Setting.slider("FOV", 10, 90, 0.1, () -> zoomFov,
						value -> zoomFov = value, () -> String.format(java.util.Locale.ROOT, "%.1f FOV", zoomFov)),
				Setting.text("FOV VALUE", () -> String.format(java.util.Locale.ROOT, "%.1f", zoomFov), value -> {
					try {
						zoomFov = Math.max(10.0, Math.min(90.0, Double.parseDouble(value.trim())));
					} catch (NumberFormatException ignored) {
						// Keep the previous value.
					}
				}));
	}

	@Override
	public String option() {
		return String.format(java.util.Locale.ROOT, "%.1f FOV", zoomFov);
	}

	@Override
	public void cycleOption() {
		cycleZoom();
	}

	@Override
	public void writeExtra(JsonObject json) {
		json.addProperty("zoomFov", zoomFov);
	}

	@Override
	public void readExtra(JsonObject json) {
		if (json.has("zoomFov")) {
			zoomFov = Math.max(10.0, Math.min(90.0, json.get("zoomFov").getAsDouble()));
		}
	}
}
