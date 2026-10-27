package com.valkaw.client.module.utility;

import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/** Turns view bobbing off while enabled and restores the player's original setting afterwards. */
public class NoBobbingModule extends Module {
	private boolean wasBobbing;
	private boolean captured;

	public NoBobbingModule() {
		super("no-bobbing", "No Bobbing", Category.UTILITY);
	}

	@Override
	public void onEnable() {
		Options options = Minecraft.getInstance().options;
		if (options != null) {
			wasBobbing = options.bobView().get();
			captured = true;
			options.bobView().set(false);
		}
	}

	@Override
	public void onDisable() {
		if (captured) {
			Minecraft.getInstance().options.bobView().set(wasBobbing);
			captured = false;
		}
	}
}
