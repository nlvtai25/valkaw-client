package mc.client.standalone;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

/**
 * Skin/cape riêng phía client. Nguồn: file trong config/skins-local|capes-local,
 * URL PNG trực tiếp (skinmc...), hoặc username/URL namemc -> Mojang API.
 * Chỉ áp dụng cho máy này; người khác chỉ thấy nếu cũng chạy Valkaw.
 */
public final class SkinStore {
    public static String skinMode = "none";   // none | url | file | user
    public static String skinValue = "";
    public static String capeMode = "none";   // none | url | file
    public static String capeValue = "";

    private static AssetInfo.TextureAsset body;
    private static net.minecraft.entity.player.PlayerSkinType model;   // null = giữ model gốc
    private static AssetInfo.TextureAsset cape;
    private static Identifier bodyId, capeId;
    private static String status = "vanilla skin";
    /** Lỗi của lần đăng ký texture gần nhất (sai kích thước / PNG hỏng); null nếu OK. */
    private static String regError;
    private static final AtomicInteger SEQ = new AtomicInteger();

    private SkinStore() {}

    static {
        // tạo sẵn thư mục để user chỉ việc thả file PNG vào
        for (String k : new String[] { "skins", "capes" }) {
            try { Files.createDirectories(dir(k)); } catch (Exception ignored) { }
        }
    }

    public static String status() { return status; }

    public static boolean hasCustom() { return body != null || cape != null; }
    public static AssetInfo.TextureAsset body() { return body; }
    public static AssetInfo.TextureAsset cape() { return cape; }
    /** Model suy ra từ PNG của skin custom (null nếu không có skin custom). */
    public static net.minecraft.entity.player.PlayerSkinType model() { return model; }
    /** Texture đã đăng ký để vẽ preview (null nếu chưa có). */
    public static Identifier bodyId() { return bodyId; }
    public static Identifier capeId() { return capeId; }

    public static Path dir(String kind) {
        return FabricLoader.getInstance().getConfigDir().resolve(kind + "-local");
    }

    /** Tên file PNG trong skins-local / capes-local (tối đa 20, đã sắp xếp). */
    public static List<String> files(String kind) {
        List<String> out = new ArrayList<>();
        Path d = dir(kind);
        if (!Files.isDirectory(d)) return out;
        try (var s = Files.list(d)) {
            s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
             .sorted()
             .limit(20)
             .forEach(p -> out.add(p.getFileName().toString()));
        } catch (Exception e) { /* thư mục chưa có */ }
        return out;
    }

    /** Nạp lại skin/cape theo cấu hình hiện tại (gọi trong thread nền nếu có tải mạng). */
    public static void reload() {
        String sm = skinMode, sv = skinValue, cm = capeMode, cv = capeValue;
        status = "loading...";
        Thread t = new Thread(() -> {
            String err = null;
            byte[] skinBytes = null, capeBytes = null;
            if (!"none".equals(sm) && !sv.isBlank()) {
                try { skinBytes = resolve(sm, sv, "skins"); }
                catch (Exception e) { err = "skin: " + e.getMessage(); }
            }
            if (!"none".equals(cm) && !cv.isBlank()) {
                try { capeBytes = resolve(cm, cv, "capes"); }
                catch (Exception e) { err = "cape: " + e.getMessage(); }
            }
            final byte[] sb = skinBytes, cb = capeBytes;
            final String fe = err;
            MinecraftClient.getInstance().execute(() -> apply(sb, cb, fe));
        }, "valkaw-skin");
        t.setDaemon(true);
        t.start();
    }

    /** Áp texture đã tải (phải chạy trên render thread). */
    private static void apply(byte[] skinBytes, byte[] capeBytes, String err) {
        reset();
        regError = null;
        if (skinBytes != null) {
            Identifier id = register(skinBytes, 64, 64, "skin");
            if (id != null) { bodyId = id; body = tex(id); }
        }
        if (capeBytes != null) {
            Identifier id = register(capeBytes, 64, 32, "cape");
            if (id != null) { capeId = id; cape = tex(id); }
        }
        // register() ghi lỗi cụ thể (sai kích thước / PNG hỏng) -> ưu tiên hiện lỗi đó.
        if (err != null) status = err;
        else if (regError != null) status = regError;
        else if (body == null && cape == null) status = "vanilla skin";
        else status = (body != null ? "skin ok" : "no skin") + " / " + (cape != null ? "cape ok" : "no cape")
                + (model != null ? " / model: " + (model == net.minecraft.entity.player.PlayerSkinType.SLIM ? "slim" : "wide") : "");
    }

    public static void reset() {
        body = null;
        cape = null;
        model = null;
        bodyId = null;
        capeId = null;
    }

    /** AssetInfo.TextureAsset có 2 method (id, texturePath) nên phải anonymous class. */
    private static AssetInfo.TextureAsset tex(Identifier id) {
        return new AssetInfo.TextureAsset() {
            @Override public Identifier texturePath() { return id; }
            @Override public Identifier id() { return id; }
        };
    }

    private static Identifier register(byte[] png, int w, int h, String kind) {
        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
            if (kind.equals("cape") ? (img.getWidth() != 64 || (img.getHeight() != 32 && img.getHeight() != 64))
                                    : (img.getWidth() != 64 || img.getHeight() != 64)) {
                status = kind + ": wrong size " + img.getWidth() + "x" + img.getHeight();
                regError = status;
                img.close();
                return null;
            }
            Identifier id = Identifier.of("valkaw", "custom_" + kind + "_" + SEQ.incrementAndGet());
            MinecraftClient.getInstance().getTextureManager()
                    .registerTexture(id, new NativeImageBackedTexture(() -> "valkaw " + kind, img));
            if (kind.equals("skin")) model = detectModel(img);
            return id;
        } catch (Exception e) {
            status = kind + ": bad PNG";
            regError = status;
            return null;
        }
    }

    /** Skin slim để trống cột x=47 ở mặt trên tay phải (44..47,16..19); wide thì đặc. */
    private static net.minecraft.entity.player.PlayerSkinType detectModel(NativeImage img) {
        int clear = 0;
        for (int y = 16; y < 20; y++) {
            if ((img.getColorArgb(47, y) >>> 24) == 0) clear++;
        }
        return clear == 4 ? net.minecraft.entity.player.PlayerSkinType.SLIM
                          : net.minecraft.entity.player.PlayerSkinType.WIDE;
    }

    private static final Pattern PROFILE = Pattern.compile("/(?:profile|skin)/([A-Za-z0-9_]{3,16})");

    /** Trả về bytes PNG cho 1 nguồn. */
    private static byte[] resolve(String mode, String value, String kind) throws Exception {
        String v = value.trim();
        switch (mode) {
            case "file": {
                Path p = dir(kind).resolve(v);
                if (!Files.isRegularFile(p)) throw new Exception("not found: " + v);
                return Files.readAllBytes(p);
            }
            case "url":
                return download(stripQuery(v));
            case "user": {
                if (kind.equals("capes")) throw new Exception("username capes not supported");
                Matcher m = PROFILE.matcher(v);
                String name = m.find() ? m.group(1) : v;
                if (!name.matches("[A-Za-z0-9_]{3,16}")) throw new Exception("bad username");
                return download(mojangSkinUrl(name));
            }
            default:
                return null;
        }
    }

    /** Bỏ query string kiểu namemc (?width=...) để lấy file PNG gốc. */
    private static String stripQuery(String url) {
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q);
    }

    private static String mojangSkinUrl(String name) throws Exception {
        JsonObject u = getJson("https://api.mojang.com/users/profiles/minecraft/" + name);
        String uuid = u.get("id").getAsString();
        JsonObject prof = getJson("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
        String b64 = prof.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
        String decoded = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
        return JsonParser.parseString(decoded).getAsJsonObject()
                .getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
    }

    private static JsonObject getJson(String url) throws Exception {
        return JsonParser.parseString(new String(download(url), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static byte[] download(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "ValkawClient/1.0");
        try (InputStream in = c.getInputStream()) {
            return in.readAllBytes();
        } finally {
            c.disconnect();
        }
    }
}
