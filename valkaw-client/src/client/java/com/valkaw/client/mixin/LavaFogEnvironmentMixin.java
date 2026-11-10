package com.valkaw.client.mixin;

import com.valkaw.client.module.performance.ClearFluidsModule;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LavaFogEnvironment.class)
public class LavaFogEnvironmentMixin {
	@Inject(method = "setupFog", at = @At("TAIL"))
	private void valkaw$clearLava(FogData data, Camera camera, ClientLevel level, float partialTick,
			DeltaTracker deltaTracker, CallbackInfo ci) {
		if (ClearFluidsModule.lavaOn()) {
			data.environmentalStart = 1_000_000.0F;
			data.environmentalEnd = 1_000_000.0F;
			data.renderDistanceStart = 1_000_000.0F;
			data.renderDistanceEnd = 1_000_000.0F;
		}
	}
}
