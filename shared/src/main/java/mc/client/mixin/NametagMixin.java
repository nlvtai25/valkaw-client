package mc.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import mc.client.standalone.Perf;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;

/** Performance > Hide Nametag: bỏ qua vẽ tên trên đầu. */
@Mixin(EntityRenderer.class)
public abstract class NametagMixin {

    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"), cancellable = true)
    private void valkaw$hideNametag(EntityRenderState state, MatrixStack matrices,
                                    OrderedRenderCommandQueue queue, CameraRenderState camera,
                                    CallbackInfo ci) {
        if (Perf.hideNametag) ci.cancel();
    }
}
