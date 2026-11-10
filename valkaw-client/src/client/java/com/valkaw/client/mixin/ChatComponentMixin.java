package com.valkaw.client.mixin;

import com.valkaw.client.gui.ChatCopyButtons;
import com.valkaw.client.module.Module;
import com.valkaw.client.module.ModuleManager;
import com.valkaw.client.module.utility.ChatTimestampsModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {
	@ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), argsOnly = true)
	private Component valkaw$timestamp(Component message) {
		Module module = ModuleManager.get().byId("chat-timestamps");
		if (!(module instanceof ChatTimestampsModule timestamps) || !timestamps.isEnabled()) {
			return message;
		}
		return Component.empty()
				.append(Component.literal(timestamps.stamp() + "§r").withStyle(ChatFormatting.WHITE))
				.append(message);
	}

	/** The Copy chips are rebuilt every frame; ChatGraphicsAccessMixin adds them back. */
	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V", at = @At("HEAD"))
	private void valkaw$clearChips(GuiGraphics graphics, Font font, int tickCount, int mouseX, int mouseY,
			boolean focused, boolean clickableOnly, CallbackInfo ci) {
		ChatCopyButtons.clear();
	}
}
