package com.valkaw.client.mixin;

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
}
