package com.valkaw.client.module.utility;

import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;

/**
 * Hides the bodies of all other players. Nametags are intentionally kept
 * visible. The actual hiding is applied by LivingEntityRendererMixin.
 */
public class HidePlayersModule extends Module {
	public HidePlayersModule() {
		super("hideplayers", "Hide Players", Category.UTILITY);
	}
}
