package mc.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import mc.client.standalone.Perf;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;

/** Performance > No Fire Overlay: không vẽ lớp lửa phủ màn hình. */
@Mixin(InGameOverlayRenderer.class)
public abstract class FireOverlayMixin {

    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void valkaw$noFire(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                               Sprite sprite, CallbackInfo ci) {
        if (Perf.noFireOverlay) ci.cancel();
    }
}
