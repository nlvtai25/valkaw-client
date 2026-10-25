package mc.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import mc.client.standalone.Freelook;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.Perspective;

/** Freelook = góc nhìn thứ 3 mở khóa: giữ F4 thì ép sang third-person để camera xoay
 *  quanh nhân vật, thả ra trả lại góc nhìn người chơi đang chọn. */
@Mixin(GameOptions.class)
public abstract class GameOptionsFreelookMixin {

    @Inject(method = "getPerspective", at = @At("HEAD"), cancellable = true)
    private void valkaw$freelookPerspective(CallbackInfoReturnable<Perspective> cir) {
        if (Freelook.active) cir.setReturnValue(Perspective.THIRD_PERSON_BACK);
    }
}
