package com.valkaw.client.mixin;

import com.valkaw.client.module.performance.MenuBackdropModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the dim behind in-world menus with the opacity the player picked. */
@Mixin(Screen.class)
public class ScreenBackdropMixin {
	@Inject(method = "renderTransparentBackground", at = @At("HEAD"), cancellable = true)
	private void valkaw$menuBackdrop(GuiGraphics graphics, CallbackInfo ci) {
		int percent = MenuBackdropModule.backdrop();
		if (percent < 0) {
			return;
		}
		int alpha = Math.round(255 * (percent / 100.0F));
		if (alpha <= 0) {
			// Zero means no dim at all, so the screen draws straight onto the world.
			ci.cancel();
			return;
		}
		Screen screen = (Screen) (Object) this;
		int top = (alpha << 24) | 0x101010;
		int bottom = (Math.min(255, alpha + 12) << 24) | 0x101010;
		graphics.fillGradient(0, 0, screen.width, screen.height, top, bottom);
		ci.cancel();
	}
}
