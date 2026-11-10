package com.valkaw.client.mixin;

import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FogRenderer.class)
public interface FogRendererAccessor {
	@Accessor("fogEnabled")
	static void setFogEnabled(boolean enabled) {
		throw new AssertionError();
	}
}
