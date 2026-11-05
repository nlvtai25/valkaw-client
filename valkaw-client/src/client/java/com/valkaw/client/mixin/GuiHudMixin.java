package com.valkaw.client.mixin;

import com.valkaw.client.hud.HudRenderer;
import com.valkaw.client.module.ModuleManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiHudMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void valkaw$renderHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		HudRenderer.render(graphics);
	}

	/**
	 * Since 1.21.9 the carved pumpkin blur is a camera overlay pulled from the
	 * equippable component, not a view-blocking block state.
	 */
	@Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
	private void valkaw$noPumpkin(GuiGraphics graphics, Identifier texture, float alpha, CallbackInfo ci) {
		if (ModuleManager.isOn("no-pumpkin") && texture.getPath().contains("pumpkinblur")) {
			ci.cancel();
		}
	}
}
