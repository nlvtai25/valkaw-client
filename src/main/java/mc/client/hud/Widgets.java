package mc.client.hud;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Các widget HUD cụ thể. Format chung: "[label] giá trị".
 * Tối ưu: formatter static, không cấp phát trong loop.
 */
final class Widgets {

    // Time lấy theo đồng hồ PC người chơi.
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /** FPS: lấy giá trị đã làm mượt của debug menu F3 (cập nhật mỗi giây). */
    static final class Fps extends HudWidget {
        private final GameState s; private int v;
        Fps(GameState s) { super("fps", "FPS"); this.s = s; }
        @Override public void update(float dt) { v = (int) s.fps(); }
        @Override protected String content() { return Integer.toString(v); }
    }

    static final class Coords extends HudWidget {
        private final GameState s; private double x, y, z;
        Coords(GameState s) { super("coords", "Coords"); this.s = s; }
        @Override public void update(float dt) { x = s.px(); y = s.py(); z = s.pz(); }
        @Override protected String content() {
            String a = "x: " + (int) Math.floor(x);
            String b = "y: " + (int) Math.floor(y);
            String c = "z: " + (int) Math.floor(z);
            return vertical ? (a + "\n" + b + "\n" + c) : (a + " " + b + " " + c);
        }
    }

    static final class Ping extends HudWidget {
        private final GameState s; private int v;
        Ping(GameState s) { super("ping", "Ping"); this.s = s; }
        @Override public void update(float dt) { v = s.ping(); }
        @Override protected String content() { return v + "ms"; }
    }

    /** IP Server dạng chữ (address). Singleplayer -> "Singleplayer". */
    static final class ServerIp extends HudWidget {
        private final GameState s; private String v = "-";
        ServerIp(GameState s) { super("ip", "IP Server"); this.s = s; }
        @Override public void update(float dt) {
            String t = s.serverIp();
            v = (t == null || t.isEmpty()) ? "-" : t;
        }
        @Override protected String content() { return v; }
    }

    /** Time theo giờ PC: dd/MM/yyyy HH:mm:ss. */
    static final class Time extends HudWidget {
        private String v = "";
        Time() { super("time", "Time"); }
        @Override public void update(float dt) { v = LocalDateTime.now().format(TIME); }
        @Override protected String content() { return v; }
    }

    static final class Sprint extends HudWidget {
        private final GameState s; private boolean on;
        Sprint(GameState s) { super("sprint", "Sprint"); this.s = s; }
        @Override public void update(float dt) { on = s.autoSprint(); }
        @Override protected String content() { return on ? "ON" : "OFF"; }
    }

    /** 4 món giáp + tay chính, xếp DỌC, kèm thanh độ bền (drawStackOverlay). 1 lớp nền. */
    static final class Armor extends HudWidget {
        private static final int ROW = 18;
        private final ItemStack[] slots = new ItemStack[5];

        Armor() { super("armor", "Armor"); }

        @Override public void update(float dt) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) { for (int i = 0; i < 5; i++) slots[i] = ItemStack.EMPTY; return; }
            slots[0] = mc.player.getEquippedStack(EquipmentSlot.HEAD);
            slots[1] = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            slots[2] = mc.player.getEquippedStack(EquipmentSlot.LEGS);
            slots[3] = mc.player.getEquippedStack(EquipmentSlot.FEET);
            slots[4] = mc.player.getMainHandStack();
        }

        @Override public void prepare(TextRenderer font) {
            width = 16 + PAD * 2;
            height = ROW * 5 + PAD * 2;
        }

        @Override protected String content() { return ""; }

        @Override public void render(DrawContext ctx, TextRenderer font) {
            if (width <= 0) return;
            ctx.fill(x, y, x + width, y + height, BG);
            int ix = x + PAD, iy = y + PAD;
            for (int i = 0; i < 5; i++) {
                ItemStack st = slots[i];
                if (st == null || st.isEmpty()) continue;
                int ry = iy + i * ROW;
                ctx.drawItem(st, ix, ry);
                ctx.drawStackOverlay(font, st, ix, ry);
            }
        }
    }

    /** RAM của game (JVM): đã dùng / được cấp (MB). Lấy mẫu 0.5 giây/lần. */
    static final class Ram extends HudWidget {
        private long nextMs;
        private long usedMb, capMb;
        Ram() { super("ram", "RAM"); }
        @Override protected String content() {
            long now = System.currentTimeMillis();
            if (now >= nextMs) {
                nextMs = now + 500;
                Runtime rt = Runtime.getRuntime();
                usedMb = (rt.totalMemory() - rt.freeMemory()) >> 20;
                capMb = rt.maxMemory() >> 20;
            }
            return usedMb + "/" + capMb + "MB";
        }
    }

    /** Tên CPU + % sử dụng toàn hệ thống. Không hiện nhãn "[CPU]". */
    static final class Cpu extends HudWidget {
        Cpu() { super("cpu", "CPU"); hideLabel = true; }
        @Override protected String content() {
            int p = SysInfo.cpuPct;
            return SysInfo.cpuName + ": " + (p < 0 ? "?" : Integer.toString(p)) + "%";
        }
    }

    /** Tên GPU + % sử dụng (nvidia-smi). Không hiện nhãn "[GPU]". */
    static final class Gpu extends HudWidget {
        Gpu() { super("gpu", "GPU"); hideLabel = true; }
        @Override protected String content() {
            int p = SysInfo.gpuPct;
            return SysInfo.gpuName + ": " + (p < 0 ? "?" : Integer.toString(p)) + "%";
        }
    }

    /** Crosshair tùy biến: lưới chấm pixel 15x15, vẽ giữa màn hình. */
    static final class Crosshair extends HudWidget {
        static final int N = 15;
        final boolean[][] px = new boolean[N][N];
        int color = 0xFFFFFFFF;   // gồm cả alpha
        int dot = 1;              // kích thước mỗi chấm (px GUI)

        Crosshair() { super("crosshair", "Crosshair"); center = true; presetCross(); }

        void fill(boolean v) { for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) px[r][c] = v; }

        void presetCross() {
            fill(false);
            for (int i = 4; i <= 10; i++) { px[7][i] = true; px[i][7] = true; }
        }

        void presetDot() { fill(false); px[7][7] = true; }

        @Override public void prepare(TextRenderer font) { width = 0; height = 0; }

        @Override public void render(DrawContext ctx, TextRenderer font) {
            if (!enabled) return;
            HudManager m = HudManager.get();
            int size = N * dot;
            int ox = (m.screenW - size) / 2;
            int oy = (m.screenH - size) / 2;
            int a = color & 0xFF000000;
            for (int r = 0; r < N; r++) {
                for (int c = 0; c < N; c++) {
                    if (!px[r][c]) continue;
                    int x0 = ox + c * dot, y0 = oy + r * dot;
                    int col = effect == 0 ? color
                            : (a | (effectRgb(effect, color, x0, ox, size) & 0x00FFFFFF));
                    ctx.fill(x0, y0, x0 + dot, y0 + dot, col);
                }
            }
        }
    }

    /** Hiển thị tên biome hiện tại (1 dòng): tên bản địa hoá nếu có, ngược lại Title Case. */
    static final class Biome extends HudWidget {
        private String v = "?";
        Biome() { super("biome", "Biome"); }
        @Override public void update(float dt) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null || mc.player == null) { v = "?"; return; }
            var entry = mc.world.getBiomeAccess().getBiome(mc.player.getBlockPos());
            var id = entry.getKey().map(k -> k.getValue()).orElse(null);
            if (id == null) { v = "?"; return; }
            v = Text.translatableWithFallback(id.toTranslationKey("biome"), prettify(id.getPath())).getString();
        }
        @Override protected String content() { return v; }
    }

    /** "snowy_taiga" -> "Snowy Taiga", "the_void" -> "The Void". */
    static String prettify(String id) {
        StringBuilder sb = new StringBuilder(id.length());
        boolean up = true;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == '_' || c == '/') { sb.append(' '); up = true; }
            else { sb.append(up ? Character.toUpperCase(c) : c); up = false; }
        }
        return sb.toString();
    }

    /** Danh sách hiệu ứng đang có + thời gian (nhiều dòng). */
    static final class Potion extends HudWidget {
        private String v = "";
        Potion() { super("potion", "Potion"); vertical = true; }
        @Override public void update(float dt) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) { v = ""; return; }
            var effects = mc.player.getStatusEffects();
            if (effects.isEmpty()) { v = ""; return; }
            StringBuilder sb = new StringBuilder();
            for (var inst : effects) {
                String name = inst.getEffectType().value().getName().getString();
                int amp = inst.getAmplifier();
                String roman = amp == 0 ? "" : " " + toRoman(amp + 1);
                int sec = inst.getDuration() / 20;
                String time = inst.isInfinite() ? "\u221E" : String.format("%d:%02d", sec / 60, sec % 60);
                if (sb.length() > 0) sb.append('\n');
                sb.append(name).append(roman).append(' ').append(time);
            }
            v = sb.toString();
        }
        @Override protected String content() { return v; }

        @Override protected String[] lines() {
            String[] ls = cached.split("\n", -1);
            if (ls.length == 0 || (ls.length == 1 && ls[0].isEmpty())) {
                return hideLabel ? new String[]{""} : new String[]{"[" + label + "]"};
            }
            String[] res = new String[ls.length + 1];
            res[0] = hideLabel ? "" : "[" + label + "]";
            System.arraycopy(ls, 0, res, 1, ls.length);
            return res;
        }

        private static String toRoman(int n) {
            String[] r = {"I","II","III","IV","V","VI","VII","VIII","IX","X"};
            return n >= 1 && n <= 10 ? r[n-1] : Integer.toString(n);
        }
    }
}
