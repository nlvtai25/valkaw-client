package mc.client.standalone;

import net.minecraft.client.MinecraftClient;

/**
 * Quick Chat: gửi 1 dòng chat/lệnh khi bấm phím đã gán (Options > Controls > Valkaw).
 * Bật/tắt bằng module "quickchat" trong HUD Manager > Modules. "/" đầu dòng = gửi lệnh.
 */
public final class QuickChat {
    public static String message = "";

    private QuickChat() {}

    public static void set(String key, String value) {
        switch (key) {
            case "message" -> message = value;
            // quickchat.key/mods: bản cũ lưu phím trong HUD cfg, giờ phím do Keybinds quản lý.
            default -> { }
        }
    }

    public static String dump() {
        return "quickchat.message=" + message + "\n";
    }

    /** Gọi mỗi client tick (chỉ khi module quickchat đang bật). */
    public static void tick(MinecraftClient mc) {
        boolean pressed = Keybinds.QUICK_CHAT.wasPressed();
        if (!pressed) return;
        if (mc.player == null || mc.getNetworkHandler() == null || mc.currentScreen != null) return;
        if (!message.isBlank()) send(mc, message);
    }

    private static void send(MinecraftClient mc, String m) {
        if (m.startsWith("/")) mc.getNetworkHandler().sendChatCommand(m.substring(1));
        else mc.getNetworkHandler().sendChatMessage(m);
    }
}
