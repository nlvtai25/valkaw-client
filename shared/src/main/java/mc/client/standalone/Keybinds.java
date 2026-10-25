package mc.client.standalone;

import java.util.List;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;

import org.lwjgl.glfw.GLFW;

/**
 * Keybind của client, hiện trong Options > Controls > Valkaw.
 * Đăng ký = tạo KeyBinding với Category riêng; GameOptionsKeysMixin chèn chúng vào
 * {@code GameOptions.allKeys} ngay sau khi mảng đó được dựng, nên options.txt tự lưu/đọc.
 */
public final class Keybinds {
    /** Nhãn mục: key.category.valkaw.main (assets/valkaw-std/lang). */
    public static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("valkaw", "main"));

    public static final KeyBinding HUD_EDITOR  = bind("key.valkaw.hud_editor", GLFW.GLFW_KEY_RIGHT_SHIFT);
    public static final KeyBinding AUTO_SPRINT = bind("key.valkaw.auto_sprint", GLFW.GLFW_KEY_Z);
    public static final KeyBinding FREELOOK    = bind("key.valkaw.freelook", GLFW.GLFW_KEY_F4);
    public static final KeyBinding QUICK_CHAT  = bind("key.valkaw.quick_chat", GLFW.GLFW_KEY_UNKNOWN);

    /** Thứ tự hiện trong Controls. */
    public static final List<KeyBinding> ALL = List.of(HUD_EDITOR, AUTO_SPRINT, FREELOOK, QUICK_CHAT);

    private Keybinds() {}

    private static KeyBinding bind(String id, int code) {
        return new KeyBinding(id, InputUtil.Type.KEYSYM, code, CATEGORY);
    }

    /** Nối keybind của client vào mảng allKeys của vanilla (mixin gọi, chỉ 1 lần). */
    public static KeyBinding[] appendAll(KeyBinding[] base) {
        // idempotent: load() co the chay lai -> khong them trung
        java.util.List<KeyBinding> add = new java.util.ArrayList<>();
        for (KeyBinding kb : ALL) if (!java.util.Arrays.asList(base).contains(kb)) add.add(kb);
        if (add.isEmpty()) return base;
        KeyBinding[] out = java.util.Arrays.copyOf(base, base.length + add.size());
        for (int i = 0; i < add.size(); i++) out[base.length + i] = add.get(i);
        return out;
    }
}
