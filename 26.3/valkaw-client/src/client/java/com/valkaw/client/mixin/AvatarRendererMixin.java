package com.valkaw.client.mixin;

import com.valkaw.client.accessor.LivingEntityRenderStateAccessor;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AvatarRenderer (the player renderer) overrides shouldRenderLayers with its own
 * implementation, which bypasses the LivingEntityRenderer mixin. Hook it here so
 * hidden players render no layers at all (cape, armour, held items).
 */
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
	@Inject(method = "shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Z",
			at = @At("HEAD"), cancellable = true)
	private void valkaw$hideAvatarLayers(AvatarRenderState state, CallbackInfoReturnable<Boolean> cir) {
		if (((LivingEntityRenderStateAccessor) state).valkaw$hideBody()) {
			cir.setReturnValue(false);
		}
	}
}
