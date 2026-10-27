package com.valkaw.client.module.utility;

import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;

public class NoFireModule extends Module {
	public NoFireModule() {
		super("no-fire", "No Fire Overlay", Category.UTILITY);
	}

	@Override
	public boolean keybindable() {
		return false;
	}
}
