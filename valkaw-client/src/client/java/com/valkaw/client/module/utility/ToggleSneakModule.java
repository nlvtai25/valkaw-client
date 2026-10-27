package com.valkaw.client.module.utility;

import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;
import net.minecraft.client.Minecraft;

public class ToggleSneakModule extends Module {
	public ToggleSneakModule() {
		super("toggle-sneak", "Toggle Sneak", Category.UTILITY);
	}

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		mc.options.keyShift.setDown(true);
	}

	@Override
	public void onDisable() {
		Minecraft.getInstance().options.keyShift.setDown(false);
	}
}
