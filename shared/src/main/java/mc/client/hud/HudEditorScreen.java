package mc.client.hud;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import mc.client.standalone.DiscordRpc;
import mc.client.standalone.Keybinds;
import mc.client.standalone.Perf;
import mc.client.standalone.SkinStore;

/**
 * HUD Manager + Editor (kiểu Lunar/Badlion).
 * - Manager: panel nhỏ canh giữa màn hình, ô search + danh sách module chia nhóm,
 *   mỗi module có icon riêng (item vanilla theo nghĩa của module); bấm tên -> màn setting,
 *   bấm công tắc -> bật/tắt.
 * - Editor: kéo thả (lưới canh 16px); 2 điểm trên/dưới để nối module thành 1 khối,
 *   chuột phải để tách khỏi nhóm.
 * - Preset: lưu/đọc layout ra config/hud-presets/<tên>.cfg (dùng lại HudConfig).
 */
public final class HudEditorScreen extends Screen {
    private static final int GRID = 16;       // lưới canh trong Editor
    private static final int POINT = 4;

    private final ModulesTab modules = new ModulesTab();

    private final HudManager m = HudManager.get();
    private final Runnable onClose;
    private final Screen parent;
    private final boolean editorMode;

    // ---- widgets ----
    private TextFieldWidget searchField;
    private TextFieldWidget presetField;
    private TextFieldWidget skinField, capeField;
    private ButtonWidget editorButton;        // "Editor (move HUD)" footer, visible in all tabs
    // Khi kéo/tải lại: giữ thẳng hàng searchField + editorButton, không cuộn theo tab
    private List<ClickableWidget> fixedWidgets = new ArrayList<>();
    @SuppressWarnings("unchecked")
    final List<ClickableWidget>[] tabWidgets = new ArrayList[4];

    // ---- file picker shared by Skins & Options ----
    int filePick;                 // 0 = không mở, 1 = chọn skin, 2 = chọn cape, 3 = chọn profile (share)
    final List<String> fileList = new ArrayList<>();
    int fileScroll;

    // ---- tab / scroll / geometry ----
    int tab;                      // 0 Modules, 1 Options, 2 Perf, 3 Skins
    int scrollY;                  // shared scroll offset (pixels)
    int pX, pY, pW, pH;
    int listTop, listBottom, viewH;
    int rowH;                     // module row height (computed from screen size)
    String status = "";

    @SuppressWarnings("unchecked")
    final TabContent[] tabContent = new TabContent[4];

    private HudWidget dragging;
    private HudWidget linkFrom;
    private int linkDir;          // +1: kéo điểm dưới (linkFrom = cha), -1: kéo điểm trên (linkFrom = con)
    private double dragAccX, dragAccY;

    /** Manager: panel giữa màn hình + nút mở Editor. */
    public HudEditorScreen(Runnable onClose) { this(false, onClose, null); }

    /** Mở từ màn hình khác (vd. TitleScreen) để ESC quay lại đúng màn đó. */
    public HudEditorScreen(Runnable onClose, Screen parent) { this(false, onClose, parent); }

    public HudEditorScreen(boolean editorMode, Runnable onClose) { this(editorMode, onClose, null); }

    public HudEditorScreen(boolean editorMode, Runnable onClose, Screen parent) {
        super(Text.literal(editorMode ? "HUD Editor" : "Client Settings"));
        this.editorMode = editorMode;
        this.onClose = onClose;
        this.parent = parent;
    }

    /** Tính lại hình học panel theo kích thước màn hình hiện tại. */
    private void geometry() {
        int h = this.height;
        int rh = (h - 16 - HudTheme.TOP - HudTheme.BOT - ModulesTab.groupCount() * HudTheme.HDR_H) / HudTheme.MAX_ROWS;
        rowH = Math.max(HudTheme.ROW_MIN, Math.min(HudTheme.ROW_MAX, rh));
        pW = Math.min(HudTheme.PANEL_W, Math.max(280, this.width - 24));
        pH = HudTheme.TOP + (HudTheme.MAX_ROWS * rowH + ModulesTab.groupCount() * HudTheme.HDR_H) + HudTheme.BOT;
        if (pH > h - 16) pH = Math.max(180, h - 16);
        pX = Math.max(8, (this.width - pW) / 2);
        pY = Math.max(8, (h - pH) / 2);
        listTop = pY + HudTheme.TOP;
        listBottom = pY + pH - HudTheme.BOT;
        viewH = listBottom - listTop;
    }

    int contentX() { return pX + HudTheme.NAV_W + 12; }
    int contentW() { return pW - HudTheme.NAV_W - 24; }

    @Override
    protected void init() {
        for (int i = 0; i < 4; i++) {
            tabWidgets[i] = new ArrayList<>();
            tabContent[i] = new TabContent();
        }
        fixedWidgets.clear();
        filePick = 0;
        fileScroll = 0;
        scrollY = 0;
        geometry();
        if (editorMode) return;

        int fw = contentW();
        int bx = contentX();

        searchField = new TextFieldWidget(this.textRenderer, bx, pY + 86, fw, 16, Text.literal("Search"));
        searchField.setMaxLength(24);
        searchField.setChangedListener(s -> rebuild());
        addDrawableChild(searchField);
        fixedWidgets.add(searchField);

        createOptionsWidgets(bx, fw);
        createPerfWidgets(bx, fw);
        createSkinsWidgets(bx, fw);

        editorButton = add(ButtonWidget.builder(Text.literal("HUD Editor"), b ->
                        this.client.setScreen(new HudEditorScreen(true, onClose)))
                .dimensions(bx + fw - 120, pY + pH - 30, 120, HudTheme.CONTROL_H).build());
        fixedWidgets.add(editorButton);

        applyTab();
        rebuild();
    }

    // ---- widget creation (positions are recomputed each frame by layoutTabs) ----

    private void createOptionsWidgets(int bx, int fw) {
        List<ClickableWidget> list = tabWidgets[1];

        ButtonWidget reset = add(ButtonWidget.builder(Text.literal("Reset"), b -> {
            m.autoLayout();
            CustomHudOverlay.save();
            status = "layout reset";
        }).dimensions(bx, 0, 64, HudTheme.CONTROL_H).build());
        list.add(reset);

        ButtonWidget global = add(ButtonWidget.builder(Text.literal("Open"), b -> {
            m.seedGlobal();
            this.client.setScreen(new HudSettingsScreen(m.global, this));
        }).dimensions(bx, 0, 64, HudTheme.CONTROL_H).build());
        list.add(global);

        presetField = new TextFieldWidget(this.textRenderer, bx, 0, fw - 140, 16, Text.literal("Preset"));
        presetField.setMaxLength(20);
        presetField.setText(CustomHudOverlay.presetName().isEmpty() ? "default" : CustomHudOverlay.presetName());
        addDrawableChild(presetField);
        list.add(presetField);

        ButtonWidget save = add(ButtonWidget.builder(Text.literal("Save"), b -> presetSave())
                .dimensions(bx, 0, 64, HudTheme.CONTROL_H).build());
        list.add(save);

        ButtonWidget load = add(ButtonWidget.builder(Text.literal("Load"), b -> presetLoad())
                .dimensions(bx, 0, 64, HudTheme.CONTROL_H).build());
        list.add(load);

        ButtonWidget export = add(ButtonWidget.builder(Text.literal("Export"), b -> presetExport())
                .dimensions(bx, 0, 64, HudTheme.CONTROL_H).build());
        list.add(export);

        ButtonWidget imp = add(ButtonWidget.builder(Text.literal("Import"), b -> {
            filePick = 3;
            filesToPickProfiles();
        }).dimensions(bx, 0, 64, HudTheme.CONTROL_H).build());
        list.add(imp);

        ButtonWidget rpc = add(ButtonWidget.builder(rpcLabel(), b -> {
            DiscordRpc.enabled = !DiscordRpc.enabled;
            b.setMessage(rpcLabel());
            CustomHudOverlay.save();
        }).dimensions(bx, 0, 96, HudTheme.CONTROL_H).build());
        list.add(rpc);
    }

    private void createPerfWidgets(int bx, int fw) {
        List<ClickableWidget> list = tabWidgets[2];
        String[][] defs = {
                { "Hide Nametag", "nametag" }, { "No Hurt Cam", "hurtcam" },
                { "No Fire Overlay", "fire" }, { "Clear Water/Lava", "clearfluid" },
                { "No Fog", "nofog" }, { "No Darkness", "nodarkness" },
                { "No Pumpkin", "nopumpkin" }, { "Infinity Chat", "chat" },
        };
        for (String[] d : defs) {
            ButtonWidget b = add(ButtonWidget.builder(perfLabel(d[0], Perf.get(d[1])), btn -> {
                Perf.set(d[1], !Perf.get(d[1]));
                btn.setMessage(perfLabel(d[0], Perf.get(d[1])));
                CustomHudOverlay.save();
            }).dimensions(bx, 0, 96, HudTheme.CONTROL_H).build());
            list.add(b);
        }
    }

    private void createSkinsWidgets(int bx, int fw) {
        List<ClickableWidget> list = tabWidgets[3];

        skinBtn(list, "URL", () -> { SkinStore.skinMode = "url"; applySkinMode(); });
        skinBtn(list, "File", () -> { filePick = 1; filesToPick("skins", SkinStore.skinValue); });
        skinBtn(list, "User", () -> { SkinStore.skinMode = "user"; applySkinMode(); });

        skinField = new TextFieldWidget(this.textRenderer, bx, 0, fw - 68, 16, Text.literal("Skin URL / username"));
        skinField.setMaxLength(200);
        skinField.setText(SkinStore.skinValue);
        skinField.setChangedListener(s -> SkinStore.skinValue = s);
        addDrawableChild(skinField);
        list.add(skinField);

        ButtonWidget skinApply = add(ButtonWidget.builder(Text.literal("Apply"), b -> applySkin())
                .dimensions(bx, 0, 60, HudTheme.CONTROL_H).build());
        list.add(skinApply);

        skinBtn(list, "URL", () -> { SkinStore.capeMode = "url"; applySkinMode(); });
        skinBtn(list, "File", () -> { filePick = 2; filesToPick("capes", SkinStore.capeValue); });

        capeField = new TextFieldWidget(this.textRenderer, bx, 0, fw - 68, 16, Text.literal("Cape URL"));
        capeField.setMaxLength(200);
        capeField.setText(SkinStore.capeValue);
        capeField.setChangedListener(s -> SkinStore.capeValue = s);
        addDrawableChild(capeField);
        list.add(capeField);

        ButtonWidget capeApply = add(ButtonWidget.builder(Text.literal("Apply"), b -> applySkin())
                .dimensions(bx, 0, 60, HudTheme.CONTROL_H).build());
        list.add(capeApply);

        ButtonWidget resetSkin = add(ButtonWidget.builder(Text.literal("Reset skin"), b -> resetSkin(false))
                .dimensions(bx, 0, 64, HudTheme.CONTROL_H).build());
        list.add(resetSkin);

        ButtonWidget resetCape = add(ButtonWidget.builder(Text.literal("Reset cape"), b -> resetSkin(true))
                .dimensions(bx, 0, 64, HudTheme.CONTROL_H).build());
        list.add(resetCape);
    }

    private void skinBtn(List<ClickableWidget> list, String label, Runnable action) {
        ButtonWidget b = add(ButtonWidget.builder(Text.literal(label), b2 -> action.run())
                .dimensions(0, 0, 64, HudTheme.CONTROL_H).build());
        list.add(b);
    }

    private ButtonWidget add(ButtonWidget b) { addDrawableChild(b); return b; }

    // ---- row layout (recomputed each frame so resize never misaligns) ----

    /** One visual row inside the scrollable list. */
    static final class Row {
        final String header;          // non-null -> draw as section header
        final String label;           // left label for control row
        final int height;
        final ClickableWidget widget; // primary right-side control (optional)
        final HudWidget module;       // module row data (optional)
        HudWidget module2;            // second module in the same row (Modules tab 2-col layout)
        final List<ClickableWidget> extra = new ArrayList<>(); // additional controls in this row
        String tag;                   // row marker used by tabs (e.g. "filePicker")
        int y;                        // content-space y, relative to listTop
        int x, w, x2, w2;             // row/control geometry

        Row(String header, int height) {
            this.header = header; this.label = null; this.height = height;
            this.widget = null; this.module = null;
        }
        Row(String label, int height, ClickableWidget widget) {
            this.header = null; this.label = label; this.height = height;
            this.widget = widget; this.module = null;
        }
        Row(String label, int height, HudWidget module) {
            this.header = null; this.label = label; this.height = height;
            this.widget = null; this.module = module;
        }
        boolean isHeader() { return header != null; }
        boolean hasWidget() { return widget != null || !extra.isEmpty(); }
    }

    /** Scrollable content of one tab: a stack of rows plus total height. */
    static final class TabContent {
        final List<Row> rows = new ArrayList<>();
        int contentHeight = 0;

        void clear() { rows.clear(); contentHeight = 0; }

        Row header(String name) {
            Row r = new Row(name, HudTheme.HDR_H);
            r.y = contentHeight;
            rows.add(r);
            contentHeight += HudTheme.HDR_H;
            return r;
        }

        Row row(String label, int height, ClickableWidget widget) {
            Row r = new Row(label, height, widget);
            r.y = contentHeight;
            rows.add(r);
            contentHeight += height;
            return r;
        }

        Row row(String label, int height, HudWidget module) {
            Row r = new Row(label, height, module);
            r.y = contentHeight;
            rows.add(r);
            contentHeight += height;
            return r;
        }

        Row spacer(int height) {
            Row r = new Row(null, height, (ClickableWidget) null);
            r.y = contentHeight;
            rows.add(r);
            contentHeight += height;
            return r;
        }

        int maxScroll(int viewH) { return Math.max(0, contentHeight - viewH); }
    }

    /** Dựng lại danh sách dòng của tab Modules theo ô search. */
    private void rebuild() {
        modules.buildRows(this, tabContent[0]);
        applyScroll();
    }

    /** Tính lại layout 4 tab; gọi mỗi frame để khớp kích thước màn hình. */
    private void layoutTabs() {
        for (int i = 0; i < 4; i++) tabContent[i].clear();
        modules.buildRows(this, tabContent[0]);
        OptionsTab.buildRows(this, tabContent[1]);
        PerfTab.buildRows(this, tabContent[2]);
        SkinTab.buildRows(this, tabContent[3]);
        applyScroll();
        positionFixedWidgets();
    }

    private void positionFixedWidgets() {
        int fw = contentW();
        int bx = contentX();
        if (searchField != null) searchField.setDimensionsAndPosition(fw, 16, bx, pY + 86);
        if (editorButton != null) editorButton.setDimensionsAndPosition(120, HudTheme.CONTROL_H, bx + fw - 120, pY + pH - 30);
    }

    int tabTop() { return listTop + scrollY; }

    private int maxScroll() { return tabContent[tab].maxScroll(viewH); }

    private void applyScroll() {
        scrollY = Math.max(0, Math.min(maxScroll(), scrollY));
    }

    /** Ẩn/hiện widget theo tab hiện tại. */
    private void applyTab() {
        if (searchField != null) searchField.visible = (tab == 0);
        for (int i = 0; i < 4; i++) {
            boolean active = tab == i;
            for (ClickableWidget w : tabWidgets[i]) w.visible = active;
        }
        scrollY = 0;
        applySkinMode();
    }

    HudManager hud() { return m; }
    String searchText() { return searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT); }

    // ---- Options / Perf / Skins actions ----

    private Text perfLabel(String name, boolean on) { return Text.literal(name + ": " + (on ? "ON" : "OFF")); }
    private Text rpcLabel() { return Text.literal("Discord RPC: " + (DiscordRpc.enabled ? "ON" : "OFF")); }

    private void presetSave() {
        String name = presetField.getText().trim();
        Path f = HudConfig.presetFile(CustomHudOverlay.configPath(), name);
        if (f == null) { status = "preset: no config path"; return; }
        CustomHudOverlay.setPresetName(name);
        HudConfig.save(m, f);
        CustomHudOverlay.save();
        status = "saved " + f.getFileName();
    }

    private void presetLoad() {
        String name = presetField.getText().trim();
        Path f = HudConfig.presetFile(CustomHudOverlay.configPath(), name);
        if (f == null) { status = "preset: no config path"; return; }
        if (!Files.exists(f)) { status = "preset not found"; return; }
        HudConfig.load(m, f);
        CustomHudOverlay.setPresetName(name);
        m.placeGroups();
        CustomHudOverlay.save();
        status = "loaded " + f.getFileName();
    }

    static Path shareDir() {
        Path root = HudConfig.presetFile(CustomHudOverlay.configPath(), "x");
        return root == null ? null : root.getParent().resolve("share");
    }

    private static List<String> shareNames() {
        List<String> out = new ArrayList<>();
        Path d = shareDir();
        if (d == null || !Files.isDirectory(d)) return out;
        try (var s = Files.list(d)) {
            s.map(p -> p.getFileName().toString())
             .filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".valkahud"))
             .sorted().forEach(out::add);
        } catch (Exception ignored) { }
        return out;
    }

    private void filesToPickProfiles() {
        fileList.clear();
        fileList.addAll(shareNames());
        fileScroll = 0;
    }

    private void presetExport() {
        String name = presetField.getText().trim();
        Path f = HudConfig.presetFile(CustomHudOverlay.configPath(), name);
        if (f == null || !Files.exists(f)) { status = "nothing to export (Save first)"; return; }
        Path d = shareDir();
        if (d == null) { status = "share: no config path"; return; }
        try {
            Files.createDirectories(d);
            Path out = d.resolve(HudConfig.sanitize(name) + ".valkahud");
            Files.write(out, Files.readAllBytes(f));
            status = "exported " + out;
        } catch (Exception e) { status = "export failed"; }
    }

    void importShared(String fileName) {
        Path f = HudConfig.presetFile(CustomHudOverlay.configPath(), fileName.replaceAll("(?i)\\.valkahud$", ""));
        if (f == null) { status = "import: no config path"; return; }
        try {
            Files.createDirectories(f.getParent());
            Files.write(f, Files.readAllBytes(shareDir().resolve(fileName)));
            presetField.setText(fileName.replaceAll("(?i)\\.valkahud$", ""));
            presetLoad();
            status = "imported " + fileName;
        } catch (Exception e) { status = "import failed"; }
    }

    void filesToPick(String kind, String current) {
        fileList.clear();
        fileList.addAll(SkinStore.files(kind));
        fileScroll = 0;
    }

    private void resetSkin(boolean cape) {
        if (cape) {
            SkinStore.capeMode = "none";
            SkinStore.capeValue = "";
            if (capeField != null) capeField.setText("");
        } else {
            SkinStore.skinMode = "none";
            SkinStore.skinValue = "";
            if (skinField != null) skinField.setText("");
        }
        SkinStore.reload();
        CustomHudOverlay.save();
        applySkinMode();
    }

    void applySkinMode() {
        if (skinField != null) {
            skinField.setText(SkinStore.skinValue);
            skinField.setVisible(tab == 3 && (SkinStore.skinMode.equals("url") || SkinStore.skinMode.equals("user")));
        }
        if (capeField != null) {
            capeField.setText(SkinStore.capeValue);
            capeField.setVisible(tab == 3 && SkinStore.capeMode.equals("url"));
        }
    }

    void applySkin() {
        if (skinField != null) SkinStore.skinValue = skinField.getText().trim();
        if (capeField != null) SkinStore.capeValue = capeField.getText().trim();
        if (SkinStore.skinMode.equals("url") || SkinStore.skinMode.equals("user") || SkinStore.capeMode.equals("url")) {
            SkinStore.reload();
        } else {
            SkinStore.reset();
            SkinStore.reload();
        }
        CustomHudOverlay.save();
    }

    private static final String[] TAB_NAMES = { "Modules", "Options", "Perf", "Skins" };
    private static final String[] TAB_DESC = {
            "Toggle and arrange your HUD.",
            "Layout, presets and integrations.",
            "Client-side visual toggles.",
            "Skin and cape sources."
    };

    // ---- tabs ----

    private int navY(int i) {
        return pY + HudTheme.HEADER_H + 12 + i * (HudTheme.NAV_ITEM_H + HudTheme.NAV_ITEM_GAP);
    }

    private int tabHit(double mx, double my) {
        int x = pX + 8, w = HudTheme.NAV_W - 16;
        for (int i = 0; i < 4; i++) {
            int y = navY(i);
            if (mx >= x && mx < x + w && my >= y && my < y + HudTheme.NAV_ITEM_H) return i;
        }
        return -1;
    }

    /** Nav rail item: selected gets an amber edge + raised card. */
    private void drawTab(DrawContext ctx, TextRenderer font, int i, int mouseX, int mouseY) {
        int x = pX + 8, w = HudTheme.NAV_W - 16;
        int y = navY(i);
        boolean sel = tab == i;
        boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + HudTheme.NAV_ITEM_H;
        if (sel) {
            ctx.fill(x, y, x + w, y + HudTheme.NAV_ITEM_H, HudTheme.NAV_SELECTED);
            ctx.fill(pX, y, pX + 2, y + HudTheme.NAV_ITEM_H, HudTheme.ACCENT);
        } else if (hov) {
            ctx.fill(x, y, x + w, y + HudTheme.NAV_ITEM_H, HudTheme.NAV_HOVER);
        }
        int col = sel ? HudTheme.TEXT_PRIMARY : (hov ? HudTheme.TEXT_PRIMARY : HudTheme.TEXT_SECONDARY);
        ctx.drawTextWithShadow(font, Text.literal(TAB_NAMES[i]), x + 10, y + (HudTheme.NAV_ITEM_H - 8) / 2, col);
    }

    // ---- render ----

    @Override public boolean shouldPause() { return false; }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (input.getKeycode() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER && tab == 3
                && ((skinField != null && skinField.isFocused()) || (capeField != null && capeField.isFocused()))) {
            applySkin();
            return true;
        }
        if (input.getKeycode() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { this.close(); return true; }
        return super.keyPressed(input);
    }

    @Override public void close() {
        if (onClose != null) onClose.run();
        super.close();
        if (parent != null) this.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        geometry();
        m.screenW = this.width;
        m.screenH = this.height;
        layoutTabs();

        ctx.fill(0, 0, this.width, this.height, editorMode ? 0x55000000 : 0x66000000);
        if (editorMode) drawGrid(ctx);

        TextRenderer font = this.textRenderer;
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
            if (w.width <= 0 || w.height <= 0) continue;
            if (w.enabled) w.render(ctx, font);
            drawWidgetOverlay(ctx, w, mouseX, mouseY);
        }
        drawLinkLine(ctx, mouseX, mouseY);

        if (editorMode) {
            ctx.drawTextWithShadow(font, Text.literal("HUD Editor - drag to move | right-click: unlink | Esc: save & exit"),
                    6, this.height - 14, HudTheme.TEXT_SECONDARY);
            return;
        }

        drawPanel(ctx, font, mouseX, mouseY, delta);
    }

    /** Скруглённый верх + тело панели настроек (модель Minecraft не умеет без заливки) */
    private void drawPanelBody(DrawContext ctx) {
        int x = pX, y = pY, w = pW, h = pH;
        int r = HudTheme.RADIUS_PANEL;
        ctx.fill(x + r, y, x + w - r, y + h, HudTheme.PANEL_BG);
        ctx.fill(x, y + r, x + r, y + h - r, HudTheme.PANEL_BG);
        ctx.fill(x + w - r, y + r, x + w, y + h - r, HudTheme.PANEL_BG);
        for (int d = 0; d < 4; d++) { // верх-лево
            ctx.fill(x + r - d, y + d, x + r + d, y + d, HudTheme.PANEL_BG);
        }
        for (int d = 0; d < 4; d++) { // верх-право
            ctx.fill(x + w - r - d, y + d, x + w - r + d, y + d, HudTheme.PANEL_BG);
        }
        // нижняя тень панели
        ctx.fill(x, y + h - 1, x + w, y + h, HudTheme.PANEL_SHADOW);
    }

    /** Кастомная вертикальная полоса прокрутки, держится справа от контента */
    private void drawScrollbar(DrawContext ctx) {
        if (maxScroll() <= 0) return;
        int barX = pX + pW - 10;
        int barW = 6;
        int barTop = listTop, barBottom = listBottom;
        int visibleH = barBottom - barTop;
        int thumbH = Math.max(12, (int) ((double) visibleH * visibleH / (double) maxScroll()));
        if (thumbH > visibleH - 4) thumbH = visibleH - 4;
        int thumbY = barTop + 2 + (int) ((double) scrollY / maxScroll() * (visibleH - thumbH - 4));
        ctx.fill(barX, barTop, barX + barW, barBottom, HudTheme.CARD_BG);
        ctx.fill(barX, thumbY, barX + barW, thumbY + thumbH, HudTheme.ACCENT);
        ctx.fill(barX, thumbY, barX + barW, thumbY + 1, HudTheme.TAB_HIGHLIGHT);
    }

    private void drawPanel(DrawContext ctx, TextRenderer font, int mouseX, int mouseY, float delta) {
        drawPanelBody(ctx);
        int cx = contentX(), cw = contentW();

        // header
        ctx.fill(pX, pY, pX + pW, pY + HudTheme.HEADER_H, HudTheme.NAV_BG);
        ctx.fill(pX, pY + HudTheme.HEADER_H - 1, pX + pW, pY + HudTheme.HEADER_H, HudTheme.BORDER);
        ctx.drawTextWithShadow(font, Text.literal("VALKAW"), pX + 12, pY + 9, HudTheme.ACCENT);
        ctx.drawTextWithShadow(font, Text.literal("CLIENT SETTINGS"), pX + 12, pY + 22, HudTheme.TEXT_SECONDARY);

        // left navigation rail
        ctx.fill(pX, pY + HudTheme.HEADER_H, pX + HudTheme.NAV_W, pY + pH - 32, HudTheme.NAV_BG);
        ctx.fill(pX + HudTheme.NAV_W - 1, pY + HudTheme.HEADER_H, pX + HudTheme.NAV_W, pY + pH - 32, HudTheme.BORDER);
        for (int i = 0; i < TAB_NAMES.length; i++) drawTab(ctx, font, i, mouseX, mouseY);

        // content heading
        ctx.drawTextWithShadow(font, Text.literal(TAB_NAMES[tab]), cx, pY + 54, HudTheme.TEXT_PRIMARY);
        ctx.drawTextWithShadow(font, Text.literal(TAB_DESC[tab]), cx, pY + 68, HudTheme.TEXT_TERTIARY);

        // footer rule
        ctx.fill(pX, pY + pH - 32, pX + pW, pY + pH - 31, HudTheme.BORDER);

        // scrollable content
        ctx.enableScissor(cx, listTop - 2, cx + cw, listBottom + 2);
        for (ClickableWidget w : tabWidgets[tab]) if (w.visible) w.render(ctx, mouseX, mouseY, delta);
        if (tab == 0) modules.draw(this, ctx, font, mouseX, mouseY);
        else if (tab == 1) OptionsTab.draw(this, ctx, font, mouseX, mouseY);
        else if (tab == 2) PerfTab.draw(this, ctx, font, mouseX, mouseY);
        else SkinTab.draw(this, ctx, font, mouseX, mouseY);
        ctx.disableScissor();
        drawScrollbar(ctx);

        // fixed search + footer action
        if (searchField != null && tab == 0) searchField.render(ctx, mouseX, mouseY, delta);
        if (editorButton != null) editorButton.render(ctx, mouseX, mouseY, delta);

        String key = Keybinds.HUD_EDITOR.getBoundKeyLocalizedText().getString();
        String hint = status.isEmpty() ? key + ": close & save" : status;
        hint = font.trimToWidth(hint, Math.max(40, (cx + cw - 120) - (pX + 12) - 8));
        ctx.drawTextWithShadow(font, Text.literal(hint), pX + 12, pY + pH - 20, HudTheme.TEXT_SECONDARY);
    }

    private void drawGrid(DrawContext ctx) {
        for (int x = 0; x < this.width; x += GRID) ctx.fill(x, 0, x + 1, this.height, 0x10FFFFFF);
        for (int y = 0; y < this.height; y += GRID) ctx.fill(0, y, this.width, y + 1, 0x10FFFFFF);
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

    // ---- input ----

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (!editorMode && mx >= pX && mx <= pX + pW && my >= listTop && my <= listBottom && maxScroll() > 0) {
            scrollY -= (int) Math.signum(vAmount) * 12;
            applyScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, hAmount, vAmount);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        double mx = click.x(), my = click.y();

        if (!editorMode && mx >= pX && mx <= pX + pW && my >= pY && my <= pY + pH) {
            if (click.button() != 0) return true;
            int t = tabHit(mx, my);
            if (t >= 0) { tab = t; filePick = 0; applyTab(); return true; }

            if (tab == 0) {
                HudWidget w = modules.click(this, mx, my);
                if (w != null) {
                    if (w.id.equals("skins")) { tab = 3; filePick = 0; applyTab(); return true; }
                    this.client.setScreen(new HudSettingsScreen(w, this));
                }
                return true;
            }
            if (tab == 3 && SkinTab.click(this, mx, my)) return true;
            if (tab == 1 && OptionsTab.click(this, mx, my)) return true;
            return true;
        }

        if (click.button() == 1) {
            HudWidget hit = widgetUnder(mx, my, null);
            if (hit != null && hit.enabled && hit.group != -1) { m.unlink(hit); return true; }
            return false;
        }
        if (click.button() != 0) return false;

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
            if (ix != 0 || iy != 0) { m.move(dragging, ix, iy); dragAccX -= ix; dragAccY -= iy; }
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
                if (linkDir > 0) m.link(linkFrom, target);
                else m.link(target, linkFrom);
            } else if (linkDir < 0) {
                m.unlink(linkFrom);
            }
            linkFrom = null;
            CustomHudOverlay.save();
            return true;
        }
        return false;
    }
}
