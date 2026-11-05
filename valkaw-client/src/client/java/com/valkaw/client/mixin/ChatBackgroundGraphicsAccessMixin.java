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

/** Same Copy chip as {@link ChatGraphicsAccessMixin}, for the chat HUD shown while playing. */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess")
public class ChatBackgroundGraphicsAccessMixin {
	@Shadow
	@Final
	private GuiGraphics graphics;

	@Inject(method = "handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z", at = @At("TAIL"))
	private void valkaw$copyChip(int y, float opacity, FormattedCharSequence content,
			CallbackInfoReturnable<Boolean> cir) {
		ChatCopyButtons.drawLine(graphics, y, opacity, content);
	}
}
