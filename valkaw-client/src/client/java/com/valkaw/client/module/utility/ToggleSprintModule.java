package com.valkaw.client.module.utility;

import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public class ToggleSprintModule extends Module {
	public ToggleSprintModule() {
		super("toggle-sprint", "Toggle Sprint", Category.UTILITY);
	}

	@Override
	public void onTick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		if (player.input.hasForwardImpulse() && !player.isShiftKeyDown() && player.getFoodData().getFoodLevel() > 6) {
			player.setSprinting(true);
		}
	}
}
