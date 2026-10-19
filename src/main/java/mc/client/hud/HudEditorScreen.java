package mc.client.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * HUD Manager + Editor.
 * - Sidebar: bật/tắt từng module.
 * - Vùng chính: kéo để di chuyển (tự giới hạn trong màn hình).
 * - 2 điểm giữa trên/dưới mỗi module để liên kết thành 1 khối (kéo điểm dưới của A vào B => B nằm dưới A).
 */
public final class HudEditorScreen extends Screen {
    private static final int SIDEBAR = 168;
    private static final int POINT = 4;
    private static final int CELL = 20;

    private final HudManager m = HudManager.get();
    private final Runnable onClose;
    private final boolean editorMode;    // true = Editor (chỉ kéo thả, không sidebar)
    private final List<ButtonWidget> buttons = new ArrayList<>();

    private HudWidget dragging;
    private HudWidget linkFrom;
    private int linkDir;                 // +1: kéo điểm dưới (linkFrom = cha), -1: kéo điểm trên (linkFrom = con)
    private double dragAccX, dragAccY;

    /** Manager: sidebar bật/tắt module + nút mở Editor. */
    public HudEditorScreen(Runnable onClose) { this(false, onClose); }

    public HudEditorScreen(boolean editorMode, Runnable onClose) {
        super(Text.literal(editorMode ? "HUD Editor" : "HUD Manager"));
        this.editorMode = editorMode;
        this.onClose = onClose;
    }

    @Override
    protected void init() {
        buttons.clear();
        if (editorMode) return;          // Editor: không sidebar, không nút -> không chắn vùng kéo

        int bx = this.width - SIDEBAR + 8;
        int bw = SIDEBAR - 16 - 28;
        ButtonWidget glob = ButtonWidget.builder(Text.literal("Global settings"), btn -> {
            m.seedGlobal();
            this.client.setScreen(new HudSettingsScreen(m.global, this));
        }).dimensions(bx, 32, SIDEBAR - 16, 18).build();
        buttons.add(glob);
        addDrawableChild(glob);

        int n = m.size();
        int top = 54, bottom = this.height - 74;
        int rowH = Math.min(CELL, Math.max(10, (bottom - top) / Math.max(1, n)));
        int bh = Math.min(18, rowH - 2);
        for (int i = 0; i < n; i++) {
            HudWidget w = m.get(i);
            int y = top + i * rowH;
            ButtonWidget b = ButtonWidget.builder(toggleText(w), btn -> {
                w.enabled = !w.enabled;
                btn.setMessage(toggleText(w));
                CustomHudOverlay.save();   // lưu ngay, khỏi phụ thuộc thứ tự đóng màn
            }).dimensions(bx, y, bw, bh).build();
            buttons.add(b);
            addDrawableChild(b);
            ButtonWidget gear = ButtonWidget.builder(Text.literal("Set"), btn ->
                    this.client.setScreen(new HudSettingsScreen(w, this)))
                    .dimensions(bx + bw + 2, y, 26, bh).build();
            buttons.add(gear);
            addDrawableChild(gear);
        }
        ButtonWidget editor = ButtonWidget.builder(Text.literal("Editor (move HUD)"), b ->
                        this.client.setScreen(new HudEditorScreen(true, onClose)))
                .dimensions(this.width - SIDEBAR + 8, this.height - 70, SIDEBAR - 16, 18).build();
        buttons.add(editor);
        addDrawableChild(editor);
        ButtonWidget reset = ButtonWidget.builder(Text.literal("Reset layout"), b -> { m.autoLayout(); CustomHudOverlay.save(); })
                .dimensions(this.width - SIDEBAR + 8, this.height - 48, SIDEBAR - 16, 18).build();
        buttons.add(reset);
        addDrawableChild(reset);
    }

    private static Text toggleText(HudWidget w) {
        return Text.literal((w.enabled ? "[ON]   " : "[OFF]  ") + w.label);
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (input.getKeycode() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { this.close(); return true; }
        return super.keyPressed(input);
    }

    @Override public void close() {
        if (onClose != null) onClose.run();
        super.close();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        m.screenW = this.width;
        m.screenH = this.height;

        // nền tối nhẹ, vẫn thấy thế giới phía sau (Editor nhạt hơn để thấy HUD rõ)
        ctx.fill(0, 0, this.width, this.height, editorMode ? 0x55000000 : 0x88000000);

        var font = this.textRenderer;
        for (int i = 0; i < m.size(); i++) {
            HudWidget w = m.get(i);
            if (w.function || w.center) continue;
            w.update(delta);
            w.prepare(font);
        }
        m.placeGroups();
        for (int i = 0; i < m.size(); i++) {
            HudWidget w = m.get(i);
            if (w.function || w.center) continue;
            if (w.enabled) w.render(ctx, font);
            drawWidgetOverlay(ctx, w, mouseX, mouseY);
        }
        drawLinkLine(ctx, mouseX, mouseY);

        if (editorMode) {
            ctx.drawTextWithShadow(font, Text.literal("HUD Editor - drag to move | right-click: unlink | Esc: save & exit"),
                    6, this.height - 14, 0xFFB0B6BD);
            return;
        }

        // sidebar bên phải (vẽ sau cùng cho nổi trên vùng kéo)
        int px = this.width - SIDEBAR;
        ctx.fill(px, 0, this.width, this.height, 0xFF141418);
        ctx.fill(px - 1, 0, px, this.height, 0x40FFFFFF);
        ctx.drawTextWithShadow(font, this.title, px + 8, 12, 0xFFFFFFFF);
        ctx.drawTextWithShadow(font, Text.literal("Right Shift: close & save"), 6, this.height - 14, 0xFF9AA0A6);

        for (int i = 0; i < buttons.size(); i++) buttons.get(i).render(ctx, mouseX, mouseY, delta);
    }

    private void drawWidgetOverlay(DrawContext ctx, HudWidget w, int mouseX, int mouseY) {
        int color = w.enabled ? 0x99FFFFFF : 0x66FF7777;
        ctx.fill(w.x, w.y, w.x + w.width, w.y + 1, color);
        ctx.fill(w.x, w.y + w.height - 1, w.x + w.width, w.y + w.height, color);
        ctx.fill(w.x, w.y, w.x + 1, w.y + w.height, color);
        ctx.fill(w.x + w.width - 1, w.y, w.x + w.width, w.y + w.height, color);
        if (!w.enabled) return;

        int cx = w.x + w.width / 2;
        boolean topHot = over(mouseX, mouseY, cx, w.y);
        boolean botHot = over(mouseX, mouseY, cx, w.y + w.height);
        drawPoint(ctx, cx, w.y, topHot ? 0xFFFFD54A : 0xFF4DA3FF);
        drawPoint(ctx, cx, w.y + w.height, botHot ? 0xFFFFD54A : 0xFFFF8A3D);
    }

    private void drawPoint(DrawContext ctx, int x, int y, int color) {
        ctx.fill(x - 3, y - 3, x + 3, y + 3, 0xFF101014);
        ctx.fill(x - 2, y - 2, x + 2, y + 2, color);
    }

    private void drawLinkLine(DrawContext ctx, int mouseX, int mouseY) {
        if (linkFrom == null) return;
        int cx = linkFrom.x + linkFrom.width / 2;
        int cy = linkDir > 0 ? linkFrom.y + linkFrom.height : linkFrom.y;
        int steps = 24;
        for (int i = 0; i <= steps; i++) {
            int px = cx + (mouseX - cx) * i / steps;
            int py = cy + (mouseY - cy) * i / steps;
            ctx.fill(px, py, px + 1, py + 1, 0xFFFFD54A);
        }
    }

    private static boolean over(int mx, int my, int px, int py) {
        return Math.abs(mx - px) <= POINT + 2 && Math.abs(my - py) <= POINT + 2;
    }

    private HudWidget widgetUnder(double mx, double my, HudWidget except) {
        for (int i = m.size() - 1; i >= 0; i--) {
            HudWidget w = m.get(i);
            if (w == except || w.function || w.center) continue;
            if (mx >= w.x && mx <= w.x + w.width && my >= w.y && my <= w.y + w.height) return w;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        if (click.button() == 1) {                    // chuột phải: tách module khỏi nhóm
            HudWidget hit = widgetUnder(click.x(), click.y(), null);
            if (hit != null && hit.enabled && hit.group != -1) { m.unlink(hit); return true; }
            return false;
        }
        if (click.button() != 0) return false;
        double mx = click.x(), my = click.y();

        for (int i = m.size() - 1; i >= 0; i--) {
            HudWidget w = m.get(i);
            if (!w.enabled || w.function || w.center) continue;
            int cx = w.x + w.width / 2;
            if (over((int) mx, (int) my, cx, w.y)) { linkFrom = w; linkDir = -1; return true; }
            if (over((int) mx, (int) my, cx, w.y + w.height)) { linkFrom = w; linkDir = 1; return true; }
        }
        HudWidget hit = widgetUnder(mx, my, null);
        if (hit != null && hit.enabled) {
            dragging = hit;
            dragAccX = 0; dragAccY = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (super.mouseDragged(click, dx, dy)) return true;
        if (dragging != null) {
            dragAccX += dx; dragAccY += dy;
            int ix = (int) dragAccX, iy = (int) dragAccY;
            if (ix != 0 || iy != 0) {
                m.move(dragging, ix, iy);
                dragAccX -= ix; dragAccY -= iy;
            }
            return true;
        }
        return linkFrom != null;
    }

    @Override
    public boolean mouseReleased(Click click) {
        super.mouseReleased(click);
        if (dragging != null) { dragging = null; CustomHudOverlay.save(); return true; }
        if (linkFrom != null) {
            HudWidget target = widgetUnder(click.x(), click.y(), linkFrom);
            if (target != null && target.enabled) {
                if (linkDir > 0) m.link(linkFrom, target);   // linkFrom ở trên, target ở dưới
                else m.link(target, linkFrom);               // target ở trên, linkFrom ở dưới
            } else if (linkDir < 0) {
                m.unlink(linkFrom);                          // kéo điểm trên ra vùng trống => tách nhóm
            }
            linkFrom = null;
            CustomHudOverlay.save();
            return true;
        }
        return false;
    }
}
