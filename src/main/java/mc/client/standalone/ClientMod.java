package mc.client.standalone;

import java.nio.file.Path;

import mc.client.hud.CustomHudOverlay;
import mc.client.hud.BlockOverlayWidget;
import mc.client.hud.HudEditorScreen;
import mc.client.hud.HudManager;
import mc.client.hud.HitboxWidget;
import mc.client.hud.HudWidget;

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
    private boolean editorKeyDown = false;
    private boolean sprintToggleKeyDown = false;
    private boolean weHeldSprint = false;
    private boolean freelookKeyDown = false;

    @Override
    public void onInitializeClient() {
        Path cfg = FabricLoader.getInstance().getConfigDir().resolve("mcclient-hud.cfg");
        CustomHudOverlay.init(game, cfg);
        HudRenderCallback.EVENT.register(this::renderHud);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        BlockOverlayWidget.registerEvents();
        HitboxWidget.registerEvents();
        // Set window title + icon once on first tick
        final boolean[] titled = {false};
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!titled[0] && mc.getWindow() != null) {
                mc.getWindow().setTitle("Valkaw 1.21.11 v1.0");
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
        System.out.println("[mcclient] HUD registered: FPS, Coords, Ping, IP, Time, Sprint, Armor, RAM, CPU, GPU, Hitbox, Crosshair, Biome, Potion — BlockOverlay — Freelook:F4");
    }

    private void renderHud(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.options.hudHidden) return;
        if (mc.currentScreen instanceof HudEditorScreen) return;
        CustomHudOverlay.render(ctx, 1.0f);
    }

    private void tick(MinecraftClient mc) {
        if (mc == null || mc.player == null) return;

        // Right Shift: mở HUD Manager/Editor
        boolean down = InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
        if (down && !editorKeyDown && mc.currentScreen == null) {
            mc.setScreen(new HudEditorScreen(CustomHudOverlay::save));
        }
        editorKeyDown = down;

        // Z: bật/tắt Auto-Sprint (tự sprint khi di chuyển tới)
        boolean z = InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_Z);
        if (z && !sprintToggleKeyDown && mc.currentScreen == null) {
            game.setAutoSprint(!game.autoSprint());
        }
        sprintToggleKeyDown = z;

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

        // F4: giữ để kích hoạt freelook (xoay camera tự do, nhân vật giữ hướng cũ)
        boolean alt = InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_F4);
        if (alt && !freelookKeyDown && mc.currentScreen == null) {
            Freelook.start(mc.player);
        }
        if (!alt && freelookKeyDown) {
            Freelook.stop();
        }
        freelookKeyDown = alt;

        // Áp freelook yaw/pitch lên render (sau player tick, trước render)
        Freelook.apply(mc.player);

        // Block Overlay: điều chỉnh độ dày bằng ↑ / ↓ khi bật (bước 0.5)
        HudWidget boW = HudManager.get().byId("blockoverlay");
        if (boW != null && boW.enabled && boW instanceof BlockOverlayWidget bo) {
            if (InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_UP)) {
                bo.thickness = Math.min(10f, bo.thickness + 0.5f);
            }
            if (InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_DOWN)) {
                bo.thickness = Math.max(0.5f, bo.thickness - 0.5f);
            }
        }
    }
}