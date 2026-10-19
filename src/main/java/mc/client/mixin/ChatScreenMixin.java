package mc.client.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import mc.client.standalone.SymbolPanel;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;

/** Gắn ô chọn symbol vào màn hình chat (T hoặc /). */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Shadow protected TextFieldWidget chatField;

    private static SymbolPanel valkaw$panel;

    @Inject(method = "init", at = @At("TAIL"))
    private void valkaw$addSymbols(CallbackInfo ci) {
        if (this.chatField == null) return;
        SymbolPanel p = new SymbolPanel(this.chatField);
        valkaw$panel = p;
        ((ScreenInvoker) this).valkaw$addDrawableChild(p);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void valkaw$escClosePanel(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        SymbolPanel p = valkaw$panel;
        if (p != null && p.isOpen() && input.key() == GLFW.GLFW_KEY_ESCAPE) {
            p.close();
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void valkaw$scrollPanel(double mx, double my, double horizontal, double vertical,
                                    CallbackInfoReturnable<Boolean> cir) {
        SymbolPanel p = valkaw$panel;
        if (p != null && p.mouseScrolled(mx, my, horizontal, vertical)) cir.setReturnValue(true);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void valkaw$onRemoved(CallbackInfo ci) {
        valkaw$panel = null;
    }
}
