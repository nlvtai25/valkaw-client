package com.valkaw.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.valkaw.client.module.ModuleManager;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void valkaw$noHurtCam(CameraRenderState cameraRenderState, PoseStack poseStack, CallbackInfo ci) {
		if (ModuleManager.isOn("no-hurt-cam")) {
			ci.cancel();
		}
	}

	@Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
	private void valkaw$noBobbing(CameraRenderState cameraRenderState, PoseStack poseStack, CallbackInfo ci) {
		// Skipping the pose here works whether or not the player's own bobbing
		// option is on, and never rewrites options.txt.
		if (ModuleManager.isOn("no-bobbing")) {
			ci.cancel();
		}
	}
}
