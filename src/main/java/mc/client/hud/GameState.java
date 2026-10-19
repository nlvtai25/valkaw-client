package mc.client.hud;

/**
 * Nguồn dữ liệu game cho HUD. Client thật (Minecraft) hiện thực interface này.
 * Tách riêng để logic HUD compile được độc lập với Minecraft.
 */
public interface GameState {
    float fps();
    double px(); double py(); double pz();
    int ping();
    String serverIp();
    boolean sprinting();
    boolean autoSprint();
}
