package mc.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import mc.client.standalone.Perf;
import net.minecraft.client.gui.hud.ChatHud;

/**
 * Infinity Chat History.
 * Vanilla cắt lịch sử chat còn 100 dòng ở 2 chỗ: ChatHud.addMessage(ChatHudLine)
 * (danh sách gốc) và addVisibleMessage (danh sách dòng đã wrap để vẽ).
 * Đổi hằng 100 đó thành Perf.CHAT_LIMIT khi bật toggle trong tab Perf.
 */
@Mixin(ChatHud.class)
public class ChatHudMixin {

    @ModifyConstant(
            method = { "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V",
                       "addVisibleMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V" },
            constant = @Constant(intValue = 100))
    private int valkaw$chatHistoryLimit(int vanilla) {
        return Perf.infinityChat ? Perf.chatLimit : vanilla;
    }
}
