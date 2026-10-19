package mc.client.hud;

/** Self-check logic HUD (layout đồng đều, giới hạn màn hình, liên kết nhóm) — không cần font. */
public final class HudSelfCheck {

    private static void check(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException("HUD self-check FAILED: " + msg);
    }

    public static void main(String[] args) {
        GameState fake = new GameState() {
            public float fps() { return 144f; }
            public double px() { return 1.5; }
            public double py() { return 64.0; }
            public double pz() { return -3.25; }
            public int ping() { return 42; }
            public String serverIp() { return "play.example.com"; }
            public boolean sprinting() { return true; }
            public boolean autoSprint() { return false; }
        };

        HudManager m = HudManager.get();
        m.register(fake);
        m.screenW = 320;
        m.screenH = 240;
        check(m.size() == 14, "phải có 14 module");

        // giả lập kích thước (khỏi cần font) để test layout/clamp
        for (int i = 0; i < m.size(); i++) {
            HudWidget w = m.get(i);
            w.width = 80; w.height = 14;
        }
        m.autoLayout();

        HudWidget coords = m.byId("coords");
        HudWidget ping = m.byId("ping");
        check(ping.y == coords.y + coords.height + HudManager.GAP, "khoảng cách không đồng đều");

        // clamp góc phải-dưới
        m.move(ping, 9999, 9999);
        check(ping.x + ping.width <= 320, "tràn phải");
        check(ping.y + ping.height <= 240, "tràn dưới");
        // clamp góc trái-trên
        m.move(ping, -9999, -9999);
        check(ping.x >= 0 && ping.y >= 0, "tràn trái/trên");

        // link: ping (cha) trên, coords (con) dưới
        m.link(ping, coords);
        check(coords.group == ping.group && coords.group != -1, "link thất bại");
        check(Math.abs(coords.y - (ping.y + ping.height + HudManager.GAP)) <= 1, "vị trí sau link sai");

        // move nhóm: cả 2 cùng dịch chuyển
        int beforeY = coords.y;
        m.move(coords, 0, 10);
        check(coords.y == beforeY + 10, "nhóm không di chuyển cùng nhau");

        // unlink
        m.unlink(coords);
        check(coords.group == -1, "unlink thất bại");
        check(ping.group == -1, "nhóm còn 1 module phải tự giải tán");

        check(Widgets.prettify("plains").equals("Plains"), "prettify plains");
        check(Widgets.prettify("snowy_taiga").equals("Snowy Taiga"), "prettify snowy_taiga");
        check(Widgets.prettify("the_void").equals("The Void"), "prettify the_void");

        System.out.println("HUD self-check OK (" + m.size() + " module)");
    }
}
