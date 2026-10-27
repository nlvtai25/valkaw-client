package com.valkaw.client.module.utility;

import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;

public class NoPumpkinModule extends Module {
	public NoPumpkinModule() {
		super("no-pumpkin", "No Pumpkin Blur", Category.UTILITY);
	}

	@Override
	public boolean keybindable() {
		return false;
	}
}
