package com.valkaw.client.mixin;

import com.valkaw.client.module.hud.ComboModule;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void valkaw$combo(Player player, Entity target, CallbackInfo ci) {
		ComboModule.hit();
	}
}
