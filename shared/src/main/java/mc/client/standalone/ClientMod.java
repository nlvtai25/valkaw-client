package mc.client.standalone;

import java.nio.file.Path;

import mc.client.hud.CustomHudOverlay;
import mc.client.hud.BlockOverlayWidget;
import mc.client.hud.HudEditorScreen;
import mc.client.hud.HudManager;
import mc.client.hud.HitboxWidget;
import mc.client.hud.HudWidget;
import mc.client.hud.HudConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;

import org.lwjgl.glfw.GLFW;

/** Entry point Fabric client: đăng ký HUD vào render loop + HUD Manager/Editor. */
public final class ClientMod implements ClientModInitializer {
    private final MinecraftGame game = new MinecraftGame();
    private boolean weHeldSprint = false;
    private boolean hudEditorHeld = false;
    private boolean freelookKeyDown = false;

    @Override
    public void onInitializeClient() {
        Path cfg = FabricLoader.getInstance().getConfigDir().resolve("mcclient-hud.cfg");
        CustomHudOverlay.init(game, cfg);
        game.setAutoSprint(HudConfig.autoSprint);
        HudRenderCallback.EVENT.register(this::renderHud);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        BlockOverlayWidget.registerEvents();
        HitboxWidget.registerEvents();
        // Set window title + icon once on first tick
        final boolean[] titled = {false};
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!titled[0] && mc.getWindow() != null) {
                // Tiêu đề cửa sổ do SimpleMenu đặt từ config/simplemenu.json5 (customWindowTitle)
                // - một nguồn duy nhất, tránh lệch version như trước.
                try {
                    java.io.InputStream is = ClientMod.class.getResourceAsStream("/assets/valkaw-std/logo.jpg");
                    if (is != null) {
                        byte[] raw = is.readAllBytes(); is.close();
                        org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush();
                        java.nio.ByteBuffer buf = org.lwjgl.system.MemoryUtil.memAlloc(raw.length);
                        buf.put(raw).flip();
                        int[] w = {0}, h = {0}, c = {0};
                        java.nio.ByteBuffer pixels = org.lwjgl.stb.STBImage.stbi_load_from_memory(buf, w, h, c, 4);
                        if (pixels != null) {
                            org.lwjgl.glfw.GLFWImage.Buffer icons = org.lwjgl.glfw.GLFWImage.malloc(1, stack);
                            icons.position(0).width(w[0]).height(h[0]).pixels(pixels);
                            org.lwjgl.glfw.GLFW.glfwSetWindowIcon(mc.getWindow().getHandle(), icons);
                            org.lwjgl.stb.STBImage.stbi_image_free(pixels);
                        }
                        org.lwjgl.system.MemoryUtil.memFree(buf);
                        stack.close();
                    }
                } catch (Exception e) { System.err.println("[valkaw] icon load failed: " + e); }
                titled[0] = true;
            }
        });
        System.out.println("[mcclient] HUD registered: FPS, Coords, Ping, IP, Time, Sprint, Armor, RAM, CPU, GPU, Hitbox, Crosshair, Biome, Potion — BlockOverlay — keybinds: Options > Controls > Valkaw");
    }

    private void renderHud(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.options.hudHidden) return;
        if (mc.currentScreen instanceof HudEditorScreen) return;
        CustomHudOverlay.render(ctx, 1.0f);
    }

    private void tick(MinecraftClient mc) {
        if (mc == null) return;
        DiscordRpc.tick(mc);
        if (mc.player == null) return;

        // Quick Chat: gửi 1 dòng khi bấm tổ hợp (bật/tắt ở module quickchat)
        HudWidget qcW = HudManager.get().byId("quickchat");
        if (qcW != null && qcW.enabled) QuickChat.tick(mc);

        // HUD Editor: mac dinh bind Right Shift (phim modifier). KeyMapping.wasPressed() khong bao gio
        // bat voi phim modifier -> tu bat canh len cua isPressed().
        boolean editorHeld = Keybinds.HUD_EDITOR.isPressed();
        if (editorHeld && !hudEditorHeld && mc.currentScreen == null) {
            mc.setScreen(new HudEditorScreen(CustomHudOverlay::save));
        }
        hudEditorHeld = editorHeld;

        // Auto-Sprint: bật/tắt (tự sprint khi di chuyển tới)
        boolean toggleSprint = Keybinds.AUTO_SPRINT.wasPressed();
        if (toggleSprint && mc.currentScreen == null) {
            game.setAutoSprint(!game.autoSprint());
            HudConfig.autoSprint = game.autoSprint();
            CustomHudOverlay.save();
        }

        // Auto-sprint: giữ "ảo" phím sprint khi bật => game tự chạy logic sprint
        // (đi tới, đủ thức ăn...). Chỉ nhả khi chính ta đang giữ, không đánh nhau với Ctrl thật.
        boolean auto = game.autoSprint();
        if (auto && mc.currentScreen == null && mc.player != null) {
            if (!mc.options.sprintKey.isPressed()) {
                mc.options.sprintKey.setPressed(true);
                weHeldSprint = true;
            }
        } else if (weHeldSprint) {
            mc.options.sprintKey.setPressed(false);
            weHeldSprint = false;
        }

        // Freelook: phím đổi trong Options > Controls > Valkaw (mặc định F4). Giữ để xoay camera.
        // Chỉ hoạt động khi module "freelook" được bật trong HUD Manager.
        HudWidget flW = HudManager.get().byId("freelook");
        boolean alt = flW != null && flW.enabled
                && Keybinds.FREELOOK.isPressed()
                && mc.currentScreen == null;
        if (alt && !freelookKeyDown && mc.currentScreen == null) {
            Freelook.start(mc.player);
        }
        if (!alt && freelookKeyDown) {
            Freelook.stop();
        }
        freelookKeyDown = alt;

        // Block Overlay: điều chỉnh độ dày bằng ↑ / ↓ khi bật (bước 0.5).
        // Chỉ khi không mở màn hình nào — để ↑/↓ còn dùng sửa chat / lore item.
        HudWidget boW = HudManager.get().byId("blockoverlay");
        if (mc.currentScreen == null && boW != null && boW.enabled && boW instanceof BlockOverlayWidget bo) {
            if (InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_UP)) {
                bo.thickness = Math.min(10f, bo.thickness + 0.5f);
            }
            if (InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_DOWN)) {
                bo.thickness = Math.max(0.5f, bo.thickness - 0.5f);
            }
        }
    }
}
