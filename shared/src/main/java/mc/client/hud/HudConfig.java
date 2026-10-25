package mc.client.hud;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mc.client.standalone.Freelook;

/**
 * Lưu/đọc vị trí + setting HUD ra file text đơn giản (thuần JDK, không phụ thuộc Minecraft).
 * Định dạng mỗi dòng: <id>.<thuộc tính>=<giá trị>. Màu lưu hex ARGB.
 */
public final class HudConfig {

    private HudConfig() {}

    /** Tên profile đang dùng (lưu kèm layout để nhớ qua các lần mở game). */
    public static String preset = "";
    public static boolean autoSprint;

    /** File preset: <thư mục config>/hud-presets/<tên>.cfg. null nếu không xác định được đường dẫn. */
    public static Path presetFile(Path configFile, String name) {
        if (configFile == null) return null;
        Path dir = configFile.getParent();
        if (dir == null) return null;
        String n = sanitize(name);
        if (n.isEmpty()) n = "default";
        return dir.resolve("hud-presets").resolve(n + ".cfg");
    }

    /** Tên preset -> tên file an toàn (bỏ dấu cách/ký tự lạ, không cho tạo đường dẫn con). */
    public static String sanitize(String name) {
        if (name == null) return "";
        return name.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
    }

    private static boolean bool(String v) { return v.equals("1") || v.equalsIgnoreCase("true"); }

    private static int hex(String v) { return (int) Long.parseLong(v, 16); }

    public static void load(HudManager m, Path file) {
        if (file == null || !Files.exists(file)) return;
        try {
            for (String raw : Files.readAllLines(file)) {
                // Tách từng dòng: 1 dòng hỏng (số/hex sai) không được làm mất phần còn lại của config.
                try { applyLine(m, raw); } catch (RuntimeException ignored) { }
            }
            m.clearLayoutFlag();   // đã có vị trí lưu -> không tự xếp lại
        } catch (Exception e) {
            System.err.println("[mcclient] HUD config load failed: " + e);
        }
    }

    private static void applyLine(HudManager m, String raw) {
        String line = raw.trim();
        if (line.isEmpty() || line.startsWith("#")) return;
        int eq = line.indexOf('=');
        if (eq < 0) return;
        String key = line.substring(0, eq);
        String val = line.substring(eq + 1).trim();
        if (key.equals("screen.w")) { m.designW = Integer.parseInt(val); return; }
        if (key.equals("screen.h")) { m.designH = Integer.parseInt(val); return; }
        if (key.equals("preset")) { preset = val; return; }
        if (key.equals("autosprint.enabled")) { autoSprint = bool(val); return; }
        if (key.equals("perf.chatlimit")) {
            try { mc.client.standalone.Perf.setChatLimit(Integer.parseInt(val.trim())); } catch (NumberFormatException ignored) { }
            return;
        }
        if (key.startsWith("perf.")) {
            mc.client.standalone.Perf.set(key.substring(5), bool(val));
            return;
        }
        if (key.startsWith("skin.")) {
            String k = key.substring(5);
            if (k.equals("mode")) mc.client.standalone.SkinStore.skinMode = val;
            else if (k.equals("value")) mc.client.standalone.SkinStore.skinValue = val;
            return;
        }
        if (key.startsWith("cape.")) {
            String k = key.substring(5);
            if (k.equals("mode")) mc.client.standalone.SkinStore.capeMode = val;
            else if (k.equals("value")) mc.client.standalone.SkinStore.capeValue = val;
            return;
        }
        if (key.startsWith("quickchat.")) {
            mc.client.standalone.QuickChat.set(key.substring(10), val);
            return;
        }
        if (key.startsWith("rpc.")) {
            String k = key.substring(4);
            if (k.equals("enabled")) mc.client.standalone.DiscordRpc.enabled = bool(val);
            return;
        }
        int dot = key.lastIndexOf('.');
        if (dot < 0) return;
        HudWidget w = m.byId(key.substring(0, dot));
        if (w == null) return;
        switch (key.substring(dot + 1)) {
            case "enabled" -> w.enabled = bool(val);
            case "x" -> w.x = Integer.parseInt(val);
            case "y" -> w.y = Integer.parseInt(val);
            case "group" -> { w.group = Integer.parseInt(val); m.bumpGroup(w.group); }
            case "label" -> { if (!val.isEmpty()) w.label = val; }
            case "hideLabel" -> w.hideLabel = bool(val);
            case "vertical" -> w.vertical = bool(val);
            case "textShadow" -> w.textShadow = bool(val);
            case "bgEnabled" -> w.bgEnabled = bool(val);
            case "textColor" -> w.textColor = hex(val);
            case "bgColor" -> w.bgColor = hex(val);
            case "effect" -> w.effect = Integer.parseInt(val);
            case "thickness" -> { if (w instanceof ColorBoxWidget b) b.thickness = Float.parseFloat(val); }
            case "colorMode" -> { if (w instanceof ColorBoxWidget b) b.colorMode = Integer.parseInt(val); }
            case "overlayColor" -> { if (w instanceof ColorBoxWidget b) b.lineColor = hex(val); }
            case "px" -> { if (w instanceof Widgets.Crosshair c) loadPx(c, val); }
            case "color" -> { if (w instanceof Widgets.Crosshair c) c.color = hex(val); else w.textColor = hex(val); }
            case "dot" -> { if (w instanceof Widgets.Crosshair c) c.dot = Integer.parseInt(val); }
            case "dateFmt" -> { if (w instanceof Widgets.Time t) t.dateFmt = val; }
            case "timeFmt" -> { if (w instanceof Widgets.Time t) t.timeFmt = val; }
            case "split" -> { if (w instanceof Widgets.Armor) Widgets.Armor.split = bool(val); }
            case "durabilityColor" -> { if (w instanceof Widgets.Armor a) a.durabilityColor = hex(val); }
            case "front" -> mc.client.standalone.Freelook.front = bool(val);
            case "showCrosshair" -> mc.client.standalone.Freelook.showCrosshair = bool(val);
            default -> { }
        }
    }

    private static void loadPx(Widgets.Crosshair c, String val) {
        String[] rows = val.split(";", -1);
        for (int r = 0; r < Widgets.Crosshair.N && r < rows.length; r++) {
            String row = rows[r];
            for (int col = 0; col < Widgets.Crosshair.N && col < row.length(); col++) {
                c.px[r][col] = row.charAt(col) == '1';
            }
        }
    }

    public static void save(HudManager m, Path file) {
        if (file == null) return;
        try {
            List<String> lines = new ArrayList<>();
            lines.add("# mcclient HUD layout");
            lines.add("screen.w=" + m.screenW);
            lines.add("screen.h=" + m.screenH);
            lines.add("preset=" + preset);
            lines.add("autosprint.enabled=" + (autoSprint ? 1 : 0));
            for (int i = 0; i < m.size(); i++) {
                HudWidget w = m.get(i);
                lines.add(w.id + ".enabled=" + (w.enabled ? 1 : 0));
                lines.add(w.id + ".x=" + w.x);
                lines.add(w.id + ".y=" + w.y);
                lines.add(w.id + ".group=" + w.group);
                lines.add(w.id + ".label=" + w.label);
                lines.add(w.id + ".hideLabel=" + (w.hideLabel ? 1 : 0));
                lines.add(w.id + ".vertical=" + (w.vertical ? 1 : 0));
                lines.add(w.id + ".textColor=" + Integer.toHexString(w.textColor));
                lines.add(w.id + ".textShadow=" + (w.textShadow ? 1 : 0));
                lines.add(w.id + ".bgEnabled=" + (w.bgEnabled ? 1 : 0));
                lines.add(w.id + ".bgColor=" + Integer.toHexString(w.bgColor));
                lines.add(w.id + ".effect=" + w.effect);
                if (w instanceof ColorBoxWidget b) {
                    lines.add(w.id + ".thickness=" + b.thickness);
                    lines.add(w.id + ".colorMode=" + b.colorMode);
                    lines.add(w.id + ".overlayColor=" + Integer.toHexString(b.lineColor));
                }
                if (w instanceof Widgets.Crosshair c) {
                    StringBuilder sb = new StringBuilder();
                    for (int r = 0; r < Widgets.Crosshair.N; r++) {
                        if (r > 0) sb.append(';');
                        for (int col = 0; col < Widgets.Crosshair.N; col++) sb.append(c.px[r][col] ? '1' : '0');
                    }
                    lines.add(w.id + ".px=" + sb);
                    lines.add(w.id + ".color=" + Integer.toHexString(c.color));
                    lines.add(w.id + ".dot=" + c.dot);
                }
                if (w instanceof Widgets.Time t) {
                    lines.add(w.id + ".dateFmt=" + t.dateFmt);
                    lines.add(w.id + ".timeFmt=" + t.timeFmt);
                }
                if (w instanceof Widgets.Armor a) {
                    lines.add(w.id + ".split=" + (Widgets.Armor.split ? 1 : 0));
                    lines.add(w.id + ".durabilityColor=" + Integer.toHexString(a.durabilityColor));
                }
                if (w instanceof FreelookWidget) {
                    lines.add(w.id + ".front=" + (Freelook.front ? 1 : 0));
                    lines.add(w.id + ".showCrosshair=" + (Freelook.showCrosshair ? 1 : 0));
                }
            }
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            for (String l : mc.client.standalone.Perf.dump().split("\n")) if (!l.isBlank()) lines.add(l);
            lines.add("skin.mode=" + mc.client.standalone.SkinStore.skinMode);
            lines.add("skin.value=" + mc.client.standalone.SkinStore.skinValue);
            lines.add("cape.mode=" + mc.client.standalone.SkinStore.capeMode);
            lines.add("cape.value=" + mc.client.standalone.SkinStore.capeValue);
            for (String l : mc.client.standalone.QuickChat.dump().split("\n")) if (!l.isBlank()) lines.add(l);
            lines.add("rpc.enabled=" + (mc.client.standalone.DiscordRpc.enabled ? 1 : 0));
            Files.write(file, lines);
        } catch (Exception e) {
            System.err.println("[mcclient] HUD config save failed: " + e);
        }
    }
}
