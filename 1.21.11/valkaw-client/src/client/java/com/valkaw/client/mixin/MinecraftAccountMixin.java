package com.valkaw.client.mixin;

import com.mojang.authlib.GameProfile;
import com.valkaw.client.Account;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replaces the launcher-supplied name with the one saved in the client config. */
@Mixin(Minecraft.class)
public class MinecraftAccountMixin
{
	@Inject(method = "getUser", at = @At("HEAD"), cancellable = true)
	private void valkaw$account(CallbackInfoReturnable<User> cir)
	{
		User user = Account.user();
		if (user != null)
		{
			cir.setReturnValue(user);
		}
	}

	/**
	 * The {@code user} field still holds the launcher profile, so the default
	 * implementation would hand out that name. The integrated server stores this
	 * as its singleplayer owner and compares it on logout; a mismatch leaves the
	 * server running forever while the client waits for it to shut down.
	 */
	@Inject(method = "getGameProfile", at = @At("HEAD"), cancellable = true)
	private void valkaw$accountProfile(CallbackInfoReturnable<GameProfile> cir)
	{
		User user = Account.user();
		if (user != null)
		{
			cir.setReturnValue(new GameProfile(user.getProfileId(), user.getName()));
		}
	}
}
