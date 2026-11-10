package com.valkaw.client.mixin;

import com.valkaw.client.ValkawClient;
import com.valkaw.client.gui.ValkawMainMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftScreenMixin {
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void valkaw$replaceTitleScreen(Screen screen, CallbackInfo ci) {
		if (screen instanceof TitleScreen) {
			((Minecraft) (Object) this).setScreen(new ValkawMainMenu());
			ci.cancel();
		}
	}

	@Inject(method = "updateTitle", at = @At("HEAD"), cancellable = true)
	private void valkaw$title(CallbackInfo ci) {
		Minecraft mc = (Minecraft) (Object) this;
		mc.getWindow().setTitle(ValkawClient.windowTitle());
		ci.cancel();
	}
}
