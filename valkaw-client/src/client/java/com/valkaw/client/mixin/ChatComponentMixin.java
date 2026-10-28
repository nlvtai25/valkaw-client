package com.valkaw.client.mixin;

import com.valkaw.client.module.Module;
import com.valkaw.client.module.ModuleManager;
import com.valkaw.client.module.utility.ChatTimestampsModule;
import com.valkaw.client.module.utility.CopyChatModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {
	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"))
	private void valkaw$remember(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
		CopyChatModule.remember(message);
	}

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
}
