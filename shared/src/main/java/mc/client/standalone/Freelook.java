package mc.client.standalone;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

/** Trạng thái freelook: tách camera khỏi hướng nhìn của nhân vật. Giữ phím để xoay tự do. */
public final class Freelook {
    public static boolean active;
    /** true = camera nhìn từ phía trước (F5 lần 2), false = sau lưng (F5 lần 1). */
    public static boolean front;
    /** Hiện crosshair khi đang freelook. */
    public static boolean showCrosshair = true;
    static float yaw, pitch;
    /** Khởi tạo freelook từ hướng hiện tại của người chơi. */
    public static void start(ClientPlayerEntity p) {
        active = true;
        yaw = p.getYaw();
        pitch = p.getPitch();
    }

    public static void stop() { active = false; }

    public static float yaw() { return yaw; }

    public static float pitch() { return pitch; }

    /** Cộng dồn delta chuột (gọi từ mixin trong Entity.changeLookDirection). */
    public static void addDelta(double dx, double dy) {
        yaw   += (float) dx * 0.15F;
        pitch += (float) dy * 0.15F;
        pitch  = MathHelper.clamp(pitch, -90F, 90F);
    }
}