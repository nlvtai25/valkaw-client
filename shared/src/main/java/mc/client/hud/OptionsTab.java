package mc.client.hud;

import java.util.List;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import mc.client.standalone.DiscordRpc;

/** Tab Options: layout / global / profile / discord. */
final class OptionsTab {

    private OptionsTab() {}

    static void buildRows(HudEditorScreen s, HudEditorScreen.TabContent c) {
        List<ClickableWidget> list = s.tabWidgets[1];
        int bx = s.contentX();
        int fw = s.contentW();
        // колонка для подписей влево, управление начинается правее подписи
        int labelW = fw / 3;
        int controlX = bx + labelW + HudTheme.GAP_8;
        int contentW = fw - labelW - HudTheme.GAP_8 - HudTheme.GAP_8; // reserved area for the scrollbar
        int y0 = s.listTop + s.scrollY;
        int btnW = Math.min(72, Math.max(40, (contentW - HudTheme.GAP_8) / 2)), btnH = HudTheme.CONTROL_H;

        c.header("LAYOUT");
        HudEditorScreen.Row resetRow = c.row("Reset layout", HudTheme.ROW_CONTROL, list.get(0));
        resetRow.w = contentW;
        resetRow.widget.setDimensionsAndPosition(btnW, btnH, controlX, y0 + resetRow.y + 1);

        HudEditorScreen.Row openRow = c.row("Open global settings", HudTheme.ROW_CONTROL, list.get(1));
        openRow.w = contentW;
        openRow.widget.setDimensionsAndPosition(btnW, btnH, controlX, y0 + openRow.y + 1);

        c.header("PROFILE");
        HudEditorScreen.Row presetRow = c.row("Preset name", HudTheme.ROW_DETAIL, list.get(2));
        presetRow.w = contentW;
        presetRow.widget.setDimensionsAndPosition(contentW, 18, controlX, y0 + presetRow.y + 2);

        // Save + Load
        HudEditorScreen.Row saveRow = c.row("", HudTheme.ROW_CONTROL, list.get(3));
        saveRow.widget.setDimensionsAndPosition(btnW, btnH, controlX, y0 + saveRow.y + 1);
        saveRow.extra.add(list.get(4));
        list.get(4).setDimensionsAndPosition(btnW, btnH, controlX + btnW + HudTheme.GAP_8, y0 + saveRow.y + 1);

        // Export + Import
        HudEditorScreen.Row exportRow = c.row("", HudTheme.ROW_CONTROL, list.get(5));
        exportRow.widget.setDimensionsAndPosition(btnW, btnH, controlX, y0 + exportRow.y + 1);
        exportRow.extra.add(list.get(6));
        list.get(6).setDimensionsAndPosition(btnW, btnH, controlX + btnW + HudTheme.GAP_8, y0 + exportRow.y + 1);

        c.header("DISCORD");
        HudEditorScreen.Row rpcRow = c.row("Discord RPC", HudTheme.ROW_CONTROL, list.get(7));
        rpcRow.w = contentW;
        rpcRow.widget.setDimensionsAndPosition(btnW * 2, btnH, controlX + contentW - btnW * 2, y0 + rpcRow.y + 1);

        HudEditorScreen.Row status = c.spacer(24);
        status.tag = "discordStatus";

        if (s.filePick == 3) {
            int fh = Math.max(78, 16 + s.fileList.size() * 10 + 14);
            HudEditorScreen.Row fp = c.spacer(fh);
            fp.tag = "filePicker";
        }
    }

    static void draw(HudEditorScreen s, DrawContext ctx, TextRenderer font, int mouseX, int mouseY) {
        int bx = s.contentX();
        for (HudEditorScreen.Row r : s.tabContent[1].rows) {
            if (r.isHeader()) continue;
            int ry = s.tabTop() + r.y;
            if (ry >= s.listBottom || ry + r.height <= s.listTop) continue;
            if ("discordStatus".equals(r.tag)) {
                ctx.drawTextWithShadow(font, Text.literal("Status: " + DiscordRpc.status), bx, ry + 2, HudTheme.TEXT_SECONDARY);
                ctx.drawTextWithShadow(font, Text.literal("No IP or server name is sent."), bx, ry + 14, HudTheme.TEXT_TERTIARY);
                continue;
            }
            if (r.tag != null) continue;
            if (r.label == null || r.label.isEmpty()) continue;
            int ty = ry + (r.height - font.fontHeight) / 2;
            ctx.drawTextWithShadow(font, Text.literal(r.label), bx, ty, HudTheme.TEXT_SECONDARY);
        }

        if (s.filePick == 3) {
            HudEditorScreen.Row fp = find(s, "filePicker");
            if (fp != null) {
                int top = s.tabTop() + fp.y;
                if (top + fp.height > s.listTop && top < s.listBottom) {
                    SkinTab.filePicker(s, ctx, font, mouseX, mouseY, top);
                }
            }
        }
    }

    static boolean click(HudEditorScreen s, double mx, double my) {
        if (s.filePick != 3) return false;
        HudEditorScreen.Row fp = find(s, "filePicker");
        if (fp == null) return false;
        int top = s.tabTop() + fp.y;
        int bx = s.contentX();
        int listY = top + 16;
        int rows = s.fileList.size();
        for (int i = 0; i < rows; i++) {
            int ry = listY + i * 10;
            if (mx >= bx && mx <= s.contentX() + s.contentW() && my >= ry && my < ry + 10) {
                s.importShared(s.fileList.get(i));
                return true;
            }
        }
        return false;
    }

    private static HudEditorScreen.Row find(HudEditorScreen s, String tag) {
        for (HudEditorScreen.Row r : s.tabContent[1].rows) if (tag.equals(r.tag)) return r;
        return null;
    }
}
