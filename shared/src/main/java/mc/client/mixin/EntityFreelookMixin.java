package mc.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import mc.client.standalone.Freelook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;

/** Bắt delta chuột khi freelook đang bật, không cho đổi yaw/pitch thật của người chơi. */
@Mixin(Entity.class)
public class EntityFreelookMixin {

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void freelookCaptureMouse(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        if ((Object) this != mc.player) return;        // chỉ ảnh hưởng đến người chơi
        if (!Freelook.active) return;
        Freelook.addDelta(cursorDeltaX, cursorDeltaY);
        ci.cancel();                                    // ngăn setYaw/setPitch thật
    }
}