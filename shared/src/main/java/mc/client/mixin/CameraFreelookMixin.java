package mc.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import mc.client.standalone.Freelook;
import net.minecraft.client.render.Camera;

/** Camera dùng Entity.getYaw(tickDelta)/getPitch(tickDelta) chứ không phải renderYaw,
 *  nên phải ghi đè góc xoay camera khi freelook bật. */
@Mixin(Camera.class)
public abstract class CameraFreelookMixin {

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, index = 1)
    private float valkaw$freelookYaw(float yaw) {
        // Front: xoay 180° để camera nằm trước mặt nhân vật (yaw gốc = sau lưng).
        if (!Freelook.active) return yaw;
        float f = Freelook.yaw() + (Freelook.front ? 180.0F : 0.0F);
        return f >= 180.0F ? f - 360.0F : (f < -180.0F ? f + 360.0F : f);
    }

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, index = 2)
    private float valkaw$freelookPitch(float pitch) {
        return Freelook.active ? Freelook.pitch() : pitch;
    }
}
