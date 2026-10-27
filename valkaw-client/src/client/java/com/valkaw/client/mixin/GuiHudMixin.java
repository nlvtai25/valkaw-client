package com.valkaw.client.mixin;

import com.valkaw.client.hud.HudRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiHudMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void valkaw$renderHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		HudRenderer.render(graphics, deltaTracker.getGameTimeDeltaPartialTick(false));
	}
}
