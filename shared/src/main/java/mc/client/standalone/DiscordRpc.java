package mc.client.standalone;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Discord Rich Presence tối giản (named pipe của Discord trên Windows).
 * Chỉ gửi trạng thái chung — không gửi IP/tên server.
 * shortcut: chỉ pipe Windows + SET_ACTIVITY, không đọc reply; đổi sang thư viện nếu cần Linux.
 */
public final class DiscordRpc {
    /** Điền khi có thông tin từ Discord Dev Portal. */
    private static final String CLIENT_ID = "1053201589361446962";
    private static final String DETAILS = "Valkaw Client";

    public static volatile boolean enabled;
    public static volatile String status = "Off";

    private static volatile String state = "In menu";
    private static final long since = System.currentTimeMillis() / 1000L;
    private static volatile String sent = "";
    private static Thread worker;
    private static RandomAccessFile pipe;

    private DiscordRpc() {}

    /** Gọi mỗi client tick: cập nhật trạng thái + khởi động thread khi bật. */
    public static void tick(net.minecraft.client.MinecraftClient mc) {
        if (!enabled) { status = "Off"; return; }
        if (CLIENT_ID.isBlank()) { status = "Not configured"; return; }
        if (mc.world == null) state = "In menu";
        else if (mc.isInSingleplayer()) state = "Singleplayer";
        else state = "Multiplayer";
        if (worker == null || !worker.isAlive()) {
            worker = new Thread(DiscordRpc::loop, "valkaw-discord-rpc");
            worker.setDaemon(true);
            worker.start();
        }
    }

    private static void loop() {
        while (enabled) {
            try {
                if (pipe == null) connect();
                String want = CLIENT_ID + "\u0000" + state;
                if (!want.equals(sent)) {
                    activity();
                    sent = want;
                    status = "Connected";
                }
            } catch (Exception e) {
                close();
                sent = "";
                status = "No Discord";
            }
            try { Thread.sleep(1000L); } catch (InterruptedException e) { return; }
        }
        close();
    }

    private static void connect() throws Exception {
        for (int i = 0; i < 10; i++) {
            try {
                pipe = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                send(0, "{\"v\":1,\"client_id\":\"" + CLIENT_ID + "\"}");
                return;
            } catch (Exception ignored) { /* thử pipe kế tiếp */ }
        }
        throw new IllegalStateException("no discord ipc");
    }

    private static void activity() throws Exception {
        String json = "{\"cmd\":\"SET_ACTIVITY\",\"args\":{\"pid\":" + ProcessHandle.current().pid()
                + ",\"activity\":{\"details\":\"" + esc(DETAILS) + "\",\"state\":\"" + esc(state) + "\""
                + ",\"timestamps\":{\"start\":" + since + "}"
                + ",\"assets\":{\"large_image\":\"main\"}}}"
                + ",\"nonce\":\"" + UUID.randomUUID() + "\"}";
        send(1, json);
    }

    private static void send(int op, String json) throws Exception {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(8 + b.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(op).putInt(b.length).put(b);
        pipe.write(buf.array());
    }

    private static void close() {
        try { if (pipe != null) pipe.close(); } catch (Exception ignored) { }
        pipe = null;
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
