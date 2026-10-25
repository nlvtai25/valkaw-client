package mc.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;

import mc.client.standalone.Perf;
import net.minecraft.client.render.fog.FogRenderer;

/** No Fog: khi fogEnabled=false vanilla trả buffer rỗng, tức là không có sương. */
@Mixin(FogRenderer.class)
public abstract class NoFogMixin {

    @Shadow private GpuBuffer emptyBuffer;

    @Inject(method = "getFogBuffer", at = @At("HEAD"), cancellable = true)
    private void valkaw$noFog(FogRenderer.FogType type, CallbackInfoReturnable<GpuBufferSlice> cir) {
        if (Perf.noFog) cir.setReturnValue(emptyBuffer.slice(0L, FogRenderer.FOG_UBO_SIZE));
    }
}
