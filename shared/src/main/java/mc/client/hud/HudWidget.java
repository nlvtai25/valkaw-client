package mc.client.hud;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Base widget HUD. Mỗi widget tự đo kích thước + vẽ nền riêng theo độ dài text.
 * Widget "function" (vd Hitbox) không có khối trên màn hình, chỉ là công tắc.
 * Widget "center" (Crosshair) vẽ ở giữa màn hình, không kéo/clamp.
 * Tối ưu: đo/vẽ 1 pass, không cấp phát mỗi frame.
 */
public abstract class HudWidget {
    public static final int PAD = 1;              // padding sát chữ, nền ôm khít module
    public static final int BG = 0x50000000;      // nền tối nhẹ cho mỗi module (thấy được thế giới phía sau)
    public static final int BORDER = 0x55FFFFFF;

    public final String id;
    public String label;                // hiển thị trong "[label]"; đổi được ở màn setting
    public final boolean function;      // true => công tắc, không vẽ khối

    public boolean enabled = true;
    public int x, y, width, height;
    public int group = -1;              // -1 = không liên kết
    public boolean center = false;      // vẽ giữa màn hình (crosshair): bỏ qua layout/kéo

    // ---- setting từng module ----
    public boolean hideLabel = false;   // ẩn "[tên]"
    public int textColor = 0xFFFFFFFF;
    public boolean textShadow = true;
    public boolean bgEnabled = true;
    public int bgColor = BG;            // gồm cả alpha
    public boolean vertical = false;    // nhiều giá trị -> mỗi giá trị 1 dòng
    public int effect = 0;              // 0 = tắt (dùng textColor); >0 = hiệu ứng RGB

    /** Tên các hiệu ứng RGB (lấy từ bàn phím cơ). Index = giá trị effect. */
    public static final String[] EFFECTS = {
        "Off", "Rainbow", "Wave", "Breathing", "Scanner", "Gradient",
    };

    protected String cached = "";

    protected HudWidget(String id, String label) { this(id, label, false); }

    protected HudWidget(String id, String label, boolean function) {
        this.id = id; this.label = label; this.function = function;
    }

    /** Cập nhật giá trị mỗi frame (không cấp phát). */
    public void update(float dt) {}

    /** Phần giá trị (không gồm "[tên] "). Có thể chứa '\n' cho nhiều dòng. */
    protected String content() { return ""; }

    /** Các dòng sẽ vẽ; dòng đầu gắn "[tên] " (trừ khi ẩn nhãn). */
    protected String[] lines() {
        String[] ls = cached.split("\n", -1);
        ls[0] = (hideLabel ? "" : "[" + label + "] ") + ls[0];
        return ls;
    }

    /** Đo kích thước + cache text. Gọi trước render mỗi frame. */
    public void prepare(TextRenderer font) {
        if (function || center) { width = 0; height = 0; return; }
        cached = content();
        String[] ls = lines();
        int w = 0;
        for (String s : ls) { int lw = font.getWidth(s); if (lw > w) w = lw; }
        width = w + PAD * 2;
        height = ls.length * font.fontHeight + PAD * 2;
    }

    public void render(DrawContext ctx, TextRenderer font) {
        if (function || width <= 0) return;
        if (bgEnabled) ctx.fill(x, y, x + width, y + height, bgColor);
        String[] ls = lines();
        if (effect == 0) {
            for (int i = 0; i < ls.length; i++) {
                ctx.drawText(font, ls[i], x + PAD, y + PAD + i * font.fontHeight, textColor, textShadow);
            }
            return;
        }
        // hiệu ứng RGB: vẽ từng ký tự với màu tính theo thời gian + vị trí
        for (int i = 0; i < ls.length; i++) {
            int px = x + PAD, py = y + PAD + i * font.fontHeight;
            String s = ls[i];
            for (int ci = 0; ci < s.length(); ci++) {
                String ch = s.substring(ci, ci + 1);   // shortcut: cấp phát nhỏ mỗi frame, đủ nhanh cho HUD
                ctx.drawText(font, ch, px, py, effectColor(px), textShadow);
                px += font.getWidth(ch);
            }
        }
    }

    /** Màu chữ cho hiệu ứng RGB tại toạ độ màn hình px. */
    private int effectColor(int px) { return effectRgb(effect, textColor, px, x, width); }

    /**
     * Màu của 1 điểm theo hiệu ứng RGB. px = toạ độ màn hình, origin = mép trái gốc, span = bề rộng.
     * Dùng chung cho cả chữ HUD và crosshair.
     */
    public static int effectRgb(int effect, int base, int px, int origin, int span) {
        long t = System.currentTimeMillis();
        int d = px - origin;
        return switch (effect) {
            case 1 -> hsv((t % 3000L) / 3000f, 1f, 1f);                                          // Rainbow
            case 2 -> hsv((t % 3000L) / 3000f + d * 0.02f, 1f, 1f);                              // Wave
            case 3 -> scale(base, 0.35f + 0.65f * (float) ((Math.sin(t / 400.0) + 1) / 2));      // Breathing
            case 4 -> {                                                                          // Scanner
                float pos = (t % 1800L) / 1800f * (span + 40) - 20;
                yield scale(base, Math.abs(d - pos) < 14 ? 1f : 0.35f);
            }
            case 5 -> hsv(d / (float) Math.max(1, span), 1f, 1f);                                // Gradient
            default -> base;
        };
    }

    private static int scale(int argb, float k) {
        return 0xFF000000
                | ((int) (((argb >> 16) & 0xFF) * k) << 16)
                | ((int) (((argb >> 8) & 0xFF) * k) << 8)
                | (int) ((argb & 0xFF) * k);
    }

    private static int hsv(float h, float s, float v) {
        h = ((h % 1f) + 1f) % 1f;
        int i = (int) (h * 6) % 6;
        float f = h * 6 - (int) (h * 6);
        float p = v * (1 - s), q = v * (1 - f * s), tt = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i) {
            case 0 -> { r = v; g = tt; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = tt; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = tt; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }
}
