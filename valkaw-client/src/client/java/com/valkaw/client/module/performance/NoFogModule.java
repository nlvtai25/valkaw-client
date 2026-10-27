package com.valkaw.client.module.performance;

import com.valkaw.client.mixin.FogRendererAccessor;
import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;

/** Disables the vanilla fog pipeline entirely; no per-frame work. */
public class NoFogModule extends Module {
	public NoFogModule() {
		super("no-fog", "No Fog", Category.PERFORMANCE);
	}

	@Override
	public boolean keybindable() {
		return false;
	}

	@Override
	public void onEnable() {
		FogRendererAccessor.setFogEnabled(false);
	}

	@Override
	public void onDisable() {
		FogRendererAccessor.setFogEnabled(true);
	}
}
