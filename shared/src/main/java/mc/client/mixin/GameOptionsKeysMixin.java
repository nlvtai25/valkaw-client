package mc.client.mixin;

import mc.client.standalone.Keybinds;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.11: Controls screen + options.txt chỉ đọc {@code GameOptions.allKeys} (mảng dựng 1 lần
 * trong constructor), nên KeyBinding tự tạo không tự xuất hiện. Chèn sau khi mảng dựng xong,
 * trước {@code load()} -> keybind của client hiện trong Controls và lưu/đọc được.
 */
@Mixin(GameOptions.class)
public class GameOptionsKeysMixin {
    @Mutable @Shadow @Final public KeyBinding[] allKeys;

    @Inject(method = "load", at = @At("HEAD"))
    private void valkaw$addKeybinds(CallbackInfo ci) {
        // Phai chen TRUOC khi load() doc file. Chen o "<init>" RETURN thi muon: load() chay ben trong
        // constructor (truoc RETURN) nen key cua client chi duoc GHI ra options.txt chu khong duoc DOC lai.
        allKeys = Keybinds.appendAll(allKeys);
    }
}
