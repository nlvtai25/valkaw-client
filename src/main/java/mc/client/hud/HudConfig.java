package mc.client.hud;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lưu/đọc vị trí + setting HUD ra file text đơn giản (thuần JDK, không phụ thuộc Minecraft).
 * Định dạng mỗi dòng: <id>.<thuộc tính>=<giá trị>. Màu lưu hex ARGB.
 */
public final class HudConfig {

    private HudConfig() {}

    private static boolean bool(String v) { return v.equals("1") || v.equalsIgnoreCase("true"); }

    private static int hex(String v) { return (int) Long.parseLong(v, 16); }

    public static void load(HudManager m, Path file) {
        if (file == null || !Files.exists(file)) return;
        try {
            for (String raw : Files.readAllLines(file)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String key = line.substring(0, eq);
                String val = line.substring(eq + 1).trim();
                if (key.equals("screen.w")) { m.designW = Integer.parseInt(val); continue; }
                if (key.equals("screen.h")) { m.designH = Integer.parseInt(val); continue; }
                int dot = key.lastIndexOf('.');
                if (dot < 0) continue;
                HudWidget w = m.byId(key.substring(0, dot));
                if (w == null) continue;
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
                    default -> { }
                }
            }
            m.clearLayoutFlag();   // đã có vị trí lưu -> không tự xếp lại
        } catch (Exception e) {
            System.err.println("[mcclient] HUD config load failed: " + e);
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
            }
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.write(file, lines);
        } catch (Exception e) {
            System.err.println("[mcclient] HUD config save failed: " + e);
        }
    }
}
