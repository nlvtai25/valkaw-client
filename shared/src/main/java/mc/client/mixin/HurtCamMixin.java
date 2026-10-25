package mc.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import mc.client.standalone.Perf;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;

/** Performance > No Hurt Cam: bỏ rung nghiêng màn hình khi bị đánh. */
@Mixin(GameRenderer.class)
public abstract class HurtCamMixin {

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void valkaw$noHurtCam(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
        if (Perf.noHurtCam) ci.cancel();
    }
}
