package com.valkaw.client.mixin;

import com.valkaw.client.accessor.LivingEntityRenderStateAccessor;
import com.valkaw.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
	/**
	 * Flag the per-frame render state for other players when Hide Players is on.
	 * The flag is set before the frame is drawn, so isBodyVisible can consult it.
	 * Also clears the glow-outline colour so a hidden player never gets an outline.
	 */
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At("RETURN"))
	private void valkaw$markHidden(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		boolean hide = ModuleManager.isOn("hideplayers")
				&& entity instanceof Player
				&& entity != mc.player;
		((LivingEntityRenderStateAccessor) state).valkaw$setHideBody(hide);
		if (hide) {
			state.outlineColor = 0;
		}
	}

	/** Skip drawing the body model, but the nametag is still rendered by the caller. */
	@Inject(method = "isBodyVisible(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z",
			at = @At("HEAD"), cancellable = true)
	private void valkaw$forceBodyHidden(LivingEntityRenderState state, CallbackInfoReturnable<Boolean> cir) {
		if (((LivingEntityRenderStateAccessor) state).valkaw$hideBody()) {
			cir.setReturnValue(false);
		}
	}

	/** No model geometry at all for hidden players (no translucent ghost body). */
	@Inject(method = "getRenderType(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/renderer/rendertype/RenderType;",
			at = @At("HEAD"), cancellable = true)
	private void valkaw$hideModel(LivingEntityRenderState state, boolean bodyVisible, boolean invisible, boolean glowing, CallbackInfoReturnable<RenderType> cir) {
		if (((LivingEntityRenderStateAccessor) state).valkaw$hideBody()) {
			cir.setReturnValue(null);
		}
	}

	/** Skip all layers (cape, armour, held items) for hidden players. */
	@Inject(method = "shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z",
			at = @At("HEAD"), cancellable = true)
	private void valkaw$hideLayers(LivingEntityRenderState state, CallbackInfoReturnable<Boolean> cir) {
		if (((LivingEntityRenderStateAccessor) state).valkaw$hideBody()) {
			cir.setReturnValue(false);
		}
	}
}
