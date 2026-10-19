package mc.client.standalone;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

/** Trạng thái freelook: tách camera khỏi hướng nhìn của nhân vật. Giữ phím để xoay tự do. */
public final class Freelook {
    public static boolean active;
    static float yaw, pitch;

    /** Khởi tạo freelook từ hướng hiện tại của người chơi. */
    public static void start(ClientPlayerEntity p) {
        active = true;
        yaw = p.getYaw();
        pitch = p.getPitch();
    }

    public static void stop() { active = false; }

    /** Cộng dồn delta chuột (gọi từ mixin trong Entity.changeLookDirection). */
    public static void addDelta(double dx, double dy) {
        yaw   += (float) dx * 0.15F;
        pitch += (float) dy * 0.15F;
        pitch  = MathHelper.clamp(pitch, -90F, 90F);
    }

    /** Áp freelook yaw/pitch lên render yaw/pitch của client player.
     *  Gọi ở END_CLIENT_TICK (sau player tick, trước render). */
    public static void apply(ClientPlayerEntity p) {
        if (!active) return;
        p.renderYaw   = yaw;
        p.renderPitch = pitch;
    }
}