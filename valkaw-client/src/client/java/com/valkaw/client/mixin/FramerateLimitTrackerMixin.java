package com.valkaw.client.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FramerateLimitTracker.class)
public class FramerateLimitTrackerMixin {
	@Shadow
	@Final
	private Options options;

	@Shadow
	@Final
	private Minecraft minecraft;

	/** Vanilla pins menus outside a world to 60 fps; honour the configured limit instead. */
	@Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
	private void valkaw$menuFramerate(CallbackInfoReturnable<Integer> cir)
	{
		if (minecraft.level != null)
		{
			return;
		}
		int configured = options.framerateLimit().get();
		if (configured > cir.getReturnValue())
		{
			cir.setReturnValue(configured);
		}
	}
}
