package com.valkaw.client.module.utility;

import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;

/** Appends a durability percentage to damaged items' tooltips (see ScreenTooltipMixin). */
public class BetterTooltipsModule extends Module {
	public BetterTooltipsModule() {
		super("better-tooltips", "Better Tooltips", Category.UTILITY);
	}

	@Override
	public boolean keybindable() {
		return false;
	}
}
