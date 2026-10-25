package mc.client.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import mc.client.standalone.Perf;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.util.Identifier;

/**
 * No Pumpkin Overlay: từ 1.21.11 lớp mờ khi đội bí ngô là "camera overlay" của
 * EquippableComponent (Items.CARVED_PUMPKIN gắn textures/misc/pumpkinblur).
 * Trả Optional.empty() = không vẽ overlay đó.
 */
@Mixin(EquippableComponent.class)
public abstract class NoPumpkinMixin {

    @Inject(method = "cameraOverlay", at = @At("RETURN"), cancellable = true)
    private void valkaw$noPumpkin(CallbackInfoReturnable<Optional<Identifier>> cir) {
        if (Perf.noPumpkin) cir.setReturnValue(Optional.empty());
    }
}
