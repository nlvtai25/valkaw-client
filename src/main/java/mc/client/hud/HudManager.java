package mc.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Quản lý HUD: đăng ký, bật/tắt, di chuyển (kèm giới hạn màn hình), liên kết nhóm.
 * Tối ưu: mảng phẳng, 1 pass mỗi frame, không stream/lambda per-frame.
 */
public final class HudManager {
    public static final int GAP = 0;      // 0 = các module dính liền thành 1 khối
    public static final int MARGIN = 4;
    public static final int SNAP = 8;     // hút về lề trái/phải khi kéo tới gần

    private static final HudManager INSTANCE = new HudManager();

    private HudWidget[] widgets = new HudWidget[0];
    private int count = 0;
    private boolean[] mark = new boolean[16];   // dùng cho relayout, không cấp phát lại
    private int nextGroup = 0;
    private boolean needsLayout = true;

    public int screenW = 320, screenH = 240;    // toạ độ GUI (scaled)
    public int designW = 0, designH = 0;        // kích thước GUI lúc lưu layout (0 = chưa biết)

    private HudManager() {}

    public static HudManager get() { return INSTANCE; }

    /** Đăng ký module theo thứ tự hiểnịn (trên -> dưới). Chỉ chạy 1 lần. */
    public synchronized void register(GameState s) {
        if (count > 0) return;
        SysInfo.start();
        add(new Widgets.Fps(s));
        add(new Widgets.Coords(s));
        add(new Widgets.Ping(s));
        add(new Widgets.ServerIp(s));
        add(new Widgets.Time());
        add(new Widgets.Sprint(s));
        add(new Widgets.Armor());
        add(new Widgets.Ram());
        add(new Widgets.Cpu());
        add(new Widgets.Gpu());
        add(new HitboxWidget());
        add(new Widgets.Crosshair());
        add(new Widgets.Biome());
        add(new Widgets.Potion());
        add(new BlockOverlayWidget());
    }

    private void add(HudWidget w) {
        if (count == widgets.length) {
            HudWidget[] bigger = new HudWidget[count == 0 ? 8 : count * 2];
            System.arraycopy(widgets, 0, bigger, 0, count);
            widgets = bigger;
        }
        if (count == mark.length) {
            boolean[] m = new boolean[mark.length * 2];
            System.arraycopy(mark, 0, m, 0, mark.length);
            mark = m;
        }
        widgets[count++] = w;
    }

    public int size() { return count; }
    public HudWidget get(int i) { return widgets[i]; }

    public HudWidget byId(String id) {
        for (int i = 0; i < count; i++) if (widgets[i].id.equals(id)) return widgets[i];
        return null;
    }

    public void setEnabled(String id, boolean on) { HudWidget w = byId(id); if (w != null) w.enabled = on; }

    /** Widget ảo giữ giá trị setting chung (không nằm trong danh sách vẽ/kéo). */
    public final HudWidget global = new HudWidget("global", "Global") {};

    /** Nạp giá trị chung hiện tại (lấy module đầu tiên làm mốc hiển thị). */
    public void seedGlobal() {
        for (int i = 0; i < count; i++) {
            HudWidget w = widgets[i];
            if (w.function || w.center) continue;
            global.textShadow = w.textShadow;
            global.bgEnabled = w.bgEnabled;
            global.textColor = w.textColor;
            global.bgColor = w.bgColor;
            global.effect = w.effect;
            return;
        }
    }

    /** Áp setting chung lên mọi module (bỏ module công tắc và crosshair). */
    public void applyGlobal() {
        for (int i = 0; i < count; i++) {
            HudWidget w = widgets[i];
            if (w.function || w.center) continue;
            w.textShadow = global.textShadow;
            w.bgEnabled = global.bgEnabled;
            w.textColor = global.textColor;
            w.bgColor = global.bgColor;
            w.effect = global.effect;
        }
    }

    public void clearLayoutFlag() { needsLayout = false; }
    public void bumpGroup(int g) { if (g >= nextGroup) nextGroup = g + 1; }

    // ---- layout ----

    /** Xếp các module rời (không liên kết) thành 1 cột dọc với khoảng cách đồng đều. */
    public void autoLayout() {
        int y = MARGIN;
        for (int i = 0; i < count; i++) {
            HudWidget w = widgets[i];
            if (w.function || w.center || w.group != -1 || !w.enabled) continue;
            w.x = MARGIN;
            w.y = y;
            y += w.height + GAP;
        }
        clampAll();
    }

    private HudWidget topmost(int g) {
        HudWidget a = null;
        for (int i = 0; i < count; i++) {
            HudWidget w = widgets[i];
            if (w.group == g && (a == null || w.y < a.y)) a = w;
        }
        return a;
    }

    /** Mép trái khối = x nhỏ nhất trong nhóm. */
    private int groupLeft(int g) {
        int left = Integer.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            HudWidget w = widgets[i];
            if (w.group == g && w.x < left) left = w.x;
        }
        return left == Integer.MAX_VALUE ? 0 : left;
    }

    /** Khối gần mép phải màn hình hơn -> căn phải. */
    private boolean rightAligned(int g, int left, int gw) {
        return screenW - (left + gw) < left;
    }

    /** Đặt x mọi thành viên theo kiểu căn lề của khối; giữ nguyên bề rộng riêng từng module. */
    private void placeGroupX(int g, int left) {
        int gw = groupWidth(g);
        boolean right = rightAligned(g, left, gw);
        for (int i = 0; i < count; i++) {
            HudWidget w = widgets[i];
            if (w.group != g || w.function) continue;
            w.x = right ? left + gw - w.width : left;
        }
    }

    /** Xếp dọc các thành viên từ module trên cùng (y nhỏ nhất). */
    private void relayoutY(int g) {
        HudWidget anchor = topmost(g);
        if (anchor == null) return;
        for (int i = 0; i < count; i++) mark[i] = false;
        int y = anchor.y + anchor.height + GAP;
        mark[indexOf(anchor)] = true;
        for (int placed = 1; placed < count; placed++) {
            HudWidget next = null; int ni = -1;
            for (int i = 0; i < count; i++) {
                HudWidget w = widgets[i];
                if (w.group == g && !mark[i] && (next == null || w.y < next.y)) { next = w; ni = i; }
            }
            if (next == null) break;
            next.y = y;
            mark[ni] = true;
            y += next.height + GAP;
        }
    }

    /** Kẹp mép trên khối sao cho cả cột nằm trong màn hình. */
    private int clampGroupTop(int g, int y) {
        int totalH = 0, n = 0;
        for (int i = 0; i < count; i++) {
            HudWidget w = widgets[i];
            if (w.group != g) continue;
            totalH += w.height; n++;
        }
        if (n > 1) totalH += GAP * (n - 1);
        int maxY = Math.max(0, screenH - totalH);
        if (y < 0) return 0;
        return Math.min(y, maxY);
    }

    /** Sắp lại 1 khối: kẹp vào màn hình + căn lề trái/phải + xếp dọc. */
    private void layoutGroup(int g) {
        HudWidget anchor = topmost(g);
        if (anchor == null) return;
        int gw = groupWidth(g);
        int left = groupLeft(g);
        left = Math.max(0, Math.min(left, Math.max(0, screenW - gw)));
        anchor.y = clampGroupTop(g, anchor.y);
        relayoutY(g);
        placeGroupX(g, left);
    }

    private int indexOf(HudWidget w) {
        for (int i = 0; i < count; i++) if (widgets[i] == w) return i;
        return -1;
    }

    // ---- move + clamp ----

    /** Di chuyển 1 module; nếu thuộc nhóm thì cả nhóm di chuyển theo. Giới hạn trong màn hình. */
    public void move(HudWidget w, int dx, int dy) {
        if (w.center) return;
        if (w.group == -1) {
            w.x += dx; w.y += dy;
            w.x = snapX(w.x, w.width);
            clampWidget(w);
        } else {
            int g = w.group;
            HudWidget anchor = topmost(g);
            if (anchor == null) return;
            int gw = groupWidth(g);
            int maxX = Math.max(0, screenW - gw);
            int left = groupLeft(g) + dx;
            int rightStop = screenW - gw - MARGIN;
            if (left <= MARGIN + SNAP) left = MARGIN;
            else if (left >= rightStop - SNAP) left = rightStop;
            left = Math.max(0, Math.min(left, maxX));
            anchor.y = clampGroupTop(g, anchor.y + dy);
            relayoutY(g);
            placeGroupX(g, left);
        }
    }

    /** Nam châm: sát lề trái/phải thì dính về đúng lề (MARGIN), ở giữa thì giữ nguyên. */
    private int snapX(int x, int w) {
        int right = screenW - w - MARGIN;
        if (x <= MARGIN + SNAP) return MARGIN;
        if (x >= right - SNAP) return right;
        return x;
    }

    private int groupWidth(int g) {
        int maxW = 0;
        for (int i = 0; i < count; i++) {
            HudWidget w = widgets[i];
            if (w.group == g && w.width > maxW) maxW = w.width;
        }
        return maxW;
    }

    private void clampWidget(HudWidget w) {
        if (w.function) return;
        int maxX = Math.max(0, screenW - w.width);
        int maxY = Math.max(0, screenH - w.height);
        if (w.x < 0) w.x = 0; else if (w.x > maxX) w.x = maxX;
        if (w.y < 0) w.y = 0; else if (w.y > maxY) w.y = maxY;
    }

    public void clampAll() {
        for (int i = 0; i < count; i++) {
            HudWidget w = widgets[i];
            if (w.function || w.center) continue;
            if (w.group == -1) { clampWidget(w); continue; }
            if (topmost(w.group) != w) continue;   // chỉ xử lý 1 lần mỗi nhóm
            layoutGroup(w.group);
        }
    }

    /** Dãn vị trí tất cả module theo tỷ lệ khi kích thước màn hình thay đổi
     *  (chuyển fullscreen ↔ windowed, đổi GUI scale). Giữ nguyên vị trí tương đối. */
    public void rescale(int oldW, int oldH, int newW, int newH) {
        if (oldW <= 0 || oldH <= 0 || newW <= 0 || newH <= 0) return;
        float sx = (float) newW / oldW;
        float sy = (float) newH / oldH;
        for (int i = 0; i < count; i++) {
            HudWidget w = widgets[i];
            if (w.function || w.center) continue;
            w.x = Math.round(w.x * sx);
            w.y = Math.round(w.y * sy);
        }
        screenW = newW; screenH = newH;
        clampAll();  // kẹp lại trong màn hình mới + sắp nhóm
    }

    /** Lần đầu vào game: scale layout đã lưu từ kích thước thiết kế sang cửa sổ hiện tại,
     *  để HUD giữ đúng vị trí tương đối khi màn hình/độ phân giải khác lúc thiết kế. */
    public void adaptToDesign(int w, int h) {
        if (designW > 0 && designH > 0) rescale(designW, designH, w, h);
        else { screenW = w; screenH = h; clampAll(); }
        designW = w; designH = h;
    }

    // ---- link (liên kết trên/dưới) ----

    /** Nối parent ở trên, child ở ngay dưới. Chống cycle bằng kiểm tra cùng nhóm. */
    public void link(HudWidget parent, HudWidget child) {
        if (parent == null || child == null || parent == child) return;
        if (parent.function || child.function) return;
        int gp = parent.group, gc = child.group;
        if (gp != -1 && gp == gc) return;
        int g = gp != -1 ? gp : (gc != -1 ? gc : nextGroup++);
        for (int i = 0; i < count; i++) {
            HudWidget w = widgets[i];
            if (gp != -1 && w.group == gp) w.group = g;
            else if (gc != -1 && w.group == gc) w.group = g;
        }
        parent.group = g;
        child.group = g;
        child.y = parent.y + parent.height + GAP;
        placeGroupX(g, parent.x);
        relayoutY(g);
        clampAll();
    }

    /** Tách 1 module khỏi nhóm. */
    public void unlink(HudWidget w) {
        int g = w.group;
        if (g == -1) return;
        w.group = -1;
        int rem = 0;
        for (int i = 0; i < count; i++) if (widgets[i].group == g) rem++;
        if (rem <= 1) {
            for (int i = 0; i < count; i++) if (widgets[i].group == g) widgets[i].group = -1;
        } else {
            layoutGroup(g);
        }
    }

    /** Gọi mỗi frame sau prepare: căn lại mép khối khi bề rộng text đổi (giữ bề rộng riêng). */
    public void placeGroups() {
        for (int i = 0; i < count; i++) {
            HudWidget w = widgets[i];
            if (w.group == -1 || w.function || w.center) continue;
            placeGroupX(w.group, groupLeft(w.group));
        }
    }

    // ---- render ----

    /** Gọi mỗi frame: cập nhật + đo + vẽ, kèm tự động xếp cột ở lần đầu. */
    public void renderFrame(DrawContext ctx, float dt) {
        if (ctx == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        var font = mc.textRenderer;

        for (int i = 0; i < count; i++) widgets[i].update(dt);

        if (needsLayout) {
            for (int i = 0; i < count; i++) widgets[i].prepare(font);
            autoLayout();
            needsLayout = false;
        }

        for (int i = 0; i < count; i++) widgets[i].prepare(font);
        placeGroups();
        clampAll();   // giữ mọi module trong màn hình (text dài ra hoặc cửa sổ nhỏ lại)
        for (int i = 0; i < count; i++) {
            HudWidget w = widgets[i];
            if (!w.enabled) continue;
            w.render(ctx, font);
        }
    }
}
