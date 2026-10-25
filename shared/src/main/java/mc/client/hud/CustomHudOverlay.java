package mc.client.hud;

import java.nio.file.Path;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Nối HudManager vào render loop của client standalone. */
public final class CustomHudOverlay {
    private static Path configFile;
    private static boolean init = false;
    private static int lastW = -1, lastH = -1;   // kích thước scaled lần render trước (phát hiện đổi cửa sổ/chế độ)

    public static void init(GameState state, Path cfg) {
        if (init) return;
        configFile = cfg;
        HudManager m = HudManager.get();
        m.register(state);
        if (cfg != null) HudConfig.load(m, cfg);
        mc.client.standalone.SkinStore.reload();   // áp skin/cape đã lưu
        init = true;
    }

    /** Gọi từ render loop sau khi HUD vanilla vẽ xong. */
    public static void render(DrawContext ctx, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        HudManager m = HudManager.get();
        if (mc != null) {
            int w = mc.getWindow().getScaledWidth();
            int h = mc.getWindow().getScaledHeight();
            boolean first = lastW <= 0 || lastH <= 0;
            if (first) {
                m.adaptToDesign(w, h);   // scale layout đã lưu từ kích thước lúc thiết kế
            } else if (w != lastW || h != lastH) {
                m.rescale(lastW, lastH, w, h);
            }
            lastW = w; lastH = h;
            m.screenW = w;
            m.screenH = h;
            m.renderFrame(ctx, dt);
            if (first) save();           // ghi sau khi đã đo text -> vị trí đúng
            return;
        }
        m.renderFrame(ctx, dt);
    }

    /** File config HUD (để màn Manager suy ra thư mục hud-presets). null = chưa init. */
    public static Path configPath() { return configFile; }

    /** Tên profile đang dùng (nhớ qua các lần mở game). */
    public static String presetName() { return HudConfig.preset; }

    public static void setPresetName(String name) { HudConfig.preset = name == null ? "" : name.trim(); }

    /** Lưu layout (gọi khi đóng HUD Editor). Nếu đang có profile -> lưu luôn vào profile đó. */
    public static void save() {
        if (configFile == null) return;
        HudManager m = HudManager.get();
        HudConfig.save(m, configFile);
        if (!HudConfig.preset.isEmpty()) {
            Path f = HudConfig.presetFile(configFile, HudConfig.preset);
            if (f != null) HudConfig.save(m, f);
        }
    }
}
