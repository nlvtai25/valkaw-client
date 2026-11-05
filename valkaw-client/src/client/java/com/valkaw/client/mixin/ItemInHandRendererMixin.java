package com.valkaw.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.valkaw.client.module.utility.FreelookModule;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freelook pulls the camera out of the head, so the first-person hands would float
 * in front of it. Vanilla third person does not draw them either.
 */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin
{
	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void valkaw$freelookHands(float partialTick, PoseStack poseStack, SubmitNodeCollector collector,
			LocalPlayer player, int light, CallbackInfo ci)
	{
		if (FreelookModule.active())
		{
			ci.cancel();
		}
	}
}
