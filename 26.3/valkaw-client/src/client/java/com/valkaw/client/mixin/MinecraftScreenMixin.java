package com.valkaw.client.mixin;

import com.valkaw.client.gui.ValkawMainMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Gui.class)
public class MinecraftScreenMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen valkaw$replaceTitleScreen(Screen screen) {
		if (screen instanceof TitleScreen || (screen == null && Minecraft.getInstance().level == null)) {
			return new ValkawMainMenu();
		}
		return screen;
	}
}
