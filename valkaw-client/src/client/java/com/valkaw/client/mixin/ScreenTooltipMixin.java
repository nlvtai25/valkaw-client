package com.valkaw.client.mixin;

import com.valkaw.client.module.ModuleManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extends item tooltips with a durability percentage (Better Tooltips). */
@Mixin(Screen.class)
public class ScreenTooltipMixin {
	@Inject(method = "getTooltipFromItem", at = @At("RETURN"), cancellable = true)
	private static void valkaw$tooltipEnhance(Minecraft mc, ItemStack stack, CallbackInfoReturnable<List<Component>> cir) {
		List<Component> lines = cir.getReturnValue();
		if (lines.isEmpty() || !ModuleManager.isOn("better-tooltips") || !stack.isDamaged()) {
			return;
		}
		int max = stack.getMaxDamage();
		if (max <= 0) {
			return;
		}
		int pct = (int) Math.round(100.0 * (max - stack.getDamageValue()) / max);
		ChatFormatting color = pct > 60 ? ChatFormatting.GREEN : pct > 25 ? ChatFormatting.YELLOW : ChatFormatting.RED;
		List<Component> enhanced = new ArrayList<>(lines);
		enhanced.add(Component.literal("Durability: " + pct + "%").withStyle(color));
		cir.setReturnValue(enhanced);
	}
}
