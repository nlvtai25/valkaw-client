package com.valkaw.client.module.performance;

import com.google.gson.JsonObject;
import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;
import com.valkaw.client.module.ModuleManager;
import com.valkaw.client.module.Setting;
import java.util.List;

/** Remove water/lava fog while keeping terrain fog. */
public class ClearFluidsModule extends Module {
	private boolean water = true;
	private boolean lava = true;

	public ClearFluidsModule() {
		super("clear-water-lava", "Clear Water / Lava", Category.PERFORMANCE);
	}

	@Override
	public boolean keybindable() {
		return false;
	}

	public static boolean waterOn() {
		Module module = ModuleManager.get().byId("clear-water-lava");
		return module != null && module.isEnabled() && ((ClearFluidsModule) module).water;
	}

	public static boolean lavaOn() {
		Module module = ModuleManager.get().byId("clear-water-lava");
		return module != null && module.isEnabled() && ((ClearFluidsModule) module).lava;
	}

	@Override
	public List<Setting> settings() {
		return List.of(
				Setting.choice("WATER", () -> water ? "ON" : "OFF", () -> water = !water),
				Setting.choice("LAVA", () -> lava ? "ON" : "OFF", () -> lava = !lava));
	}

	@Override
	public void writeExtra(JsonObject json) {
		super.writeExtra(json);
		json.addProperty("water", water);
		json.addProperty("lava", lava);
	}

	@Override
	public void readExtra(JsonObject json) {
		super.readExtra(json);
		if (json.has("water")) {
			water = json.get("water").getAsBoolean();
		}
		if (json.has("lava")) {
			lava = json.get("lava").getAsBoolean();
		}
	}
}
