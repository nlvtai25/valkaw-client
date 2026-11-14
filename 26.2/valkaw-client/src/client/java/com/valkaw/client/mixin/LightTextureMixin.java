package com.valkaw.client.mixin;

import com.valkaw.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fullbright: forces the gamma option to read as max while the module is on.
 * 26.2 moved lightmap building out of {@code LightTexture.updateLightTexture} into
 * {@code LightmapRenderStateExtractor.extract}, which is where gamma is now consumed.
 */
@Mixin(LightmapRenderStateExtractor.class)
public class LightTextureMixin {
	@Redirect(method = "extract", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
	private Object valkaw$fullbright(OptionInstance<?> instance) {
		Object value = instance.get();
		Minecraft mc = Minecraft.getInstance();
		if (mc.options != null && instance == mc.options.gamma() && ModuleManager.isOn("fullbright")) {
			return 10.0D;
		}
		return value;
	}
}
