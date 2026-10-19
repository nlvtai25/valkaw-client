package mc.client.standalone;

import mc.client.hud.GameState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ServerInfo;

/** Đọc dữ liệu từ Minecraft thật (Yarn mappings) cho HUD. Null-safe để không crash. */
final class MinecraftGame implements GameState {

    private ClientPlayerEntity p() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc == null ? null : mc.player;
    }

    /** Lấy giá trị FPS đã làm mượt của debug menu F3 (cập nhật mỗi giây => đọc được). */
    @Override public float fps() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc == null ? 0f : mc.getCurrentFps();
    }

    @Override public double px() { ClientPlayerEntity p = p(); return p == null ? 0 : p.getX(); }
    @Override public double py() { ClientPlayerEntity p = p(); return p == null ? 0 : p.getY(); }
    @Override public double pz() { ClientPlayerEntity p = p(); return p == null ? 0 : p.getZ(); }

    @Override public int ping() {
        try {
            ClientPlayerEntity p = p();
            MinecraftClient mc = MinecraftClient.getInstance();
            if (p == null || mc.getNetworkHandler() == null) return 0;
            var entry = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
            return entry == null ? 0 : entry.getLatency();
        } catch (Throwable t) { return 0; }
    }

    /** IP server dạng chữ (address). Singleplayer -> "Singleplayer". */
    @Override public String serverIp() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) return "";
            if (mc.isInSingleplayer()) return "Singleplayer";
            ServerInfo si = mc.getCurrentServerEntry();
            return si == null ? "" : si.address;
        } catch (Throwable t) { return ""; }
    }

    @Override public boolean sprinting() {
        ClientPlayerEntity p = p();
        return p != null && p.isSprinting();
    }

    // Auto-sprint: trạng thái bật/tắt do người dùng toggle bằng V (bật tự động sprint khi di chuyển)
    private boolean autoSprint = false;
    @Override public boolean autoSprint() { return autoSprint; }
    public void setAutoSprint(boolean on) { autoSprint = on; }
}
