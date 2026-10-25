package mc.client.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import mc.client.standalone.SymbolPanel;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;

/** Gắn ô chèn symbol vào màn hình chat (T hoặc /). */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Shadow protected TextFieldWidget chatField;
    @Shadow protected ChatInputSuggestor chatInputSuggestor;

    @Shadow protected abstract void setChatFromHistory(int offset);

    private SymbolPanel valkaw$panel;

    @Inject(method = "init", at = @At("TAIL"))
    private void valkaw$addSymbols(CallbackInfo ci) {
        if (this.chatField == null) return;
        SymbolPanel p = new SymbolPanel(this.chatField);
        valkaw$panel = p;
        ((ScreenInvoker) this).valkaw$addDrawableChild(p);
    }

    /**
     * 1.21.11 dùng phím mũi tên để di chuyển focus giữa các widget (Screen.keyPressed).
     * Khi có widget khác trong ChatScreen, một phím mũi tên có thể cướp focus khỏi ô chat
     * trước khi ChatScreen xử lý lịch sử/con trỏ. Chặn phím mũi tên ở đây để chúng luôn
     * thuộc về ô chat: tập trung lại vào ô chat rồi tự xử lý.
     * Phím UP/DOWN: để nguyên cho bảng gợi ý command nếu nó đang mở.
     */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void valkaw$keepArrowsInChat(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        TextFieldWidget field = this.chatField;
        if (field == null) return;
        int key = input.key();
        boolean arrow = key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT
                || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN;
        if (!arrow) return;
        if (this.chatInputSuggestor != null && this.chatInputSuggestor.isOpen()) return;

        ((Screen) (Object) this).setFocused(field);

        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            field.keyPressed(input);
        } else {
            setChatFromHistory(key == GLFW.GLFW_KEY_UP ? -1 : 1);
        }
        cir.setReturnValue(true);
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
