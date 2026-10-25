package mc.client.hud;

/**
 * Widget vẽ khung hộp (Block Overlay, Hitbox): dùng chung setting màu + độ dày.
 * colorMode: 0 = Game (bảng màu), 1 = HEX, 2 = RGB (hiệu ứng).
 */
public abstract class ColorBoxWidget extends HudWidget {
    public int colorMode = 0;
    public float thickness = 2.5f;      // pixel
    public int lineColor = 0xFF00FFFF;  // ARGB
    protected float minThickness = 0f;

    protected ColorBoxWidget(String id, String label) { super(id, label, true); }

    /** Màu hiện tại theo mode (ARGB). Nơi vẽ tự quyết alpha. */
    public int renderColor(long timeMs) {
        if (colorMode == 1) return lineColor;
        if (colorMode == 2) {
            int e = effect == 0 ? 1 : effect;   // "Off" -> Rainbow
            return HudWidget.effectRgb(e, lineColor, (int) ((timeMs / 3L) % 1024), 0, 300);
        }
        return 0xFF000000 | (lineColor & 0xFFFFFF);
    }
}
