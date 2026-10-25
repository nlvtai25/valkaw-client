package mc.client.hud;

/**
 * Shared color and spacing tokens for the HUD manager/settings UI.
 * Minecraft has no true rounded rectangles; the radius values document the intent
 * and are approximated with inset borders / square corners where needed.
 */
final class HudTheme {
    private HudTheme() {}

    // ---- colors ----
    static final int PANEL_BG = 0xF0121216;
    static final int PANEL_BORDER = 0x40FFFFFF;
    static final int PANEL_SHADOW = 0x40000000;

    static final int ACCENT = 0xFFF59E0B;
    static final int ACCENT_HOVER = 0xFFFBBF24;
    static final int ACCENT_DIM = 0xFFD97706;
    static final int DANGER = 0xFFF85149;
    static final int DANGER_DIM = 0xFFB91C1C;
    // On/off switch palette: on = SUCCESS, off = OFF
    static final int SUCCESS = 0xFF3FB950;   // green
    static final int OFF = 0xFF3A3A44;       // dark
    static final int ON = SUCCESS;           // alias kept for ModulesTab.switch draw

    static final int TAB_SELECTED = ACCENT;
    static final int TAB_IDLE = 0xFF1C1C22;
    static final int TAB_HOVER_BG = 0xFF2E2E36;
    static final int TAB_HIGHLIGHT = 0x90FFFFFF;   // highlight edge of selected tab pill

    // rows / cards / toggles
    static final int ROW_HIGHLIGHT = 0x18FFFFFF;
    static final int CARD_BG = 0x901A1A1E;
    static final int CARD_BORDER = 0x60FFFFFF;
    static final int SWITCH_KNOB = 0xFFEDEDF2;
    static final int SWITCH_BG = 0xFF101014;

    static final int TEXT_PRIMARY = 0xFFEDEDF2;
    static final int TEXT_SECONDARY = 0xFF8A8F98;
    static final int TEXT_TERTIARY = 0xFF777B84;
    static final int ROW_HOVER = 0x22FFFFFF;
    static final int HEADER_LINE = 0x28FFFFFF;

    // ---- spacing ----
    static final int GAP_4 = 4;
    static final int GAP_8 = 8;
    static final int GAP_12 = 12;
    static final int GAP_16 = 16;
    static final int GAP_24 = 24;
    static final int GAP_32 = 32;

    static final int RADIUS_CONTROL = 6;   // advisory: buttons/text fields
    static final int RADIUS_PANEL = 10;    // advisory: panel/card corners

    // ---- panel geometry ----
    static final int PANEL_W = 400;
    static final int NAV_W = 104;
    static final int HEADER_H = 44;
    static final int TOP = 110;      // panel top -> list top
    static final int BOT = 40;       // list bottom -> panel bottom
    static final int NAV_ITEM_H = 26;
    static final int NAV_ITEM_GAP = 4;
    static final int TAB_Y = 22;
    static final int TAB_H = 16;
    static final int HDR_H = 12;     // section header height
    static final int MAX_ROWS = 11;  // module rows used to size the panel
    static final int ROW_MIN = 10;
    static final int ROW_MAX = 18;
    static final int CONTROL_H = 18; // button / text field height
    static final int ROW_CONTROL = 20; // control row height (CONTROL_H + 2px padding)
    static final int ROW_DETAIL = 22; // row with a text field (contains caption + input)

    // ---- shell surfaces ----
    static final int NAV_BG = 0xFF0E0E12;
    static final int NAV_SELECTED = 0xFF1E1E24;
    static final int NAV_HOVER = 0x18FFFFFF;
    static final int BORDER = 0x30FFFFFF;
}
