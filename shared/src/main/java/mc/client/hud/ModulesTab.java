package mc.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

/**
 * Tab Modules: danh sách module chia nhóm, có search và cuộn dọc.
 * Category membership comes from CATS only; any widget not listed there falls into OTHER.
 */
final class ModulesTab {
    private static final int SWITCH_W = 24;
    private static final int SWITCH_H = 10;

    /** Nhóm hiển thị (tên nhóm + id các module). Module lạ không có trong đây rơi vào OTHER. */
    private static final String[][] CATS = {
            { "INFO",   "fps ram cpu gpu time" },
            { "PLAYER", "coords biome potion armor armor_head armor_chest armor_legs armor_feet armor_hand sprint quickchat" },
            { "SERVER", "ping ip" },
            { "VISUAL",   "crosshair hitbox blockoverlay freelook" },
            { "COSMETIC", "skins" },
    };

    static int groupCount() { return CATS.length; }

    /** Build rows into the shared tab content: header per group, then 2-column module rows. */
    void buildRows(HudEditorScreen s, HudEditorScreen.TabContent c) {
        c.clear();
        String q = s.searchText();
        int colW = s.contentW() / 2;

        List<String> names = new ArrayList<>();
        List<List<HudWidget>> groups = new ArrayList<>();
        for (String[] cat : CATS) {
            List<HudWidget> tmp = new ArrayList<>();
            for (String id : cat[1].split(" ")) {
                HudWidget widget = s.hud().byId(id);
                if (widget instanceof Widgets.ArmorPart && !Widgets.Armor.split) continue;
                if (widget != null && match(widget, q)) tmp.add(widget);
            }
            names.add(cat[0]);
            groups.add(tmp);
        }

        List<HudWidget> other = new ArrayList<>();
        for (int i = 0; i < s.hud().size(); i++) {
            HudWidget widget = s.hud().get(i);
            if (!categorized(widget.id) && match(widget, q)) other.add(widget);
        }
        if (!other.isEmpty()) {
            names.add("OTHER");
            groups.add(other);
        }

        for (int i = 0; i < names.size(); i++) {
            putGroup(s, c, names.get(i), groups.get(i), colW);
        }
    }

    private void putGroup(HudEditorScreen s, HudEditorScreen.TabContent c, String name, List<HudWidget> mods, int colW) {
        if (mods.isEmpty()) return;
        int total = mods.size();
        int enabled = 0;
        for (HudWidget w : mods) if (w.enabled) enabled++;
        c.header(name + "  " + enabled + "/" + total);

        int leftX = s.contentX();
        int rightX = leftX + colW;
        int rowH = s.rowH;
        int cols = colW >= 100 ? 2 : 1;
        for (int i = 0; i < mods.size(); i += cols) {
            HudWidget left = mods.get(i);
            HudWidget right = (cols == 2 && i + 1 < mods.size()) ? mods.get(i + 1) : null;
            HudEditorScreen.Row r = c.row(null, rowH, left);
            r.x = leftX;
            r.w = cols == 2 ? colW - 4 : s.contentW() - 4;
            if (right != null) {
                r.module2 = right;
                r.x2 = rightX; r.w2 = colW - 4;
            }
        }
    }

    void draw(HudEditorScreen s, DrawContext ctx, TextRenderer font, int mouseX, int mouseY) {
        ctx.enableScissor(s.contentX(), s.listTop, s.contentX() + s.contentW(), s.listBottom);
        for (HudEditorScreen.Row r : s.tabContent[0].rows) {
            int ry = s.tabTop() + r.y;
            if (ry >= s.listBottom || ry + r.height <= s.listTop) continue;
            if (r.isHeader()) {
                ctx.drawTextWithShadow(font, Text.literal(r.header), s.contentX(), ry + (HudTheme.HDR_H - 8) / 2, HudTheme.TEXT_SECONDARY);
                ctx.fill(s.contentX(), ry + HudTheme.HDR_H - 3, s.contentX() + s.contentW(), ry + HudTheme.HDR_H - 2, HudTheme.HEADER_LINE);
                continue;
            }
            drawModule(s, ctx, font, mouseX, mouseY, r, r.module, r.x, r.w, ry);
            if (r.module2 != null) drawModule(s, ctx, font, mouseX, mouseY, r, r.module2, r.x2, r.w2, ry);
        }
        ctx.disableScissor();
    }

    private void drawModule(HudEditorScreen s, DrawContext ctx, TextRenderer font, int mouseX, int mouseY,
                            HudEditorScreen.Row row, HudWidget w, int rx, int rw, int ry) {
        boolean hot = mouseX >= rx && mouseX <= rx + rw && mouseY >= ry && mouseY <= ry + s.rowH;
        if (hot) ctx.fill(rx, ry, rx + rw, ry + s.rowH, HudTheme.ROW_HOVER);
        int tx = rx + 4;
        if (s.rowH >= 14) {
            ctx.drawItem(icon(w.id), rx + 2, ry + (s.rowH - 16) / 2);
            tx = rx + 22;
        }
        int maxW = (rx + rw - SWITCH_W - 4) - tx;
        String label = font.trimToWidth(w.label, maxW);
        ctx.drawTextWithShadow(font, Text.literal(label), tx, ry + (s.rowH - 8) / 2,
                w.enabled ? HudTheme.TEXT_PRIMARY : HudTheme.TEXT_TERTIARY);
        drawSwitch(ctx, rx + rw - SWITCH_W - 2, ry + (s.rowH - SWITCH_H) / 2, w.enabled);
    }

    /** Handle a click inside the Modules list. Returns the clicked widget (row body), or null. */
    HudWidget click(HudEditorScreen s, double mx, double my) {
        for (HudEditorScreen.Row r : s.tabContent[0].rows) {
            if (r.isHeader()) continue;
            int ry = s.tabTop() + r.y;
            HudWidget hit = clickModule(s, mx, my, r, r.module, r.x, r.w, ry);
            if (hit != null) {
                if (mx >= r.x + r.w - SWITCH_W - 2 && mx <= r.x + r.w) {
                    r.module.enabled = !r.module.enabled;
                    CustomHudOverlay.save();
                    return null;
                }
                return hit;
            }
            if (r.module2 != null) {
                hit = clickModule(s, mx, my, r, r.module2, r.x2, r.w2, ry);
                if (hit != null) {
                    if (mx >= r.x2 + r.w2 - SWITCH_W - 2 && mx <= r.x2 + r.w2) {
                        r.module2.enabled = !r.module2.enabled;
                        CustomHudOverlay.save();
                        return null;
                    }
                    return hit;
                }
            }
        }
        return null;
    }

    private HudWidget clickModule(HudEditorScreen s, double mx, double my, HudEditorScreen.Row r,
                                  HudWidget w, int rx, int rw, int ry) {
        if (w == null) return null;
        if (mx >= rx && mx <= rx + rw && my >= ry && my <= ry + s.rowH) return w;
        return null;
    }

    private static void drawSwitch(DrawContext ctx, int left, int top, boolean on) {
        ctx.fill(left, top, left + SWITCH_W, top + SWITCH_H, HudTheme.SWITCH_BG);
        ctx.fill(left + 1, top + 1, left + SWITCH_W - 1, top + SWITCH_H - 1, on ? HudTheme.ON : HudTheme.OFF);
        int knob = on ? left + SWITCH_W - SWITCH_H : left + 1;
        ctx.fill(knob + 1, top + 1, knob + SWITCH_H - 1, top + SWITCH_H - 1, HudTheme.SWITCH_KNOB);
    }

    static boolean categorized(String id) {
        for (String[] cat : CATS) {
            for (String s : cat[1].split(" ")) if (s.equals(id)) return true;
        }
        return false;
    }

    private static boolean match(HudWidget widget, String q) {
        if (q.isEmpty()) return true;
        return widget.id.toLowerCase(Locale.ROOT).contains(q) || widget.label.toLowerCase(Locale.ROOT).contains(q);
    }

    // ---- icon riêng cho từng module (item vanilla, không cần asset) ----

    private static java.util.HashMap<String, ItemStack> icons;
    private static ItemStack iconFallback;

    private static ItemStack icon(String id) {
        if (icons == null) {
            iconFallback = new ItemStack(Items.PAPER);
            icons = new java.util.HashMap<>();
            icons.put("fps", new ItemStack(Items.RABBIT_FOOT));
            icons.put("coords", new ItemStack(Items.COMPASS));
            icons.put("ping", new ItemStack(Items.REDSTONE));
            icons.put("ip", new ItemStack(Items.NAME_TAG));
            icons.put("time", new ItemStack(Items.CLOCK));
            icons.put("sprint", new ItemStack(Items.SUGAR));
            icons.put("armor", new ItemStack(Items.IRON_CHESTPLATE));
            icons.put("armor_head", new ItemStack(Items.IRON_HELMET));
            icons.put("armor_chest", new ItemStack(Items.IRON_CHESTPLATE));
            icons.put("armor_legs", new ItemStack(Items.IRON_LEGGINGS));
            icons.put("armor_feet", new ItemStack(Items.IRON_BOOTS));
            icons.put("armor_hand", new ItemStack(Items.IRON_SWORD));
            icons.put("ram", new ItemStack(Items.CHEST));
            icons.put("cpu", new ItemStack(Items.COMPARATOR));
            icons.put("gpu", new ItemStack(Items.ENDER_EYE));
            icons.put("hitbox", new ItemStack(Items.STRUCTURE_VOID));
            icons.put("crosshair", new ItemStack(Items.CROSSBOW));
            icons.put("biome", new ItemStack(Items.GRASS_BLOCK));
            icons.put("potion", new ItemStack(Items.POTION));
            icons.put("blockoverlay", new ItemStack(Items.GLASS));
            icons.put("freelook", new ItemStack(Items.SPYGLASS));
            icons.put("quickchat", new ItemStack(Items.WRITABLE_BOOK));
            icons.put("skins", new ItemStack(Items.LEATHER_CHESTPLATE));
        }
        ItemStack s = icons.get(id);
        return s == null ? iconFallback : s;
    }
}
