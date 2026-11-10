package com.valkaw.client.module.utility;

import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;

/**
 * Camera bobbing switch. The renderer skips the bob pose while this module is
 * on, so the player's own bobbing option is never touched.
 */
public class NoBobbingModule extends Module {
	public NoBobbingModule() {
		super("no-bobbing", "No Bobbing", Category.UTILITY);
	}
}
