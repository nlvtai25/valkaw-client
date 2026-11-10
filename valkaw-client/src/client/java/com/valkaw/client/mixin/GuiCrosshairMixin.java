package com.valkaw.client.mixin;

import com.valkaw.client.module.hud.CrosshairModule;
import com.valkaw.client.module.Module;
import com.valkaw.client.module.ModuleManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiCrosshairMixin {
	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void valkaw$renderCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		Module module = ModuleManager.get().byId("crosshair");
		if (!(module instanceof CrosshairModule crosshair) || !crosshair.isEnabled() || mc.player == null
				|| mc.gameMode == null || mc.gameMode.getPlayerMode() == GameType.SPECTATOR
				|| !mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		crosshair.renderCrosshair(graphics);
		ci.cancel();
	}
}
