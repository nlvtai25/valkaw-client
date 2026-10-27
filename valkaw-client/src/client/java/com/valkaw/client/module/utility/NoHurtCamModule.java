package com.valkaw.client.module.utility;

import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;

public class NoHurtCamModule extends Module {
	public NoHurtCamModule() {
		super("no-hurt-cam", "No Hurt Cam", Category.UTILITY);
	}

	@Override
	public boolean keybindable() {
		return false;
	}
}
