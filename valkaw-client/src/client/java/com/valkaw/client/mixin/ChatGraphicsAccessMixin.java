package com.valkaw.client.mixin;

import com.valkaw.client.gui.ChatCopyButtons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws a Copy chip after every chat line while the chat screen is open. This runs on the
 * focused renderer, so the pose stack is already scaled by the chat scale and translated by 4.
 */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess")
public class ChatGraphicsAccessMixin {
	@Shadow
	@Final
	private GuiGraphics graphics;

	@Inject(method = "handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z", at = @At("TAIL"))
	private void valkaw$copyChip(int y, float opacity, FormattedCharSequence content,
			CallbackInfoReturnable<Boolean> cir) {
		ChatCopyButtons.drawLine(graphics, y, opacity, content);
	}
}
