package com.valkaw.client.module.performance;

import com.google.gson.JsonObject;
import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;
import com.valkaw.client.module.ModuleManager;
import com.valkaw.client.module.Setting;
import java.util.List;

/** Controls how dark the backdrop behind in-world menus (chest, inventory, furnace) is. */
public class MenuBackdropModule extends Module {
	private int opacity = 75;

	public MenuBackdropModule() {
		super("menu-backdrop", "Menu Backdrop", Category.PERFORMANCE);
		setEnabled(true);
	}

	/** Backdrop strength for the mixin, or -1 when vanilla should keep its own. */
	public static int backdrop() {
		Module module = ModuleManager.get().byId("menu-backdrop");
		if (module instanceof MenuBackdropModule dim && module.isEnabled()) {
			return dim.opacity;
		}
		return -1;
	}

	@Override
	public String status() {
		return opacity + "%";
	}

	@Override
	public String option() {
		return status();
	}

	@Override
	public List<Setting> settings() {
		return List.of(Setting.slider("OPACITY", 0, 100, 1, () -> opacity,
				value -> opacity = (int) Math.round(value), () -> opacity + "%"));
	}

	@Override
	public void writeExtra(JsonObject json) {
		super.writeExtra(json);
		json.addProperty("opacity", opacity);
	}

	@Override
	public void readExtra(JsonObject json) {
		super.readExtra(json);
		if (json.has("opacity")) {
			opacity = Math.max(0, Math.min(100, json.get("opacity").getAsInt()));
		}
	}
}
