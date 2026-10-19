package mc.client.hud;

import java.util.function.BooleanSupplier;

import org.lwjgl.glfw.GLFW;


import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Màn setting của 1 module: đổi tên nhãn, màu chữ + đổ bóng, nền (bật/tắt/màu/alpha), xếp dọc.
 * Riêng Crosshair: lưới pixel 15x15 + màu + alpha + độ dày chấm.
 */
public final class HudSettingsScreen extends Screen {
    private static final int[] PALETTE = {
        0xFFFFFFFF, 0xFF000000, 0xFFFF5555, 0xFF55FF55, 0xFF5599FF,
        0xFFFFFF55, 0xFF55FFFF, 0xFFFF55FF, 0xFFFFA500, 0xFFAAAAAA,
    };
    private static final int SW = 16;        // kích thước ô màu
    private static final int HEXW = 90;      // bề rộng ô nhập hex
    private static final int EFFW = 130;     // bề rộng nút chọn hiệu ứng RGB
    private static final int CELLPX = 8;     // kích thước 1 chấm trong lưới crosshair
    private static final int GRID_Y = 24;
    private static final int ROW1_Y = 90;    // hàng màu chữ
    private static final int HEX_Y = 110;    // ô nhập hex màu chữ
    private static final int ROW2_Y = 136;   // hàng màu nền
    private static final int ALPHA_Y = 156;  // nút chỉnh độ trong nền
    private static final int OPACITY_Y = 176;
    private static final int OVERLAY_Y = 90; // hàng nội dung theo mode của Block Overlay

    private final HudWidget w;
    private final Screen parent;
    private final boolean isCrosshair;
    private final boolean isGlobal;      // true = setting chung, áp cho mọi module

    private TextFieldWidget labelField;
    private TextFieldWidget hexField;
    private int hexLabelX, hexX, effX, hexRowY;   // toạ độ hàng Hex + nút RGB effect

    private final boolean isColorBox;

    public HudSettingsScreen(HudWidget w, Screen parent) {
        super(Text.literal("Settings: " + w.label));
        this.w = w;
        this.parent = parent;
        this.isCrosshair = w.center;
        this.isGlobal = "global".equals(w.id);
        this.isColorBox = w instanceof ColorBoxWidget;
    }

    /** Áp giá trị chung lên mọi module (chỉ khi đang ở màn Global). */
    private void changed() { if (isGlobal) HudManager.get().applyGlobal(); }

    private Widgets.Crosshair ch() { return (Widgets.Crosshair) w; }

    @Override
    protected void init() {
        int cx = this.width / 2;
        if (isCrosshair) { initCrosshair(cx); return; }
        if (isColorBox) { initColorBox(cx); return; }

        if (!isGlobal) {
            labelField = new TextFieldWidget(this.textRenderer, cx - 110, 40, 220, 18, Text.literal("Label"));
            labelField.setMaxLength(24);
            labelField.setText(w.label);
            labelField.setChangedListener(s -> w.label = s.isBlank() ? w.id : s);
            addDrawableChild(labelField);
            setInitialFocus(labelField);
        }

        int n = isGlobal ? 2 : (w.id.equals("coords") ? 4 : 3);
        int bw = 96, gap = 6;
        int x = Math.max(8, (this.width - (n * bw + (n - 1) * gap)) / 2);
        int bx = x;
        if (!isGlobal) { toggle(bx, 66, bw, "Show label", () -> !w.hideLabel, () -> w.hideLabel = !w.hideLabel); bx += bw + gap; }
        toggle(bx, 66, bw, "Text shadow", () -> w.textShadow, () -> w.textShadow = !w.textShadow);
        bx += bw + gap;
        toggle(bx, 66, bw, "Background", () -> w.bgEnabled, () -> w.bgEnabled = !w.bgEnabled);
        bx += bw + gap;
        if (n == 4) toggle(bx, 66, bw, "Vertical", () -> w.vertical, () -> w.vertical = !w.vertical);

        addHexAndEffect(cx, HEX_Y, c -> w.textColor = 0xFF000000 | c, () -> w.textColor & 0xFFFFFF);

        addDrawableChild(ButtonWidget.builder(Text.literal("Alpha -"), b -> { bgAlpha(-32); changed(); })
                .dimensions(cx - 72, ALPHA_Y, 68, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Alpha +"), b -> { bgAlpha(32); changed(); })
                .dimensions(cx + 4, ALPHA_Y, 68, 18).build());
    }

    /** Hàng "Hex" + nút RGB effect dùng chung cho Text color và Crosshair color. */
    private void addHexAndEffect(int cx, int rowY, java.util.function.IntConsumer apply, java.util.function.IntSupplier current) {
        hexRowY = rowY;
        int hw = this.textRenderer.getWidth("Hex") + 4;
        int g0 = cx - (hw + HEXW + 10 + EFFW) / 2;
        hexLabelX = g0;
        hexX = g0 + hw;
        effX = hexX + HEXW + 10;

        hexField = new TextFieldWidget(this.textRenderer, hexX, rowY, HEXW, 18, Text.literal("Hex"));
        hexField.setMaxLength(7);
        hexField.setText(hexOf(current.getAsInt()));
        hexField.setChangedListener(s -> { Integer c = parseHex(s); if (c != null) { apply.accept(c); changed(); } });
        addDrawableChild(hexField);

        addDrawableChild(ButtonWidget.builder(effLabel(), b -> {
            w.effect = (w.effect + 1) % HudWidget.EFFECTS.length;
            changed();
            b.setMessage(effLabel());
        }).dimensions(effX, rowY, EFFW, 18).build());
    }

    private Text effLabel() {
        int e = Math.floorMod(w.effect, HudWidget.EFFECTS.length);
        return Text.literal("RGB: " + HudWidget.EFFECTS[e]);
    }

    private void bgAlpha(int d) {
        int a = Math.max(0, Math.min(255, (w.bgColor >>> 24) + d));
        w.bgColor = (a << 24) | (w.bgColor & 0x00FFFFFF);
    }

    private static String hexOf(int argb) { return String.format("%06X", argb & 0xFFFFFF); }

    private static Integer parseHex(String s) {
        String h = s == null ? "" : s.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() != 6) return null;
        try { return Integer.parseInt(h, 16); } catch (NumberFormatException e) { return null; }
    }

    private TextFieldWidget thickField;

    private ColorBoxWidget bo() { return (ColorBoxWidget) w; }

    private void initColorBox(int cx) {
        ColorBoxWidget bo = bo();
        int bw = 96, gap = 6, y = 50;
        // 3 nút mode màu
        addDrawableChild(ButtonWidget.builder(colorModeLabel(0), b -> { bo.colorMode = 0; refreshColorButtons(); }).dimensions(cx - 150, y, bw, 18).build());
        addDrawableChild(ButtonWidget.builder(colorModeLabel(1), b -> { bo.colorMode = 1; refreshColorButtons(); }).dimensions(cx - 150 + bw + gap, y, bw, 18).build());
        addDrawableChild(ButtonWidget.builder(colorModeLabel(2), b -> { bo.colorMode = 2; refreshColorButtons(); }).dimensions(cx - 150 + (bw + gap) * 2, y, bw, 18).build());
        // Nội dung theo mode: Game = bảng màu, HEX = ô nhập mã, RGB = chọn hiệu ứng
        if (bo.colorMode == 1) addColorBoxHex(cx, OVERLAY_Y);
        else if (bo.colorMode == 2) addColorBoxEffect(cx, OVERLAY_Y);
        // Thickness slider: Block Overlay 10–100px, Hitbox 0–100px (bước 0.25)
        float min = bo.minThickness;
        int sliderW = 220;
        addDrawableChild(new net.minecraft.client.gui.widget.SliderWidget(cx - sliderW / 2, 130, sliderW, 18,
                Text.literal("Thickness: " + bo.thickness + "px"),
                Math.max(0f, Math.min(1f, (bo.thickness - min) / (100f - min)))) {
            @Override protected void updateMessage() {
                float v = Math.round((min + (float) value * (100f - min)) * 4) / 4f;
                setMessage(Text.literal("Thickness: " + v + "px"));
            }
            @Override protected void applyValue() {
                bo.thickness = Math.round((min + (float) value * (100f - min)) * 4) / 4f;
                if (thickField != null) thickField.setText(String.valueOf(bo.thickness));
            }
        });
        // ô nhập tay
        thickField = new TextFieldWidget(this.textRenderer, cx - 40, 155, 80, 18, Text.literal("px"));
        thickField.setMaxLength(6);
        thickField.setText(String.valueOf(bo.thickness));
        thickField.setChangedListener(s -> {
            try { float v = Float.parseFloat(s);
                if (v >= min && v <= 100f) bo.thickness = Math.round(v * 4) / 4f;
            } catch (NumberFormatException ignored) {}
        });
        addDrawableChild(thickField);
    }

    private void refreshColorButtons() { this.client.setScreen(new HudSettingsScreen(w, parent)); }

    private Text colorModeLabel(int mode) {
        String[] names = {"Game", "HEX", "RGB"};
        return Text.literal((bo().colorMode == mode ? "► " : "") + names[mode]);
    }
    /** Mode HEX: chỉ 1 ô nhập mã màu. */
    private void addColorBoxHex(int cx, int rowY) {
        hexRowY = rowY;
        int hw = this.textRenderer.getWidth("Hex") + 4;
        hexLabelX = cx - (hw + HEXW) / 2;
        hexX = hexLabelX + hw;
        hexField = new TextFieldWidget(this.textRenderer, hexX, rowY, HEXW, 18, Text.literal("Hex"));
        hexField.setMaxLength(7);
        hexField.setText(hexOf(bo().lineColor));
        hexField.setChangedListener(s -> { Integer c = parseHex(s); if (c != null) bo().lineColor = 0xFF000000 | c; });
        addDrawableChild(hexField);
    }

    /** Mode RGB: chỉ nút chọn hiệu ứng (dùng chung HudWidget.EFFECTS với HUD). */
    private void addColorBoxEffect(int cx, int rowY) {
        hexRowY = rowY;
        effX = cx - EFFW / 2;
        addDrawableChild(ButtonWidget.builder(effLabel(), b -> {
            bo().effect = (bo().effect + 1) % HudWidget.EFFECTS.length;
            b.setMessage(effLabel());
        }).dimensions(effX, rowY, EFFW, 18).build());
    }

    private void drawColorBoxSettings(DrawContext ctx, TextRenderer font) {
        ColorBoxWidget bo = bo();
        if (bo.colorMode == 0) {
            drawRow(ctx, font, "Color", OVERLAY_Y, bo.lineColor | 0xFF000000);
        } else if (bo.colorMode == 1) {
            ctx.drawTextWithShadow(font, Text.literal("Hex"), hexLabelX, hexRowY + 5, 0xFF9AA0A6);
        } else {
            ctx.drawTextWithShadow(font, Text.literal("Effect"), effX - font.getWidth("Effect") - 8, hexRowY + 5, 0xFF9AA0A6);
        }
    }

    private void initCrosshair(int cx) {
        int y = GRID_Y + Widgets.Crosshair.N * CELLPX + 10;
        int x = cx - 150;
        addDrawableChild(ButtonWidget.builder(Text.literal("Clear"), b -> ch().fill(false))
                .dimensions(x, y, 70, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cross"), b -> ch().presetCross())
                .dimensions(x + 76, y, 60, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Dot"), b -> ch().presetDot())
                .dimensions(x + 142, y, 50, 18).build());

        y += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Alpha -"), b -> alpha(-32))
                .dimensions(x, y, 70, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Alpha +"), b -> alpha(32))
                .dimensions(x + 76, y, 70, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Dot -"), b -> ch().dot = Math.max(1, ch().dot - 1))
                .dimensions(x + 160, y, 60, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Dot +"), b -> ch().dot = Math.min(4, ch().dot + 1))
                .dimensions(x + 226, y, 60, 18).build());

        addHexAndEffect(cx, this.height - 66,
                c -> ch().color = (ch().color & 0xFF000000) | c,
                () -> ch().color & 0xFFFFFF);
    }

    private void alpha(int d) {
        int a = Math.max(0, Math.min(255, (ch().color >>> 24) + d));
        ch().color = (a << 24) | (ch().color & 0x00FFFFFF);
    }

    private void toggle(int x, int y, int bw, String name, BooleanSupplier get, Runnable flip) {
        addDrawableChild(ButtonWidget.builder(txt(name, get.getAsBoolean()), b -> {
            flip.run();
            changed();
            b.setMessage(txt(name, get.getAsBoolean()));
        }).dimensions(x, y, bw, 18).build());
    }

    private static Text txt(String name, boolean on) {
        return Text.literal(name + ": " + (on ? "ON" : "OFF"));
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (isColorBox && thickField != null && thickField.isFocused()
                && input.getKeycode() == GLFW.GLFW_KEY_ENTER) { refreshColorButtons(); return true; }
        if (input.getKeycode() == GLFW.GLFW_KEY_ESCAPE) { this.close(); return true; }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        CustomHudOverlay.save();   // lưu ngay khi thoát màn setting, không đợi đóng HUD Editor
        if (parent != null) MinecraftClient.getInstance().setScreen(parent);
        else super.close();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        var font = this.textRenderer;
        int tw = font.getWidth(this.title);
        ctx.drawTextWithShadow(font, this.title, this.width / 2 - tw / 2, 12, 0xFFFFFFFF);
        if (isCrosshair) drawCrosshair(ctx, font);
        else if (isColorBox) drawColorBoxSettings(ctx, font);
        else drawTextSettings(ctx, font);
        ctx.drawTextWithShadow(font, Text.literal("Esc: back"), 8, this.height - 14, 0xFF9AA0A6);
    }

    private void drawTextSettings(DrawContext ctx, TextRenderer font) {
        if (isGlobal) {
            String h = "Applies to all modules";
            ctx.drawTextWithShadow(font, Text.literal(h), this.width / 2 - font.getWidth(h) / 2, 42, 0xFF9AA0A6);
        } else {
            ctx.drawTextWithShadow(font, Text.literal("Label (e.g. Coords -> Location):"), this.width / 2 - 110, 28, 0xFF9AA0A6);
        }
        drawRow(ctx, font, "Text color", ROW1_Y, w.textColor | 0xFF000000);
        ctx.drawTextWithShadow(font, Text.literal("Hex"), hexLabelX, hexRowY + 5, 0xFF9AA0A6);
        drawRow(ctx, font, "Background color", ROW2_Y, w.bgColor | 0xFF000000);
        String op = "Opacity: " + (w.bgColor >>> 24) + "/255";
        ctx.drawTextWithShadow(font, Text.literal(op), this.width / 2 - font.getWidth(op) / 2, OPACITY_Y, 0xFF9AA0A6);
    }

    /** Vẽ 1 hàng chọn màu kèm nhãn nằm ngay bên trái, thẳng hàng dọc với ô màu. */
    private void drawRow(DrawContext ctx, TextRenderer font, String label, int rowY, int selected) {
        int x = Math.max(8, swStart() - font.getWidth(label) - 8);
        ctx.drawTextWithShadow(font, Text.literal(label), x, rowY + (SW - font.fontHeight) / 2, 0xFF9AA0A6);
        drawSwatches(ctx, rowY, selected);
    }

    private void drawCrosshair(DrawContext ctx, TextRenderer font) {
        int gx = gridX(), size = Widgets.Crosshair.N * CELLPX;
        Widgets.Crosshair c = ch();
        for (int r = 0; r < Widgets.Crosshair.N; r++) {
            for (int col = 0; col < Widgets.Crosshair.N; col++) {
                int x0 = gx + col * CELLPX, y0 = GRID_Y + r * CELLPX;
                int fill = c.px[r][col] ? (c.color | 0xFF000000) : 0xFF24242A;
                ctx.fill(x0, y0, x0 + CELLPX - 1, y0 + CELLPX - 1, fill);
            }
        }
        ctx.fill(gx - 1, GRID_Y - 1, gx + size + 1, GRID_Y, 0xFF6A6A72);
        ctx.fill(gx - 1, GRID_Y + size, gx + size + 1, GRID_Y + size + 1, 0xFF6A6A72);
        int swY = this.height - 40;
        ctx.drawTextWithShadow(font, Text.literal("Hex"), hexLabelX, hexRowY + 5, 0xFF9AA0A6);
        drawRow(ctx, font, "Crosshair color", swY, c.color | 0xFF000000);
        ctx.drawTextWithShadow(font, Text.literal("Dot: " + c.dot + "px | Opacity: " + (c.color >>> 24) + "/255"),
                this.width / 2 - 90, this.height - 14, 0xFF9AA0A6);
    }

    private int gridX() { return (this.width - Widgets.Crosshair.N * CELLPX) / 2; }

    private int swStart() { return this.width / 2 - (PALETTE.length * (SW + 2)) / 2; }

    private int swX(int i) { return swStart() + i * (SW + 2); }

    private void drawSwatches(DrawContext ctx, int rowY, int selected) {
        for (int i = 0; i < PALETTE.length; i++) {
            int x = swX(i);
            ctx.fill(x - 1, rowY - 1, x + SW + 1, rowY + SW + 1, 0xFF101014);
            ctx.fill(x, rowY, x + SW, rowY + SW, PALETTE[i]);
            if ((PALETTE[i] & 0x00FFFFFF) == (selected & 0x00FFFFFF)) {
                ctx.fill(x - 2, rowY - 2, x + SW + 2, rowY - 1, 0xFFFFFFFF);
                ctx.fill(x - 2, rowY + SW + 1, x + SW + 2, rowY + SW + 2, 0xFFFFFFFF);
                ctx.fill(x - 2, rowY - 2, x - 1, rowY + SW + 2, 0xFFFFFFFF);
                ctx.fill(x + SW + 1, rowY - 2, x + SW + 2, rowY + SW + 2, 0xFFFFFFFF);
            }
        }
    }

    private int swatchHit(double mx, double my, int rowY) {
        if (my < rowY || my > rowY + SW) return -1;
        for (int i = 0; i < PALETTE.length; i++) {
            int x = swX(i);
            if (mx >= x && mx <= x + SW) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        double mx = click.x(), my = click.y();

        if (isCrosshair) {
            int gx = gridX(), size = Widgets.Crosshair.N * CELLPX;
            if (mx >= gx && mx < gx + size && my >= GRID_Y && my < GRID_Y + size) {
                int col = (int) ((mx - gx) / CELLPX), r = (int) ((my - GRID_Y) / CELLPX);
                ch().px[r][col] = click.button() == 0;
                return true;
            }
            int hit = swatchHit(mx, my, this.height - 40);
            if (hit >= 0) {
                ch().color = (ch().color & 0xFF000000) | (PALETTE[hit] & 0x00FFFFFF);
                if (hexField != null) hexField.setText(hexOf(ch().color));
                return true;
            }
            return false;
        }

        if (isColorBox) {
            // mode Game: bảng màu chọn lineColor
            if (bo().colorMode == 0) {
                int h = swatchHit(mx, my, OVERLAY_Y);
                if (h >= 0) { bo().lineColor = 0xFF000000 | (PALETTE[h] & 0x00FFFFFF); return true; }
            }
            return false;
        }

        int hit = swatchHit(mx, my, ROW1_Y);
        if (hit >= 0) {
            w.textColor = PALETTE[hit];
            if (hexField != null) hexField.setText(hexOf(w.textColor));
            changed();
            return true;
        }
        hit = swatchHit(mx, my, ROW2_Y);
        if (hit >= 0) { w.bgColor = (w.bgColor & 0xFF000000) | (PALETTE[hit] & 0x00FFFFFF); changed(); return true; }
        return false;
    }
}
