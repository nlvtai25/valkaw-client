package com.valkaw.client.mixin;

import com.valkaw.client.module.ModuleManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {
	@Inject(method = "getViewBlockingState", at = @At("RETURN"), cancellable = true)
	private static void valkaw$noPumpkin(Player player, CallbackInfoReturnable<BlockState> cir) {
		BlockState state = cir.getReturnValue();
		if (state != null && ModuleManager.isOn("no-pumpkin")
				&& (state.is(Blocks.CARVED_PUMPKIN) || state.is(Blocks.JACK_O_LANTERN))) {
			cir.setReturnValue(null);
		}
	}

	@Redirect(method = "renderScreenEffect", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isOnFire()Z"))
	private boolean valkaw$noFire(LocalPlayer player) {
		return player.isOnFire() && !ModuleManager.isOn("no-fire");
	}
}
