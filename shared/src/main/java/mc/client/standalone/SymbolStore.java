package mc.client.standalone;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Bộ symbol cho ô chat.
 * - Mặc định: lấy từ assets/valkaw-std/symbols.txt trong mod (bộ symbol của Symbol Chat,
 *   chia theo tab; tab có dấu '*' ở tiêu đề là chế độ 1 symbol/dòng, ví dụ kaomoji).
 * - Symbol riêng: thêm vào config/valkaw-symbols.txt (mỗi dòng 1 symbol) -> hiện ở tab "Custom".
 */
public final class SymbolStore {

    public static final class Category {
        public final String name;
        public final List<String> symbols;
        public final boolean lineMode;

        Category(String name, List<String> symbols, boolean lineMode) {
            this.name = name;
            this.symbols = symbols;
            this.lineMode = lineMode;
        }
    }

    private static final List<Category> CATEGORIES = new ArrayList<>();
    private static boolean loaded;

    private SymbolStore() { }

    public static List<Category> get() {
        if (!loaded) load();
        return CATEGORIES;
    }

    private static void load() {
        loaded = true;

        Map<String, List<String>> map = new LinkedHashMap<>();
        Map<String, Boolean> lineMode = new LinkedHashMap<>();
        String current = null;

        try (InputStream in = SymbolStore.class.getResourceAsStream("/assets/valkaw-std/symbols.txt")) {
            if (in != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    if (line.charAt(0) == '#') {
                        boolean line2 = line.endsWith("*");
                        current = line2 ? line.substring(1, line.length() - 1).trim() : line.substring(1).trim();
                        lineMode.put(current, line2);
                        if (!map.containsKey(current)) map.put(current, new ArrayList<String>());
                    } else if (current != null) {
                        map.get(current).add(line.trim());
                    }
                }
            }
        } catch (Exception ignored) { }

        // Symbol người dùng tự thêm -> tab "Custom".
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve("valkaw-symbols.txt");
            if (!Files.exists(p)) {
                Files.createDirectories(p.getParent());
                Files.write(p, "# Them symbol rieng: moi dong 1 symbol. Dong bat dau bang # bi bo qua.\n"
                    .getBytes(StandardCharsets.UTF_8));
            }
            List<String> custom = new ArrayList<>();
            for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                String s = line.trim();
                if (s.isEmpty() || s.charAt(0) == '#') continue;
                custom.add(s);
            }
            if (!custom.isEmpty()) {
                map.put("Custom", custom);
                lineMode.put("Custom", false);
            }
        } catch (Exception ignored) { }

        for (Map.Entry<String, List<String>> e : map.entrySet()) {
            if (!e.getValue().isEmpty()) {
                Boolean lm = lineMode.get(e.getKey());
                CATEGORIES.add(new Category(e.getKey(), e.getValue(), lm != null && lm));
            }
        }
    }
}
