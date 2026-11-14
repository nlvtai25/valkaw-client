package com.valkaw.client.mixin;

import com.valkaw.client.SnapChatAccess;
import com.valkaw.client.gui.ChatCopyButtons;
import com.valkaw.client.module.ModuleManager;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Clicking a Copy chip next to a chat line copies that line. */
@Mixin(ChatScreen.class)
public class ChatScreenMixin implements SnapChatAccess {
	@Shadow
	protected EditBox input;

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void valkaw$copyChatLine(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (event.button() != 0 || !ModuleManager.isOn("copy-chat")) {
			return;
		}
		if (ChatCopyButtons.click(event.x(), event.y())) {
			cir.setReturnValue(true);
		}
	}

	@Override
	public void valkawSetInput(String text) {
		input.setValue(text);
	}
}
