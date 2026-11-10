package com.valkaw.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.valkaw.client.module.ModuleManager;
import com.valkaw.client.module.utility.ZoomModule;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void valkaw$zoomFov(Camera camera, float partialTick, boolean useFovSetting,
			CallbackInfoReturnable<Float> cir) {
		float zoom = ZoomModule.fovOverride();
		if (zoom > 0.0F) {
			cir.setReturnValue(zoom);
		}
	}

	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void valkaw$noHurtCam(PoseStack poseStack, float partialTick, CallbackInfo ci) {
		if (ModuleManager.isOn("no-hurt-cam")) {
			ci.cancel();
		}
	}

	@Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
	private void valkaw$noBobbing(PoseStack poseStack, float partialTick, CallbackInfo ci) {
		// Skipping the pose here works whether or not the player's own bobbing
		// option is on, and never rewrites options.txt.
		if (ModuleManager.isOn("no-bobbing")) {
			ci.cancel();
		}
	}
}
