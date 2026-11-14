package com.valkaw.client.mixin;

import com.valkaw.client.module.utility.FreelookModule;
import com.valkaw.client.module.utility.ZoomModule;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Shadow
	private double accumulatedDX;
	@Shadow
	private double accumulatedDY;

	@Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
	private void valkaw$zoomScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (ZoomModule.adjustScroll(yOffset)) {
			ci.cancel();
		}
	}

	@Inject(method = "turnPlayer(D)V", at = @At("HEAD"), cancellable = true)
	private void valkaw$freelook(double partialTick, CallbackInfo ci) {
		if (FreelookModule.active()) {
			FreelookModule.rotate(accumulatedDX, accumulatedDY);
			ci.cancel();
		}
	}
}
