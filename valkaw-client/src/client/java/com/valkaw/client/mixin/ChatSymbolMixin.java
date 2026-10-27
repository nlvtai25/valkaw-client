package com.valkaw.client.mixin;

import com.valkaw.client.gui.ChatSymbolPanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatSymbolMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void valkaw$render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		ChatSymbolPanel.render((ChatScreen) (Object) this, graphics, mouseX, mouseY);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void valkaw$click(MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
		if (ChatSymbolPanel.click((ChatScreen) (Object) this, event.x(), event.y(), event.button())) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
	private void valkaw$scroll(double mouseX, double mouseY, double scrollX, double scrollY,
			CallbackInfoReturnable<Boolean> cir) {
		if (ChatSymbolPanel.scroll((ChatScreen) (Object) this, mouseX, mouseY, scrollY)) {
			cir.setReturnValue(true);
		}
	}
}
