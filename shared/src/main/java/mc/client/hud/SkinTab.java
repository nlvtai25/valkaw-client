package mc.client.hud;

import java.util.List;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import mc.client.standalone.SkinStore;

/** Tab Skins & Capes: preview 2D (mặt + cape) và danh sách file khi chọn nguồn File. */
final class SkinTab {

    private SkinTab() {}

    static void buildRows(HudEditorScreen s, HudEditorScreen.TabContent c) {
        List<ClickableWidget> list = s.tabWidgets[3];
        int bx = s.contentX();
        int fw = s.contentW();
        // So với OptionsTab: cột tên bên trái, widget ở bên phải
        int labelW = fw / 3;
        int controlX = bx + labelW + HudTheme.GAP_8;
        // -GAP_8 extra: reserved width for the right-side scrollbar
        int contentW = fw - labelW - HudTheme.GAP_8 - HudTheme.GAP_8;
        int y0 = s.listTop + s.scrollY;
        int btnW = Math.max(40, Math.min(56, contentW / 3));

        c.header("SKIN");
        HudEditorScreen.Row skinSrc = c.row("Source", HudTheme.ROW_CONTROL, list.get(0));
        skinSrc.w = contentW;
        skinSrc.extra.add(list.get(1));
        skinSrc.extra.add(list.get(2));
        placeButtonRow(skinSrc, controlX, y0, 3);

        HudEditorScreen.Row skinVal = c.row("Value", HudTheme.ROW_DETAIL, list.get(3));
        skinVal.w = contentW;
        skinVal.widget.setDimensionsAndPosition(contentW - btnW - HudTheme.GAP_8, HudTheme.ROW_DETAIL - 4,
                controlX, y0 + skinVal.y + 2);
        list.get(4).setDimensionsAndPosition(btnW, HudTheme.CONTROL_H, controlX + contentW - btnW, y0 + skinVal.y + 1);

        HudEditorScreen.Row preview = c.spacer(83);
        preview.tag = "preview";

        HudEditorScreen.Row skinReset = c.row("Reset", HudTheme.ROW_CONTROL, list.get(9));
        skinReset.w = contentW;
        skinReset.extra.add(list.get(10));
        placeButtonRow(skinReset, controlX, y0, 2);

        c.header("CAPE");
        HudEditorScreen.Row capeSrc = c.row("Source", HudTheme.ROW_CONTROL, list.get(5));
        capeSrc.w = contentW;
        capeSrc.extra.add(list.get(6));
        placeButtonRow(capeSrc, controlX, y0, 2);

        HudEditorScreen.Row capeVal = c.row("Value", HudTheme.ROW_DETAIL, list.get(7));
        capeVal.w = contentW;
        capeVal.widget.setDimensionsAndPosition(contentW - btnW - HudTheme.GAP_8, HudTheme.ROW_DETAIL - 4,
                controlX, y0 + capeVal.y + 2);
        list.get(8).setDimensionsAndPosition(btnW, HudTheme.CONTROL_H, controlX + contentW - btnW, y0 + capeVal.y + 1);

        HudEditorScreen.Row status = c.spacer(20);
        status.tag = "status";

        if (s.filePick > 0) {
            int fh = Math.max(78, 16 + s.fileList.size() * 10 + 14);
            HudEditorScreen.Row fp = c.spacer(fh);
            fp.tag = "filePicker";
        }
    }

    private static void placeButtonRow(HudEditorScreen.Row r, int controlX, int y0, int count) {
        int gap = HudTheme.GAP_4;
        int w = (r.w - (count - 1) * gap) / count;
        int x = controlX;
        r.widget.setDimensionsAndPosition(w, HudTheme.CONTROL_H, x, y0 + r.y + 1);
        x += w + gap;
        for (ClickableWidget e : r.extra) {
            e.setDimensionsAndPosition(w, HudTheme.CONTROL_H, x, y0 + r.y + 1);
            x += w + gap;
        }
    }

    static void draw(HudEditorScreen s, DrawContext ctx, TextRenderer font, int mouseX, int mouseY) {
        int bx = s.contentX();
        int gx = bx + s.contentW() / 3;
        drawLabels(s, ctx, font, bx);

        HudEditorScreen.Row preview = find(s, "preview");
        if (preview != null) {
            int py = s.tabTop() + preview.y;
            if (py + preview.height > s.listTop && py < s.listBottom) {
                drawPreview(s, ctx, font, gx, py + 16);
            }
        }

        HudEditorScreen.Row status = find(s, "status");
        if (status != null) {
            int sy = s.tabTop() + status.y;
            if (sy + status.height > s.listTop && sy < s.listBottom) {
                ctx.drawTextWithShadow(font, Text.literal(SkinStore.status()), gx, sy, HudTheme.TEXT_SECONDARY);
            }
        }

        if (s.filePick > 0) {
            HudEditorScreen.Row fp = find(s, "filePicker");
            if (fp != null) {
                int top = s.tabTop() + fp.y;
                if (top + fp.height > s.listTop && top < s.listBottom) {
                    filePicker(s, ctx, font, mouseX, mouseY, top);
                }
            }
        }
    }

    private static void drawLabels(HudEditorScreen s, DrawContext ctx, TextRenderer font, int bx) {
        for (HudEditorScreen.Row r : s.tabContent[3].rows) {
            if (r.isHeader() || r.label == null || r.tag != null) continue;
            int ry = s.tabTop() + r.y;
            if (ry >= s.listBottom || ry + r.height <= s.listTop) continue;
            int ty = ry + (r.height - font.fontHeight) / 2;
            ctx.drawTextWithShadow(font, Text.literal(r.label), bx, ty, HudTheme.TEXT_SECONDARY);
        }
    }

    private static void drawPreview(HudEditorScreen s, DrawContext ctx, TextRenderer font, int bx, int py) {
        Identifier body = SkinStore.bodyId();
        if (body != null) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, body, bx, py, 8.0F, 8.0F, 48, 48, 8, 8, 64, 64);
        } else {
            ctx.fill(bx, py, bx + 48, py + 48, 0x30FFFFFF);
        }
        int px2 = bx + 64;
        Identifier cape = SkinStore.capeId();
        if (cape != null) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, cape, px2, py, 1.0F, 1.0F, 32, 48, 10, 16, 64, 32);
        } else {
            ctx.fill(px2, py, px2 + 32, py + 48, 0x30FFFFFF);
        }
        ctx.drawTextWithShadow(font, Text.literal("Skin: " + modeName(SkinStore.skinMode)), bx + 108, py + 8, HudTheme.TEXT_PRIMARY);
        ctx.drawTextWithShadow(font, Text.literal("Cape: " + modeName(SkinStore.capeMode)), bx + 108, py + 24, HudTheme.TEXT_PRIMARY);
    }

    /** Danh sách file dùng chung cho tab Skins (skin/cape) và tab Options (profile share). */
    static void filePicker(HudEditorScreen s, DrawContext ctx, TextRenderer font, int mouseX, int mouseY, int top) {
        String kind = switch (s.filePick) { case 1 -> "skins"; case 2 -> "capes"; default -> "share"; };
        int bx = s.contentX();
        int titleY = top + 2;
        ctx.drawTextWithShadow(font, Text.literal(s.filePick == 3 ? "Pick profile in share:" : "Pick in " + kind + "-local:"),
                bx, titleY, HudTheme.ACCENT);
        int listY = titleY + 14;
        if (s.fileList.isEmpty()) {
            String dir = s.filePick == 3 ? String.valueOf(HudEditorScreen.shareDir()) : String.valueOf(SkinStore.dir(kind));
            ctx.drawTextWithShadow(font, Text.literal("(empty) " + dir), bx, listY, HudTheme.DANGER);
            return;
        }
        int rows = s.fileList.size();
        for (int i = 0; i < rows; i++) {
            int ry = listY + i * 10;
            boolean hov = mouseX >= bx && mouseX <= s.contentX() + s.contentW() && mouseY >= ry && mouseY < ry + 10;
            if (hov) ctx.fill(bx, ry, s.contentX() + s.contentW(), ry + 10, HudTheme.ROW_HOVER);
            ctx.drawTextWithShadow(font, Text.literal(s.fileList.get(i)), bx + 2, ry + 1, hov ? HudTheme.TEXT_PRIMARY : HudTheme.TEXT_TERTIARY);
        }
    }

    static boolean click(HudEditorScreen s, double mx, double my) {
        if (s.filePick <= 0) return false;
        HudEditorScreen.Row fp = find(s, "filePicker");
        if (fp == null) return false;
        int top = s.tabTop() + fp.y;
        int bx = s.contentX();
        int listY = top + 16;
        int rows = s.fileList.size();
        for (int i = 0; i < rows; i++) {
            int ry = listY + i * 10;
            if (mx >= bx && mx <= s.contentX() + s.contentW() && my >= ry && my < ry + 10) {
                String name = s.fileList.get(i);
                if (s.filePick == 1) { SkinStore.skinMode = "file"; SkinStore.skinValue = name; }
                else if (s.filePick == 2) { SkinStore.capeMode = "file"; SkinStore.capeValue = name; }
                else { s.importShared(name); return true; }
                s.filePick = 0;
                s.applySkinMode();
                s.applySkin();
                return true;
            }
        }
        return false;
    }

    private static HudEditorScreen.Row find(HudEditorScreen s, String tag) {
        for (HudEditorScreen.Row r : s.tabContent[3].rows) if (tag.equals(r.tag)) return r;
        return null;
    }

    private static String modeName(String m) {
        return switch (m) {
            case "url" -> "URL";
            case "file" -> "File";
            case "user" -> "Username";
            default -> "Vanilla";
        };
    }
}
