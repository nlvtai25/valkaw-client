package com.valkaw.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.valkaw.client.module.utility.FreelookModule;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freelook pulls the camera out of the head, so the first-person hands would float
 * in front of it. Vanilla third person does not draw them either.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class FirstPersonHandsAndItemsRendererMixin
{
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void valkaw$freelookHands(float partialTick, PoseStack poseStack, SubmitNodeCollector collector,
			PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands, CallbackInfo ci)
	{
		if (FreelookModule.active())
		{
			ci.cancel();
		}
	}
}
