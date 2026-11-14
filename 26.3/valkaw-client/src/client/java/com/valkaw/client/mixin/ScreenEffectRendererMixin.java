package com.valkaw.client.mixin;

import com.valkaw.client.module.ModuleManager;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.3 moved the pumpkin and fire overlays out of the renderer: they now live in the
 * player render state and the overlay pass only reads them. The toggles therefore clear
 * the fields on the state before that pass runs.
 */
@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin
{
	@Inject(method = "submit", at = @At("HEAD"))
	private void valkaw$overlayToggles(float partialTick, SubmitNodeCollector collector, PlayerRenderState player,
			CameraRenderState camera, boolean firstPerson, CallbackInfo ci)
	{
		if (player.blockOverlay != null && ModuleManager.isOn("no-pumpkin"))
		{
			player.blockOverlay = null;
		}
		if (player.isOnFire && ModuleManager.isOn("no-fire"))
		{
			player.isOnFire = false;
		}
	}
}
