package mc.client.hud;

import java.util.List;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import mc.client.standalone.Perf;

/** Tab Perf: 8 client-side visual toggles в одну колонку. */
final class PerfTab {

    private PerfTab() {}

    static void buildRows(HudEditorScreen s, HudEditorScreen.TabContent c) {
        List<ClickableWidget> list = s.tabWidgets[2];
        int bx = s.contentX();
        int fw = s.contentW();
        int labelW = fw / 3;
        int controlX = bx + labelW + HudTheme.GAP_8;
        // -GAP_8 extra: reserved width for the right-side scrollbar
        int contentW = fw - labelW - HudTheme.GAP_8 - HudTheme.GAP_8;
        int y0 = s.listTop + s.scrollY;
        int btnW = 96;

        c.header("CLIENT VISUALS");
        String[][] defs = {
                { "Hide Nametag", "nametag" }, { "No Hurt Cam", "hurtcam" },
                { "No Fire Overlay", "fire" }, { "Clear Water/Lava", "clearfluid" },
                { "No Fog", "nofog" }, { "No Darkness", "nodarkness" },
                { "No Pumpkin", "nopumpkin" }, { "Infinity Chat", "chat" },
        };
        for (int i = 0; i < defs.length; i++) {
            HudEditorScreen.Row r = c.row(defs[i][0], HudTheme.ROW_DETAIL, list.get(i));
            r.w = contentW;
            r.widget.setDimensionsAndPosition(btnW, HudTheme.CONTROL_H,
                    controlX + contentW - btnW, y0 + r.y + 1);
        }
    }

    static void draw(HudEditorScreen s, DrawContext ctx, TextRenderer font, int mouseX, int mouseY) {
        int bx = s.contentX();
        for (HudEditorScreen.Row r : s.tabContent[2].rows) {
            if (r.isHeader() || r.label == null) continue;
            int ry = s.tabTop() + r.y;
            if (ry >= s.listBottom || ry + r.height <= s.listTop) continue;
            int ty = ry + (r.height - font.fontHeight) / 2;
            ctx.drawTextWithShadow(font, Text.literal(r.label), bx, ty, HudTheme.TEXT_SECONDARY);
        }
        String note = "Client-side only - does not change vanilla/Sodium settings";
        int ny = s.listBottom - 12;
        if (ny >= s.listTop) {
            ctx.drawTextWithShadow(font, Text.literal(note), s.contentX(), ny, HudTheme.TEXT_TERTIARY);
        }
    }
}
