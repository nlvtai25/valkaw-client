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

    /** Time theo giờ PC. Pattern ngày/giờ nhập được trong setting (vd dd/MM/yyyy, HH:mm:ss). */
    static final class Time extends HudWidget {
        static final String DEF_DATE = "dd/MM/yyyy";
        static final String DEF_TIME = "HH:mm:ss";
        String dateFmt = DEF_DATE;
        String timeFmt = DEF_TIME;
        private String v = "";
        private DateTimeFormatter fmt = TIME;
        private String built = DEF_DATE + " " + DEF_TIME;

        Time() { super("time", "Time"); }

        /** Đổi pattern; pattern sai thì giữ formatter cũ (không ném lỗi mỗi frame). */
        private void rebuild() {
            String d = dateFmt.trim(), t = timeFmt.trim();
            String p = d.isEmpty() ? t : (t.isEmpty() ? d : d + " " + t);
            if (p.equals(built)) return;
            built = p;
            try { fmt = DateTimeFormatter.ofPattern(p); }
            catch (IllegalArgumentException e) { /* pattern sai: giữ nguyên formatter trước */ }
        }

        @Override public void update(float dt) {
            rebuild();
            v = LocalDateTime.now().format(fmt);
        }

        @Override protected String content() { return v; }
    }

    static final class Sprint extends HudWidget {
        private final GameState s; private boolean on;
        Sprint(GameState s) { super("sprint", "Sprint"); this.s = s; }
        @Override public void update(float dt) { on = s.autoSprint(); }
        @Override protected String content() { return on ? "ON" : "OFF"; }
    }

    /** 4 món giáp + tay chính. split=true -> 5 module riêng (ArmorPart) thay cho khối gộp. */
    static final class Armor extends HudWidget {
        static final int ROW = 18;
        static boolean split;                       // công tắc Split: Yes/No
        int durabilityColor = 0xFFFFE066;           // màu chữ độ bền
        private final ItemStack[] slots = new ItemStack[5];

        Armor() { super("armor", "Armor"); }

        @Override public void update(float dt) {
            for (int i = 0; i < 5; i++) slots[i] = equipped(i);
        }

        @Override public void prepare(TextRenderer font) {
            if (split) { width = 0; height = 0; return; }
            super.prepare(font);                     // label + text rỗng: vẫn tính width theo nhãn
            int tw = 0;
            for (int i = 0; i < 5; i++) {
                int w = font.getWidth(durability(slots[i]));
                if (w > tw) tw = w;
            }
            width = PAD * 2 + 16 + (tw > 0 ? 2 + tw : 0);
            height = ROW * 5 + PAD * 2;
        }

        @Override protected String content() { return ""; }

        @Override public void render(DrawContext ctx, TextRenderer font) {
            if (split || width <= 0) return;
            if (bgEnabled) ctx.fill(x, y, x + width, y + height, bgColor);
            boolean itemLeft = x + width / 2 < HudManager.get().screenW / 2;   // module bên trái -> độ bền bên phải
            for (int i = 0; i < 5; i++) {
                ItemStack st = slots[i];
                if (st == null || st.isEmpty()) continue;
                int ry = y + PAD + i * ROW;
                int ix = itemLeft ? x + PAD : x + width - PAD - 16;
                ctx.drawItem(st, ix, ry);
                ctx.drawStackOverlay(font, st, ix, ry);
                String d = durability(st);
                if (!d.isEmpty()) {
                    int tx = itemLeft ? ix + 18 : x + PAD;
                    ctx.drawText(font, d, tx, ry + (16 - font.fontHeight) / 2, durabilityColor, textShadow);
                }
            }
        }
    }

    /** Một món giáp riêng lẻ (khi Armor bật Split). slot: 0 mũ, 1 áo, 2 quần, 3 giày, 4 tay chính. */
    static final class ArmorPart extends HudWidget {
        static final String[] IDS = { "armor_head", "armor_chest", "armor_legs", "armor_feet", "armor_hand" };
        static final String[] NAMES = { "Helmet", "Chestplate", "Leggings", "Boots", "Main hand" };
        final int slot;
        private ItemStack st = ItemStack.EMPTY;
        private String dur = "";

        ArmorPart(int slot) { super(IDS[slot], NAMES[slot]); this.slot = slot; }

        @Override public void update(float dt) {
            st = equipped(slot);
            dur = Armor.split ? durability(st) : "";
        }

        @Override public void prepare(TextRenderer font) {
            if (!Armor.split || st == null || st.isEmpty()) { width = 0; height = 0; return; }
            super.prepare(font);
            width = PAD * 2 + 16 + (dur.isEmpty() ? 0 : 2 + font.getWidth(dur));
            height = 16 + PAD * 2;
        }

        @Override protected String content() { return ""; }

        @Override public void render(DrawContext ctx, TextRenderer font) {
            if (!Armor.split || width <= 0) return;
            if (bgEnabled) ctx.fill(x, y, x + width, y + height, bgColor);
            boolean itemLeft = x + width / 2 < HudManager.get().screenW / 2;
            int ix = itemLeft ? x + PAD : x + width - PAD - 16;
            int iy = y + PAD;
            ctx.drawItem(st, ix, iy);
            ctx.drawStackOverlay(font, st, ix, iy);
            if (!dur.isEmpty()) {
                int tx = itemLeft ? ix + 18 : x + PAD;
                HudWidget armor = HudManager.get().byId("armor");
                int color = armor instanceof Armor a ? a.durabilityColor : 0xFFFFE066;
                ctx.drawText(font, dur, tx, iy + (16 - font.fontHeight) / 2, color, textShadow);
            }
        }
    }

    /** Item đang mặc/cầm theo slot (0..4). */
    static ItemStack equipped(int slot) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return ItemStack.EMPTY;
        return switch (slot) {
            case 0 -> mc.player.getEquippedStack(EquipmentSlot.HEAD);
            case 1 -> mc.player.getEquippedStack(EquipmentSlot.CHEST);
            case 2 -> mc.player.getEquippedStack(EquipmentSlot.LEGS);
            case 3 -> mc.player.getEquippedStack(EquipmentSlot.FEET);
            default -> mc.player.getMainHandStack();
        };
    }

    /** "<còn lại>/<tối đa>" cho item có độ bền; item khác trả chuỗi rỗng. */
    static String durability(ItemStack st) {
        if (st == null || st.isEmpty() || !st.isDamageable()) return "";
        return (st.getMaxDamage() - st.getDamage()) + "/" + st.getMaxDamage();
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
            if (mc.client.standalone.Freelook.active && !mc.client.standalone.Freelook.showCrosshair) return;
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
