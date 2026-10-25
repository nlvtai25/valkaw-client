package mc.client.mixin;

import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;

import mc.client.standalone.Perf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;

/** Performance > Clear Water/Lava: bỏ sương màu khi người chơi ở trong nước/dung nham. */
@Mixin(FogRenderer.class)
public abstract class ClearFluidMixin {

    @Shadow private GpuBuffer emptyBuffer;

    // cancellable=true bắt buộc: setReturnValue() ném CancellationException nếu thiếu
    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
    private void valkaw$clearFluid(Camera camera, float tickProgress, ClientWorld world,
                                   int viewDistance, float skyDarkness,
                                   CallbackInfoReturnable<Vector4f> cir) {
        if (!Perf.clearFluid) return;
        Vector4f c = cir.getReturnValue();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (c != null && mc != null && mc.player != null
                && (mc.player.isSubmergedInWater() || mc.player.isInLava())) {
            cir.setReturnValue(new Vector4f(c.x, c.y, c.z, 0.0F));
        }
    }

    // Clear Water: disable fog buffer entirely while submerged so water/lava reads clear.
    @Inject(method = "getFogBuffer", at = @At("HEAD"), cancellable = true)
    private void valkaw$clearFluidBuffer(FogRenderer.FogType type, CallbackInfoReturnable<GpuBufferSlice> cir) {
        if (!Perf.clearFluid) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null
                && (mc.player.isSubmergedInWater() || mc.player.isInLava())) {
            cir.setReturnValue(emptyBuffer.slice(0L, FogRenderer.FOG_UBO_SIZE));
        }
    }
}
