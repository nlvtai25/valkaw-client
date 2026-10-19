package mc.client.standalone;

import mc.client.hud.CustomHudOverlay;
import mc.client.hud.GameState;
import mc.client.menu.CustomMenuScreen;

/**
 * Client standalone 1.21.11 — nhúng tất cả mod tối ưu + HUD widget.
 * Phần đọc dữ liệu Minecraft thật nằm trong project MCP-Reborn (có net.minecraft),
 * gọi Launcher.bind(state) để nối vào HUD.
 */
public final class Launcher {

    /** Provider mặc định (chưa nối game) — tránh phụ thuộc net.minecraft khi build jar độc lập. */
    private static GameState state = new GameState() {
        public float fps()         { return 0f; }
        public double px()         { return 0; }
        public double py()         { return 0; }
        public double pz()         { return 0; }
        public int ping()          { return 0; }
        public String serverIp()   { return ""; }
        public boolean sprinting() { return false; }
        public boolean autoSprint() { return false; }
    };

    /** Client Minecraft thật gọi hàm này sau khi khởi tạo. */
    public static void bind(GameState real) { if (real != null) state = real; }

    public static void main(String[] args) {
        System.out.println("=== MC 1.21.11 STANDALONE CLIENT ===");
        System.out.println("Embedded mods: sodium, lithium, phosphor, modernfix, entityculling,");
        System.out.println("  ferritecore, immediatelyfast, krypton, dynamicfps, nvidium");
        CustomHudOverlay.init(state, null);
        CustomMenuScreen.register();
        System.out.println("HUD widgets: FPS, Coords, Ping, IP Server, Time, Sprint, Armor, Hitbox");
        System.out.println("Ready. Build: gradlew jar");
    }
}
