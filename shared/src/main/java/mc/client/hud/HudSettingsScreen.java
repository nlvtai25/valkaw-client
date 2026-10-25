package mc.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

import mc.client.standalone.Freelook;
import mc.client.standalone.Keybinds;

/**
 * Per-module HUD settings. One compact panel: section headers, switch rows,
 * colour palette, opacity/thickness sliders and text fields.
 */
public final class HudSettingsScreen extends Screen {
    private static final int[] PALETTE = {
        0xFFFFFFFF, 0xFF000000, 0xFFFF5555, 0xFF55FF55, 0xFF5599FF,
        0xFFFFFF55, 0xFF55FFFF, 0xFFFF55FF, 0xFFFFA500, 0xFFAAAAAA,
    };
    private static final int HEADER_H = 46;
    private static final int FOOTER_H = 36;
    private static final int PAD = 14;
    private static final int FIELD_H = 20;
    private static final int ROW_H = 28;
    private static final int SW = 18;
    private static final int SW_GAP = 4;
    private static final int CELL = 8;
    private static final int SECTION_H = 16;
    private static final int SECTION_GAP = 10;

    private final HudWidget w;
    private final Screen parent;
    private final boolean isCrosshair;
    private final boolean isGlobal;
    private final boolean isColorBox;
    private final boolean isFreelook;
    private final boolean isQuickChat;

    private TextFieldWidget labelField, hexField, dateField, timeField, qcField, thickField;
    private boolean awaitingKey;

    private final List<El> els = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private int scroll, contentH;
    private int pX, pY, pW, pH, cx, cw, contentTop, contentBottom;
    private int doneX, doneY, doneW, doneH;
    private Rect alphaSlider, thicknessSlider, gridRect;
    private boolean draggingAlpha, draggingThickness;

    public HudSettingsScreen(HudWidget w, Screen parent) {
        super(Text.literal("Settings: " + w.label));
        this.w = w;
        this.parent = parent;
        this.isCrosshair = w.center;
        this.isGlobal = "global".equals(w.id);
        this.isColorBox = w instanceof ColorBoxWidget;
        this.isFreelook = "freelook".equals(w.id);
        this.isQuickChat = "quickchat".equals(w.id);
    }

    private void changed() { if (isGlobal) HudManager.get().applyGlobal(); }

    private Widgets.Crosshair ch() { return (Widgets.Crosshair) w; }
    private ColorBoxWidget bo() { return (ColorBoxWidget) w; }

    @Override
    protected void init() {
        scroll = 0;
        els.clear();
        hits.clear();
        if (isCrosshair) initCrosshair();
        else if (isColorBox) initColorBox();
        else if (isFreelook) { /* key/view/crosshair rows are custom-drawn */ }
        else if (isQuickChat) initQuickChat();
        else initStandard();
        layout();
    }

    private void initStandard() {
        if (!isGlobal) {
            labelField = field("Label", 24, w.label, s -> w.label = s.isBlank() ? w.id : s);
            setInitialFocus(labelField);
        }
        if (w instanceof Widgets.Time t) {
            dateField = field("Date format", 32, t.dateFmt, s -> t.dateFmt = s);
            timeField = field("Time format", 32, t.timeFmt, s -> t.timeFmt = s);
        }
    }

    private void initCrosshair() {
        hexField = field("Hex", 7, hexOf(ch().color), s -> {
            Integer c = parseHex(s);
            if (c != null) ch().color = (ch().color & 0xFF000000) | c;
        });
    }

    private void initColorBox() {
        hexField = field("Hex", 7, hexOf(bo().lineColor), s -> {
            Integer c = parseHex(s);
            if (c != null) bo().lineColor = 0xFF000000 | c;
        });
        thickField = field("px", 6, String.valueOf(bo().thickness), s -> {
            try {
                float v = Float.parseFloat(s);
                if (v >= bo().minThickness && v <= 100f) bo().thickness = Math.round(v * 4) / 4f;
            } catch (NumberFormatException ignored) { }
        });
    }

    private void initQuickChat() {
        qcField = field("Message", 240, mc.client.standalone.QuickChat.message,
                s -> mc.client.standalone.QuickChat.message = s);
    }

    private TextFieldWidget field(String name, int max, String value, java.util.function.Consumer<String> listener) {
        TextFieldWidget f = new TextFieldWidget(this.textRenderer, 0, 0, 100, FIELD_H, Text.literal(name));
        f.setMaxLength(max);
        f.setText(value == null ? "" : value);
        f.setDrawsBackground(false);
        f.setTextShadow(false);
        f.setEditableColor(HudTheme.TEXT_PRIMARY);
        f.setPlaceholder(Text.literal(name));
        f.setChangedListener(listener);
        addDrawableChild(f);
        return f;
    }

    // ---- layout ----

    private int wantedHeight() {
        if (isCrosshair) return 410;
        if (isColorBox) return 330;
        if (isFreelook) return 260;
        if (isQuickChat) return 240;
        if (w instanceof Widgets.Time) return 500;
        if (w instanceof Widgets.Armor) return 460;
        if (isGlobal) return 370;
        return 440;
    }

    private int maxScroll() {
        return Math.max(0, contentH - Math.max(0, contentBottom - contentTop - PAD * 2));
    }

    private void layout() {
        pW = Math.min(380, Math.max(260, width - 24));
        pH = Math.min(Math.max(120, height - 16), wantedHeight());
        pY = Math.max(8, (height - pH) / 2);
        pX = Math.max(8, (width - pW) / 2);
        cx = pX + PAD;
        cw = pW - PAD * 2;
        contentTop = pY + HEADER_H;
        contentBottom = Math.max(contentTop + 24, pY + pH - FOOTER_H);
        if (scroll > maxScroll()) scroll = maxScroll();

        els.clear();
        hits.clear();
        alphaSlider = null;
        thicknessSlider = null;
        gridRect = null;

        int y = contentTop + PAD - scroll;
        if (isCrosshair) y = buildCrosshair(y);
        else if (isColorBox) y = buildColorBox(y);
        else if (isFreelook) y = buildFreelook(y);
        else if (isQuickChat) y = buildQuickChat(y);
        else y = buildStandard(y);
        contentH = (y + scroll) - (contentTop + PAD);

        doneW = 64;
        doneH = 20;
        doneX = pX + pW - PAD - doneW;
        doneY = pY + pH - FOOTER_H + 8;
        hits.add(new Hit(pX + 8, pY + 8, 24, HEADER_H - 8, this::close));
        hits.add(new Hit(doneX, doneY, doneW, doneH, this::close));
    }

    private int buildStandard(int y) {
        y = section("GENERAL", y);
        if (!isGlobal) {
            addCaption("LABEL", cx, y);
            y += 10;
            addFieldBg(labelField, cx, y, cw, FIELD_H, true);
            y += FIELD_H + 10;
        } else {
            addHint("Changes apply to every module.", cx, y);
            y += 14;
        }
        if (!isGlobal) y = toggle("Show label", !w.hideLabel, () -> w.hideLabel = !w.hideLabel, y);
        y = toggle("Text shadow", w.textShadow, () -> w.textShadow = !w.textShadow, y);
        y = toggle("Background", w.bgEnabled, () -> w.bgEnabled = !w.bgEnabled, y);
        if ("coords".equals(w.id)) y = toggle("Vertical", w.vertical, () -> w.vertical = !w.vertical, y);
        if (w instanceof Widgets.Armor) {
            y = toggle("Split armor", Widgets.Armor.split, () -> Widgets.Armor.split = !Widgets.Armor.split, y);
        }

        y += SECTION_GAP;
        y = section("TEXT COLOR", y);
        y = palette(w.textColor, c -> { w.textColor = 0xFF000000 | c; changed(); }, y);
        y += 6;
        y = hexEffectRow(y, () -> {
            w.effect = (w.effect + 1) % HudWidget.EFFECTS.length;
            changed();
        });

        y += SECTION_GAP;
        y = section("BACKGROUND", y);
        y = palette(w.bgColor, c -> { w.bgColor = (w.bgColor & 0xFF000000) | c; changed(); }, y);
        y += 6;
        y = alphaSlider(y);

        if (w instanceof Widgets.Armor a) {
            y += SECTION_GAP;
            y = section("DURABILITY", y);
            y = palette(a.durabilityColor, c -> a.durabilityColor = 0xFF000000 | c, y);
        }
        if (w instanceof Widgets.Time) {
            y += SECTION_GAP;
            y = section("FORMAT", y);
            int half = (cw - 8) / 2;
            addCaption("DATE", cx, y);
            addCaption("TIME", cx + half + 8, y);
            y += 10;
            addFieldBg(dateField, cx, y, half, FIELD_H, true);
            addFieldBg(timeField, cx + half + 8, y, half, FIELD_H, true);
            y += FIELD_H + 8;
            addHint("dd/MM/yyyy  |  HH:mm:ss", cx, y);
            y += 12;
        }
        return y;
    }

    private int buildCrosshair(int y) {
        Widgets.Crosshair c = ch();
        y = section("PATTERN", y);
        int size = Widgets.Crosshair.N * CELL;
        int gx = cx + (cw - size) / 2;
        final int gy = y;
        gridRect = new Rect(gx, gy, size, size);
        els.add(new El(gx, gy, size, size, (ctx, font, mx, my) -> drawGrid(ctx, font, gx, gy, size, mx, my)));
        y += size + 10;

        y = buttonRow(new String[]{"Clear", "Cross", "Dot"},
                new Runnable[]{() -> c.fill(false), c::presetCross, c::presetDot}, y);
        y = buttonRow(new String[]{"Alpha -", "Alpha +", "Dot -", "Dot +"},
                new Runnable[]{() -> alpha(-32), () -> alpha(32),
                        () -> c.dot = Math.max(1, c.dot - 1), () -> c.dot = Math.min(4, c.dot + 1)}, y);
        addHint("Dot " + c.dot + "px  |  Opacity " + (c.color >>> 24) + "/255", cx, y);
        y += 12;

        y += SECTION_GAP;
        y = section("COLOR", y);
        y = palette(c.color, col -> c.color = (c.color & 0xFF000000) | col, y);
        y += 6;
        y = hexEffectRow(y, () -> c.effect = (c.effect + 1) % HudWidget.EFFECTS.length);
        return y;
    }

    private int buildColorBox(int y) {
        ColorBoxWidget b = bo();
        if (hexField != null && b.colorMode != 1) {
            hexField.visible = false;
            hexField.setFocused(false);
        }
        y = section("MODE", y);
        int bw = (cw - 8) / 3;
        addFlatButton("Game", cx, y, bw, FIELD_H, b.colorMode == 0, () -> b.colorMode = 0);
        addFlatButton("HEX", cx + bw + 4, y, bw, FIELD_H, b.colorMode == 1, () -> b.colorMode = 1);
        addFlatButton("RGB", cx + (bw + 4) * 2, y, bw, FIELD_H, b.colorMode == 2, () -> b.colorMode = 2);
        y += FIELD_H + 8;

        y = section("COLOR", y);
        if (b.colorMode == 0) {
            y = palette(b.lineColor, c -> b.lineColor = 0xFF000000 | c, y);
        } else if (b.colorMode == 1) {
            addFieldBg(hexField, cx, y, cw, FIELD_H, true);
            y += FIELD_H + 8;
        } else {
            addFlatButton(effectLabel(), cx, y, cw, FIELD_H, false, () -> {
                b.effect = (b.effect + 1) % HudWidget.EFFECTS.length;
            });
            y += FIELD_H + 8;
        }

        y += SECTION_GAP;
        y = section("THICKNESS", y);
        y = thicknessSlider(y);
        addCaption("PX", cx, y);
        addFieldBg(thickField, cx + 24, y, 80, FIELD_H, true);
        y += FIELD_H + 8;
        return y;
    }

    private int buildFreelook(int y) {
        y = section("CONTROLS", y);
        y = actionRow("Key", awaitingKey ? "Press a key..." : keyName(), y, () -> awaitingKey = true);
        y = actionRow("View", Freelook.front ? "Front" : "Behind", y,
                () -> Freelook.front = !Freelook.front);
        y = toggle("Crosshair", Freelook.showCrosshair,
                () -> Freelook.showCrosshair = !Freelook.showCrosshair, y);
        addHint("Key can also be changed in Controls > Valkaw.", cx, y);
        y += 12;
        return y;
    }

    private int buildQuickChat(int y) {
        y = section("MESSAGE", y);
        addCaption("MESSAGE", cx, y);
        y += 10;
        addFieldBg(qcField, cx, y, cw, FIELD_H, true);
        y += FIELD_H + 10;
        y = actionRow("Key", awaitingKey ? "Press a key..." : keyName(), y, () -> awaitingKey = true);
        addHint("Prefix / to send as command.", cx, y);
        y += 12;
        return y;
    }

    private int section(String title, int y) {
        els.add(new El(cx, y, cw, SECTION_H, (ctx, font, mx, my) -> {
            ctx.fill(cx, y + 4, cx + 2, y + 11, HudTheme.ACCENT);
            ctx.drawTextWithShadow(font, Text.literal(title), cx + 8, y + 4, HudTheme.ACCENT_DIM);
        }));
        return y + SECTION_H;
    }

    private void addCaption(String text, int x, int y) {
        els.add(new El(x, y, cw, 10, (ctx, font, mx, my) ->
                ctx.drawTextWithShadow(font, Text.literal(text), x, y, HudTheme.TEXT_TERTIARY)));
    }

    private void addHint(String text, int x, int y) {
        els.add(new El(x, y, cw, 12, (ctx, font, mx, my) ->
                ctx.drawTextWithShadow(font, Text.literal(text), x, y, HudTheme.TEXT_TERTIARY)));
    }

    private int toggle(String label, boolean on, Runnable flip, int y) {
        final int yy = y;
        els.add(new El(cx, yy, cw, ROW_H, (ctx, font, mx, my) -> drawToggle(ctx, font, label, on, cx, yy, cw, mx, my)));
        hits.add(new Hit(cx, yy, cw, ROW_H, () -> { flip.run(); changed(); }));
        return yy + ROW_H;
    }

    private int palette(int selected, IntConsumer apply, int y) {
        final int yy = y;
        els.add(new El(cx, yy, cw, SW, (ctx, font, mx, my) -> drawPalette(ctx, selected, yy, mx, my)));
        int start = paletteStartX();
        for (int i = 0; i < PALETTE.length; i++) {
            final int idx = i;
            int sx = start + i * (SW + SW_GAP);
            hits.add(new Hit(sx, yy, SW, SW, () -> apply.accept(PALETTE[idx] & 0xFFFFFF)));
        }
        return yy + SW + 8;
    }

    private int hexEffectRow(int y, Runnable effectAction) {
        final int yy = y;
        els.add(new El(cx, yy, 30, FIELD_H, (ctx, font, mx, my) ->
                ctx.drawTextWithShadow(font, Text.literal("HEX"), cx, yy + 6, HudTheme.TEXT_SECONDARY)));
        addFieldBg(hexField, cx + 30, yy, 90, FIELD_H, true);
        int bx = cx + 128;
        int bw = Math.max(60, cw - 128);
        addFlatButton(effectLabel(), bx, yy, bw, FIELD_H, false, effectAction);
        return yy + FIELD_H + 10;
    }

    private int actionRow(String label, String value, int y, Runnable action) {
        final int yy = y;
        els.add(new El(cx, yy, cw, ROW_H, (ctx, font, mx, my) ->
                ctx.drawTextWithShadow(font, Text.literal(label), cx + 10, yy + (ROW_H - 8) / 2, HudTheme.TEXT_PRIMARY)));
        int bw = 110;
        addFlatButton(value, cx + cw - bw, yy + (ROW_H - FIELD_H) / 2, bw, FIELD_H,
                awaitingKey && "Key".equals(label), action);
        return yy + ROW_H;
    }

    private int buttonRow(String[] labels, Runnable[] actions, int y) {
        int n = labels.length;
        int gap = 6;
        int bw = (cw - (n - 1) * gap) / n;
        for (int i = 0; i < n; i++) {
            addFlatButton(labels[i], cx + i * (bw + gap), y, bw, FIELD_H, false, actions[i]);
        }
        return y + FIELD_H + 8;
    }

    private int alphaSlider(int y) {
        final int yy = y;
        alphaSlider = new Rect(cx, yy + 12, cw, 16);
        els.add(new El(cx, yy, cw, 30, (ctx, font, mx, my) -> drawAlphaSlider(ctx, font, yy)));
        return yy + 30;
    }

    private int thicknessSlider(int y) {
        final int yy = y;
        thicknessSlider = new Rect(cx, yy + 12, cw, 16);
        els.add(new El(cx, yy, cw, 30, (ctx, font, mx, my) -> drawThicknessSlider(ctx, font, yy)));
        return yy + 30;
    }

    private void addFlatButton(String text, int x, int y, int bw, int bh, boolean selected, Runnable action) {
        els.add(new El(x, y, bw, bh, (ctx, font, mx, my) ->
                drawFlatButton(ctx, font, text, x, y, bw, bh, selected, mx, my)));
        hits.add(new Hit(x, y, bw, bh, action));
    }

    private void addFieldBg(TextFieldWidget f, int x, int y, int bw, int bh, boolean allowed) {
        if (f == null) return;
        f.setDimensionsAndPosition(bw, bh, x, y);
        f.visible = allowed && y + bh > contentTop && y < contentBottom;
        if (!f.visible) {
            f.setFocused(false);
            return;
        }
        els.add(new El(x, y, bw, bh, (ctx, font, mx, my) -> {
            ctx.fill(x, y, x + bw, y + bh, 0xFF0E0E12);
            int border = f.isFocused() ? HudTheme.ACCENT : HudTheme.BORDER;
            ctx.fill(x, y, x + bw, y + 1, border);
            ctx.fill(x, y + bh - 1, x + bw, y + bh, border);
            ctx.fill(x, y, x + 1, y + bh, border);
            ctx.fill(x + bw - 1, y, x + bw, y + bh, border);
        }));
    }

    private int paletteStartX() {
        int total = PALETTE.length * SW + (PALETTE.length - 1) * SW_GAP;
        return cx + (cw - total) / 2;
    }

    // ---- drawing ----

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        layout();
        TextRenderer font = this.textRenderer;
        ctx.fill(0, 0, this.width, this.height, 0x99000000);
        drawPanelBody(ctx);
        drawHeader(ctx, font);

        ctx.fill(cx - 4, contentTop, cx + cw + 4, contentBottom, HudTheme.CARD_BG);
        ctx.enableScissor(cx, contentTop, cx + cw, contentBottom);
        for (El e : els) e.painter.draw(ctx, font, mouseX, mouseY);
        super.render(ctx, mouseX, mouseY, delta);
        ctx.disableScissor();

        drawScrollbar(ctx);
        drawFooter(ctx, font, mouseX, mouseY);
    }

    private void drawPanelBody(DrawContext ctx) {
        int x = pX, y = pY, w = pW, h = pH, r = HudTheme.RADIUS_PANEL;
        ctx.fill(x + r, y, x + w - r, y + h, HudTheme.PANEL_BG);
        ctx.fill(x, y + r, x + r, y + h - r, HudTheme.PANEL_BG);
        ctx.fill(x + w - r, y + r, x + w, y + h - r, HudTheme.PANEL_BG);
        for (int d = 0; d < 4; d++) ctx.fill(x + r - d, y + d, x + r + d, y + d, HudTheme.PANEL_BG);
        for (int d = 0; d < 4; d++) ctx.fill(x + w - r - d, y + d, x + w - r + d, y + d, HudTheme.PANEL_BG);
        ctx.fill(x, y + h - 1, x + w, y + h, HudTheme.PANEL_SHADOW);
    }

    private void drawHeader(DrawContext ctx, TextRenderer font) {
        ctx.fill(pX, pY, pX + pW, pY + HEADER_H, HudTheme.NAV_BG);
        ctx.fill(pX, pY + HEADER_H - 1, pX + pW, pY + HEADER_H, HudTheme.BORDER);
        ctx.drawTextWithShadow(font, Text.literal("<"), pX + 14, pY + 17, HudTheme.ACCENT);
        String title = isGlobal ? "Global Settings" : w.label;
        title = font.trimToWidth(title, pW - 80);
        ctx.drawTextWithShadow(font, Text.literal(title), pX + 34, pY + 12, HudTheme.TEXT_PRIMARY);
        String sub = isGlobal ? "APPLIES TO ALL MODULES" : "MODULE SETTINGS";
        ctx.drawTextWithShadow(font, Text.literal(sub), pX + 34, pY + 26, HudTheme.TEXT_TERTIARY);
    }

    private void drawFooter(DrawContext ctx, TextRenderer font, int mouseX, int mouseY) {
        ctx.fill(pX, pY + pH - FOOTER_H, pX + pW, pY + pH - FOOTER_H + 1, HudTheme.BORDER);
        String hint = awaitingKey ? "Press a key... Esc to cancel" : "Esc  Back";
        hint = font.trimToWidth(hint, pW - 100);
        ctx.drawTextWithShadow(font, Text.literal(hint), pX + PAD, pY + pH - 24,
                awaitingKey ? HudTheme.ACCENT : HudTheme.TEXT_SECONDARY);
        drawFlatButton(ctx, font, "Done", doneX, doneY, doneW, doneH, false, mouseX, mouseY);
    }

    private void drawScrollbar(DrawContext ctx) {
        if (maxScroll() <= 0) return;
        int barX = pX + pW - 8, barW = 4;
        int visible = contentBottom - contentTop;
        int thumbH = Math.max(16, (int) ((double) visible * visible / Math.max(1, contentH)));
        if (thumbH > visible - 4) thumbH = visible - 4;
        int thumbY = contentTop + (int) ((double) scroll / maxScroll() * (visible - thumbH));
        ctx.fill(barX, contentTop, barX + barW, contentBottom, HudTheme.CARD_BG);
        ctx.fill(barX, thumbY, barX + barW, thumbY + thumbH, HudTheme.ACCENT);
    }

    private void drawToggle(DrawContext ctx, TextRenderer font, String label, boolean on,
                            int x, int y, int bw, int mouseX, int mouseY) {
        boolean hov = inside(x, y, bw, ROW_H, mouseX, mouseY);
        if (hov) ctx.fill(x, y, x + bw, y + ROW_H, HudTheme.ROW_HOVER);
        ctx.drawTextWithShadow(font, Text.literal(label), x + 10, y + (ROW_H - 8) / 2, HudTheme.TEXT_PRIMARY);
        int tx = x + bw - 42, ty = y + (ROW_H - 14) / 2;
        ctx.fill(tx, ty, tx + 30, ty + 14, on ? (hov ? HudTheme.ACCENT_HOVER : HudTheme.ACCENT) : HudTheme.OFF);
        int kx = on ? tx + 16 : tx + 2;
        ctx.fill(kx, ty + 2, kx + 10, ty + 12, HudTheme.SWITCH_KNOB);
    }

    private void drawFlatButton(DrawContext ctx, TextRenderer font, String text,
                                int x, int y, int bw, int bh, boolean selected, int mouseX, int mouseY) {
        boolean hov = inside(x, y, bw, bh, mouseX, mouseY);
        int bg = selected ? HudTheme.ACCENT : (hov ? HudTheme.TAB_HOVER_BG : HudTheme.TAB_IDLE);
        int fg = selected ? 0xFF101014 : HudTheme.TEXT_PRIMARY;
        ctx.fill(x, y, x + bw, y + bh, bg);
        int border = selected ? HudTheme.ACCENT_HOVER : HudTheme.BORDER;
        ctx.fill(x, y, x + bw, y + 1, border);
        ctx.fill(x, y + bh - 1, x + bw, y + bh, border);
        ctx.fill(x, y, x + 1, y + bh, border);
        ctx.fill(x + bw - 1, y, x + bw, y + bh, border);
        String t = font.trimToWidth(text, bw - 8);
        ctx.drawTextWithShadow(font, Text.literal(t), x + (bw - font.getWidth(t)) / 2, y + (bh - 8) / 2, fg);
    }

    private void drawPalette(DrawContext ctx, int selected, int y, int mouseX, int mouseY) {
        int start = paletteStartX();
        for (int i = 0; i < PALETTE.length; i++) {
            int x = start + i * (SW + SW_GAP);
            boolean hov = inside(x, y, SW, SW, mouseX, mouseY);
            boolean sel = (PALETTE[i] & 0xFFFFFF) == (selected & 0xFFFFFF);
            if (sel) ctx.fill(x - 2, y - 2, x + SW + 2, y + SW + 2, HudTheme.ACCENT);
            else ctx.fill(x - 1, y - 1, x + SW + 1, y + SW + 1,
                    hov ? HudTheme.TEXT_SECONDARY : HudTheme.BORDER);
            ctx.fill(x, y, x + SW, y + SW, PALETTE[i]);
        }
    }

    private void drawAlphaSlider(DrawContext ctx, TextRenderer font, int y) {
        int value = w.bgColor >>> 24;
        ctx.drawTextWithShadow(font, Text.literal("Opacity"), cx, y + 2, HudTheme.TEXT_PRIMARY);
        String v = value + "/255";
        ctx.drawTextWithShadow(font, Text.literal(v), cx + cw - font.getWidth(v), y + 2, HudTheme.TEXT_SECONDARY);
        int ty = y + 18;
        ctx.fill(cx, ty, cx + cw, ty + 6, HudTheme.OFF);
        int fill = (int) ((double) cw * value / 255.0);
        ctx.fill(cx, ty, cx + fill, ty + 6, HudTheme.ACCENT);
        int kx = Math.max(cx + 4, Math.min(cx + cw - 4, cx + fill));
        ctx.fill(kx - 4, ty - 2, kx + 4, ty + 8, HudTheme.SWITCH_KNOB);
    }

    private void drawThicknessSlider(DrawContext ctx, TextRenderer font, int y) {
        ColorBoxWidget b = bo();
        float min = b.minThickness;
        float range = 100f - min;
        ctx.drawTextWithShadow(font, Text.literal("Thickness"), cx, y + 2, HudTheme.TEXT_PRIMARY);
        String v = b.thickness + "px";
        ctx.drawTextWithShadow(font, Text.literal(v), cx + cw - font.getWidth(v), y + 2, HudTheme.TEXT_SECONDARY);
        int ty = y + 18;
        ctx.fill(cx, ty, cx + cw, ty + 6, HudTheme.OFF);
        int fill = (int) ((double) cw * Math.max(0f, Math.min(1f, (b.thickness - min) / Math.max(1f, range))));
        ctx.fill(cx, ty, cx + fill, ty + 6, HudTheme.ACCENT);
        int kx = Math.max(cx + 4, Math.min(cx + cw - 4, cx + fill));
        ctx.fill(kx - 4, ty - 2, kx + 4, ty + 8, HudTheme.SWITCH_KNOB);
    }

    private void drawGrid(DrawContext ctx, TextRenderer font, int gx, int gy, int size, int mouseX, int mouseY) {
        Widgets.Crosshair c = ch();
        ctx.fill(gx - 2, gy - 2, gx + size + 2, gy + size + 2, 0xFF0E0E12);
        for (int r = 0; r < Widgets.Crosshair.N; r++) {
            for (int col = 0; col < Widgets.Crosshair.N; col++) {
                int x0 = gx + col * CELL, y0 = gy + r * CELL;
                int fill = c.px[r][col] ? (c.color | 0xFF000000) : 0xFF24242A;
                ctx.fill(x0, y0, x0 + CELL - 1, y0 + CELL - 1, fill);
            }
        }
        if (inside(gx, gy, size, size, mouseX, mouseY)) {
            int col = Math.max(0, Math.min(Widgets.Crosshair.N - 1, (mouseX - gx) / CELL));
            int row = Math.max(0, Math.min(Widgets.Crosshair.N - 1, (mouseY - gy) / CELL));
            int x0 = gx + col * CELL, y0 = gy + row * CELL;
            ctx.fill(x0, y0, x0 + CELL - 1, y0 + 1, HudTheme.ACCENT);
            ctx.fill(x0, y0 + CELL - 2, x0 + CELL - 1, y0 + CELL - 1, HudTheme.ACCENT);
            ctx.fill(x0, y0, x0 + 1, y0 + CELL - 1, HudTheme.ACCENT);
            ctx.fill(x0 + CELL - 2, y0, x0 + CELL - 1, y0 + CELL - 1, HudTheme.ACCENT);
        }
        int border = HudTheme.BORDER;
        ctx.fill(gx - 2, gy - 2, gx + size + 2, gy - 1, border);
        ctx.fill(gx - 2, gy + size + 1, gx + size + 2, gy + size + 2, border);
        ctx.fill(gx - 2, gy - 2, gx - 1, gy + size + 2, border);
        ctx.fill(gx + size + 1, gy - 2, gx + size + 2, gy + size + 2, border);
    }

    private static boolean inside(int x, int y, int w, int h, int mx, int my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---- input ----

    private String keyName() {
        var bind = isQuickChat ? Keybinds.QUICK_CHAT : Keybinds.FREELOOK;
        return bind.getBoundKeyLocalizedText().getString();
    }

    private String effectLabel() {
        int e = Math.floorMod(w.effect, HudWidget.EFFECTS.length);
        return "RGB: " + HudWidget.EFFECTS[e];
    }

    private void alpha(int d) {
        int a = Math.max(0, Math.min(255, (ch().color >>> 24) + d));
        ch().color = (a << 24) | (ch().color & 0x00FFFFFF);
    }

    private void setAlphaFromMouse(double mx) {
        if (alphaSlider == null) return;
        double t = (mx - alphaSlider.x()) / (double) Math.max(1, alphaSlider.w());
        t = Math.max(0, Math.min(1, t));
        int a = (int) Math.round(t * 255);
        w.bgColor = (a << 24) | (w.bgColor & 0x00FFFFFF);
        changed();
    }

    private void setThicknessFromMouse(double mx) {
        if (thicknessSlider == null) return;
        ColorBoxWidget b = bo();
        double t = (mx - thicknessSlider.x()) / (double) Math.max(1, thicknessSlider.w());
        t = Math.max(0, Math.min(1, t));
        float min = b.minThickness;
        float v = Math.round((min + (float) t * (100f - min)) * 4) / 4f;
        b.thickness = v;
        if (thickField != null) thickField.setText(String.valueOf(v));
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (awaitingKey) {
            int code = input.getKeycode();
            if (code == GLFW.GLFW_KEY_ESCAPE) {
                awaitingKey = false;
                return true;
            }
            if (isModifierKey(code)) return true;
            awaitingKey = false;
            var bound = InputUtil.Type.KEYSYM.createFromCode(code);
            if (isQuickChat) Keybinds.QUICK_CHAT.setBoundKey(bound);
            else Keybinds.FREELOOK.setBoundKey(bound);
            return true;
        }
        if (input.getKeycode() == GLFW.GLFW_KEY_ESCAPE) { this.close(); return true; }
        return super.keyPressed(input);
    }

    private static boolean isModifierKey(int code) {
        return code == GLFW.GLFW_KEY_LEFT_SHIFT || code == GLFW.GLFW_KEY_RIGHT_SHIFT
                || code == GLFW.GLFW_KEY_LEFT_CONTROL || code == GLFW.GLFW_KEY_RIGHT_CONTROL
                || code == GLFW.GLFW_KEY_LEFT_ALT || code == GLFW.GLFW_KEY_RIGHT_ALT
                || code == GLFW.GLFW_KEY_LEFT_SUPER || code == GLFW.GLFW_KEY_RIGHT_SUPER;
    }

    @Override
    public void close() {
        CustomHudOverlay.save();
        MinecraftClient.getInstance().options.write();
        if (parent != null) MinecraftClient.getInstance().setScreen(parent);
        else super.close();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (mx >= pX && mx <= pX + pW && my >= contentTop && my <= contentBottom && maxScroll() > 0) {
            scroll -= (int) Math.signum(vAmount) * 16;
            scroll = Math.max(0, Math.min(maxScroll(), scroll));
            return true;
        }
        return super.mouseScrolled(mx, my, hAmount, vAmount);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        layout();
        if (super.mouseClicked(click, doubled)) return true;
        double mx = click.x(), my = click.y();

        if (gridRect != null && gridRect.contains(mx, my)) {
            int col = Math.max(0, Math.min(Widgets.Crosshair.N - 1, (int) ((mx - gridRect.x()) / CELL)));
            int row = Math.max(0, Math.min(Widgets.Crosshair.N - 1, (int) ((my - gridRect.y()) / CELL)));
            ch().px[row][col] = click.button() == 0;
            return true;
        }
        if (click.button() != 0) return false;

        if (alphaSlider != null && alphaSlider.contains(mx, my)) {
            draggingAlpha = true;
            setAlphaFromMouse(mx);
            return true;
        }
        if (thicknessSlider != null && thicknessSlider.contains(mx, my)) {
            draggingThickness = true;
            setThicknessFromMouse(mx);
            return true;
        }
        for (Hit h : hits) {
            if (mx >= h.x() && mx < h.x() + h.w() && my >= h.y() && my < h.y() + h.h()) {
                h.action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (super.mouseDragged(click, dx, dy)) return true;
        if (draggingAlpha) { setAlphaFromMouse(click.x()); return true; }
        if (draggingThickness) { setThicknessFromMouse(click.x()); return true; }
        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingAlpha = false;
        draggingThickness = false;
        return super.mouseReleased(click);
    }

    private static String hexOf(int argb) { return String.format("%06X", argb & 0xFFFFFF); }

    private static Integer parseHex(String s) {
        String h = s == null ? "" : s.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() != 6) return null;
        try { return Integer.parseInt(h, 16); } catch (NumberFormatException e) { return null; }
    }

    private interface Painter {
        void draw(DrawContext ctx, TextRenderer font, int mouseX, int mouseY);
    }

    private static final class El {
        final int x, y, w, h;
        final Painter painter;
        El(int x, int y, int w, int h, Painter painter) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.painter = painter;
        }
    }

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }
}
