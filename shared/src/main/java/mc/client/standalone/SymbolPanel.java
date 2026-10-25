package mc.client.standalone;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Ô chọn symbol nằm cạnh ô chat (mở khi bấm T hoặc /).
 * Có tab theo nhóm, bấm symbol để chèn vào ô chat, cuộn chuột để xem thêm, Esc để đóng.
 */
public class SymbolPanel extends ClickableWidget {
    private static final int CELL = 20;
    private static final int COLS = 10;
    private static final int ROWS = 8;      // 6 -> 8 (+33% chiều cao lưới)
    private static final int PAD = 4;
    private static final int BTN = 20;
    private static final int TABH = 14;
    private static final int TABCOLS = 3;   // tab rộng hơn -> tên nhóm không bị cắt
    private static final String SEC = "\u00A7";

    /** Tab "Color": 16 m\u00E0u c\u00F3 s\u1EB5n c\u1EE7a game (§0-§f) + c\u00E1c m\u00E3 format, b\u1EA5m l\u00E0 ch\u00E8n m\u00E3 v\u00E0o \u00F4 chat. */
    private static final int[] COLOR_RGB = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
        0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
        0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };
    private static final String[] FORMAT_LABELS = { "B", "I", "U", "S", "K", "R" };
    private static final List<String> COLOR_TAB = buildColorTab();

    private static List<String> buildColorTab() {
        List<String> l = new ArrayList<String>();
        String hex = "0123456789abcdef";
        for (int i = 0; i < 16; i++) l.add(SEC + hex.substring(i, i + 1));
        String[] fmt = { "l", "o", "n", "m", "k", "r" };
        for (int i = 0; i < fmt.length; i++) l.add(SEC + fmt[i]);
        return l;
    }

    private final TextFieldWidget field;
    private boolean open;
    private int tab;
    private int scroll;

    private int btnX, btnY, panelX, panelY, panelW, panelH, gridTop, tabRows;

    public SymbolPanel(TextFieldWidget field) {
        super(0, 0, BTN, BTN, Text.literal("Symbols"));
        this.field = field;
        layout();
    }

    public boolean isOpen() { return open; }

    public void close() { open = false; layout(); }

    /** Panel không nhận focus: phím mũi tên phải ở lại ô chat, không nhảy focus sang đây. */
    @Override
    public ScreenRect getNavigationFocus() { return ScreenRect.empty(); }

    /** Bấm panel không cướp focus khỏi ô chat. */
    @Override
    public boolean isClickable() { return false; }

    private List<SymbolStore.Category> cats() { return SymbolStore.get(); }

    private int colorTab() { return cats().size(); }

    private int tabCount() { return cats().size() + 1; }

    private boolean isColorTab() { return tab == colorTab(); }

    private int cols() {
        if (isColorTab()) return COLS;
        List<SymbolStore.Category> c = cats();
        if (c.isEmpty()) return COLS;
        return c.get(Math.min(tab, c.size() - 1)).lineMode ? 1 : COLS;
    }

    private List<String> symbols() {
        if (isColorTab()) return COLOR_TAB;
        List<SymbolStore.Category> c = cats();
        if (c.isEmpty()) return java.util.Collections.emptyList();
        return c.get(Math.min(tab, c.size() - 1)).symbols;
    }

    private void layout() {
        int fx = field.getX();
        int fw = field.getWidth();
        int fy = field.getY();

        btnX = fx + fw - BTN;
        btnY = fy - BTN - 2;

        tabRows = Math.max(1, (tabCount() + TABCOLS - 1) / TABCOLS);
        panelW = COLS * CELL + PAD * 2;
        panelH = tabRows * TABH + 4 + ROWS * CELL + PAD;
        panelX = fx + fw - panelW;
        panelY = btnY - panelH - 2;
        if (panelY < 2) panelY = 2;
        if (panelX < 2) panelX = 2;
        gridTop = panelY + tabRows * TABH + 2;

        if (open) {
            int ux = Math.min(panelX, btnX);
            int uy = Math.min(panelY, btnY);
            // setDimensionsAndPosition(width, height, x, y) - khong phai (x, y, w, h)
            setDimensionsAndPosition(
                Math.max(panelX + panelW, btnX + BTN) - ux,
                Math.max(panelY + panelH, btnY + BTN) - uy,
                ux, uy);
        } else {
            setDimensionsAndPosition(BTN, BTN, btnX, btnY);
        }
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        layout();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (open) drawPanel(ctx, mc, mouseX, mouseY);
        drawButton(ctx, mc, mouseX, mouseY);
    }

    private void drawButton(DrawContext ctx, MinecraftClient mc, int mouseX, int mouseY) {
        boolean hov = mouseX >= btnX && mouseX < btnX + BTN && mouseY >= btnY && mouseY < btnY + BTN;
        ctx.fill(btnX, btnY, btnX + BTN, btnY + BTN, hov ? 0xFF3A3A42 : 0xFF26262C);
        ctx.fill(btnX, btnY, btnX + BTN, btnY + 2, 0xFFF59E0B);
        ctx.drawCenteredTextWithShadow(mc.textRenderer, open ? "\u00D7" : "\u263A", btnX + BTN / 2, btnY + 6, 0xFFFFFFFF);
    }

    private void drawPanel(DrawContext ctx, MinecraftClient mc, int mouseX, int mouseY) {
        ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFFF59E0B);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF01C1C22);

        List<SymbolStore.Category> all = cats();
        int tabW = panelW / TABCOLS;

        for (int i = 0; i < tabCount(); i++) {
            int tx = panelX + (i % TABCOLS) * tabW;
            int ty = panelY + (i / TABCOLS) * TABH;
            boolean sel = i == tab;
            boolean hov = mouseX >= tx && mouseX < tx + tabW && mouseY >= ty && mouseY < ty + TABH;
            ctx.fill(tx, ty, tx + tabW - 1, ty + TABH - 1, sel ? 0xFFF59E0B : (hov ? 0xFF3A3A42 : 0xFF2A2A31));
            int count = (i == colorTab()) ? COLOR_TAB.size() : all.get(i).symbols.size();
            String cnt = Integer.toString(count);
            String label = (i == colorTab()) ? "Color" : all.get(i).name;
            // tên đầy đủ (không cắt) + số symbol bên phải, tách nhau bằng khoảng trống
            label = mc.textRenderer.trimToWidth(label, tabW - 4 - mc.textRenderer.getWidth(cnt) - 4);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, label, tx + (tabW - 1) / 2 - (mc.textRenderer.getWidth(cnt) + 3) / 2,
                    ty + 3, sel ? 0xFF1C1C22 : 0xFFDDDDDD);
            ctx.drawTextWithShadow(mc.textRenderer, cnt, tx + tabW - 4 - mc.textRenderer.getWidth(cnt), ty + 3,
                    sel ? 0xCC1C1C22 : 0xFF7A7F88);
        }

        List<String> list = symbols();
        int cols = cols();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = scroll + r * cols + c;
                if (idx >= list.size()) return;
                int cx = panelX + PAD + c * CELL;
                int cy = gridTop + r * CELL;
                boolean hov = mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL;
                if (hov) ctx.fill(cx, cy, cx + CELL, cy + CELL, 0xFFFFF3D0);
                String s = list.get(idx);
                if (isColorTab() && idx < 16) {
                    // o mau: vien xam de mau toi (den/xam) van thay
                    ctx.fill(cx + 2, cy + 2, cx + CELL - 2, cy + 3, 0xFF888888);
                    ctx.fill(cx + 2, cy + CELL - 3, cx + CELL - 2, cy + CELL - 2, 0xFF888888);
                    ctx.fill(cx + 2, cy + 2, cx + 3, cy + CELL - 2, 0xFF888888);
                    ctx.fill(cx + CELL - 3, cy + 2, cx + CELL - 2, cy + CELL - 2, 0xFF888888);
                    ctx.fill(cx + 3, cy + 3, cx + CELL - 3, cy + CELL - 3, 0xFF000000 | COLOR_RGB[idx]);
                } else if (isColorTab()) {
                    ctx.drawCenteredTextWithShadow(mc.textRenderer, FORMAT_LABELS[idx - 16], cx + CELL / 2, cy + 6, 0xFFFFFFFF);
                } else if (cols == 1) {
                    ctx.drawTextWithShadow(mc.textRenderer, mc.textRenderer.trimToWidth(s, panelW - PAD * 2), cx + 2, cy + 6, 0xFFFFFFFF);
                } else {
                    ctx.drawCenteredTextWithShadow(mc.textRenderer, s, cx + CELL / 2, cy + 6, 0xFFFFFFFF);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return false;
        double mx = click.x();
        double my = click.y();

        if (mx >= btnX && mx < btnX + BTN && my >= btnY && my < btnY + BTN) {
            open = !open;
            layout();
            if (open) field.setFocused(true);
            return true;
        }

        if (open && mx >= panelX && mx < panelX + panelW && my >= panelY && my < panelY + panelH) {
            // tab
            if (my < panelY + tabRows * TABH) {
                int tabW = panelW / TABCOLS;
                int i = ((int) ((my - panelY) / TABH)) * TABCOLS + (int) ((mx - panelX) / tabW);
                if (i >= 0 && i < tabCount()) {
                    tab = i;
                    scroll = 0;
                }
                return true;
            }
            // grid
            int cols = cols();
            int c = (int) ((mx - panelX - PAD) / CELL);
            int r = (int) ((my - gridTop) / CELL);
            List<String> list = symbols();
            int idx = scroll + r * cols + c;
            if (c >= 0 && c < cols && r >= 0 && r < ROWS && idx >= 0 && idx < list.size()) {
                String s = list.get(idx);
                // O mau hien bang ma § nhung chèn ra &x cho server/plugin d?ch mau.
                field.write(isColorTab() ? "&" + s.substring(1) : s);
                field.setFocused(true);
            }
            return true;
        }

        return false;
    }

    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (!open) return false;
        if (mx < panelX || mx >= panelX + panelW || my < gridTop || my >= panelY + panelH) return false;
        int cols = cols();
        int max = Math.max(0, symbols().size() - cols * ROWS);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(vertical) * cols));
        return true;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
