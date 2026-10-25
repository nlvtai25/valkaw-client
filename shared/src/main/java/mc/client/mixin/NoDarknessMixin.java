package mc.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import mc.client.standalone.Perf;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.entity.LivingEntity;

/** No Darkness: getDarkness trả "lượng tối" (0..~0.45) — trả 0 = không tối. */
@Mixin(LightmapTextureManager.class)
public abstract class NoDarknessMixin {

    @Inject(method = "getDarkness", at = @At("RETURN"), cancellable = true)
    private void valkaw$noDarkness(LivingEntity entity, float darkness, float tickDelta,
                                   CallbackInfoReturnable<Float> cir) {
        if (Perf.noDarkness) cir.setReturnValue(0.0F);
    }
}
