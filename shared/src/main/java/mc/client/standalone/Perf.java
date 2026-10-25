package mc.client.standalone;

/**
 * Toggle hiệu năng/hiển thị phía client. Lưu vào mcclient-hud.cfg dạng perf.*
 * Mặc định TẮT = hành vi vanilla.
 */
public final class Perf {
    /** true = ẩn tên trên đầu mỗi entity. */
    public static boolean hideNametag;
    /** true = tắt rung màn hình khi bị đánh. */
    public static boolean noHurtCam;
    /** true = ẩn lớp lửa phủ màn hình khi đang cháy. */
    public static boolean noFireOverlay;
    /** true = bỏ sương màu khi ở trong nước/dung nham. */
    public static boolean clearFluid;
    /** true = bỏ fog (sương mù) hoàn toàn. */
    public static boolean noFog;
    /** true = bỏ hiệu ứng tối màn hình của Darkness (Warden). */
    public static boolean noDarkness;
    /** true = bỏ lớp mờ khi đội bí ngô (camera overlay). */
    public static boolean noPumpkin;
    /** true = Infinity Chat History: giữ toàn bộ lịch sử chat thay vì 100 dòng như vanilla. */
    public static boolean infinityChat;

    /** Trần an toàn cho chat "vô hạn" (giữ RAM ở mức chấp nhận được). */
    public static final int CHAT_LIMIT = 10000;
    /** Giới hạn thực tế; đổi được qua config `perf.chatlimit=<n>`. */
    public static int chatLimit = CHAT_LIMIT;

    private Perf() {}

    public static boolean get(String key) {
        return switch (key) {
            case "nametag" -> hideNametag;
            case "hurtcam" -> noHurtCam;
            case "fire" -> noFireOverlay;
            case "clearfluid" -> clearFluid;
            case "nofog" -> noFog;
            case "nodarkness" -> noDarkness;
            case "nopumpkin" -> noPumpkin;
            case "chat" -> infinityChat;
            default -> false;
        };
    }

    /** Đọc `perf.chatlimit`: chặn giá trị vô lý (0 hoặc âm sẽ làm chat luôn rỗng). */
    public static void setChatLimit(int n) {
        chatLimit = Math.max(100, Math.min(1_000_000, n));
    }

    public static void set(String key, boolean value) {
        switch (key) {
            case "nametag" -> hideNametag = value;
            case "hurtcam" -> noHurtCam = value;
            case "fire" -> noFireOverlay = value;
            case "clearfluid" -> clearFluid = value;
            case "nofog" -> noFog = value;
            case "nodarkness" -> noDarkness = value;
            case "nopumpkin" -> noPumpkin = value;
            case "chat" -> infinityChat = value;
            default -> { }
        }
    }

    public static String dump() {
        return "perf.nametag=" + (hideNametag ? 1 : 0) + "\n"
             + "perf.hurtcam=" + (noHurtCam ? 1 : 0) + "\n"
             + "perf.fire=" + (noFireOverlay ? 1 : 0) + "\n"
             + "perf.clearfluid=" + (clearFluid ? 1 : 0) + "\n"
             + "perf.nofog=" + (noFog ? 1 : 0) + "\n"
             + "perf.nodarkness=" + (noDarkness ? 1 : 0) + "\n"
             + "perf.nopumpkin=" + (noPumpkin ? 1 : 0) + "\n"
             + "perf.chat=" + (infinityChat ? 1 : 0) + "\n"
             + "perf.chatlimit=" + chatLimit + "\n";
    }
}
