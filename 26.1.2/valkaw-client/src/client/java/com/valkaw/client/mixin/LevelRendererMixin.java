package com.valkaw.client.mixin;

import com.valkaw.client.module.Module;
import com.valkaw.client.module.ModuleManager;
import com.valkaw.client.module.utility.BlockOutlineModule;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
	// 26.1.2 passes alpha as the trailing float of renderHitOutline, so only the colour is replaced here.
	@ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int valkaw$outlineColor(int color) {
		Module module = ModuleManager.get().byId("block-outline");
		return module instanceof BlockOutlineModule outline && outline.isEnabled() ? outline.color() : color;
	}
}
