package mc.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Hitbox: vẽ khung hộp quanh entity (thay cho F3+B), màu + độ dày chỉnh trong setting.
 * Không vẽ cho entity invisible (kể cả vanish của server) và người chơi spectator.
 */
public final class HitboxWidget extends ColorBoxWidget {
    public HitboxWidget() { super("hitbox", "Hitbox"); this.enabled = false; this.lineColor = 0xFFFFFFFF; }

    public static void registerEvents() { WorldRenderEvents.BEFORE_DEBUG_RENDER.register(HitboxWidget::onRender); }

    private static void onRender(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return;
        if (mc.options.hudHidden) return;
        HudWidget w = HudManager.get().byId("hitbox");
        if (!(w instanceof HitboxWidget hb) || !hb.enabled || hb.thickness <= 0f) return;
        if (mc.player.isSpectator()) return;   // mình đang spectator -> khỏi vẽ
        MatrixStack matrices = ctx.matrices();
        if (matrices == null) return;
        Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos();
        VertexConsumer vc = ctx.consumers() != null ? ctx.consumers().getBuffer(RenderLayers.lines()) : null;
        if (vc == null) return;

        int c = hb.renderColor(System.currentTimeMillis());
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;

        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || e.isRemoved()) continue;
            if (e.isInvisible()) continue;                                   // Invisible / vanish plugin
            if (e instanceof PlayerEntity p && p.isSpectator()) continue;    // Spectator
            Box box = e.getBoundingBox().expand(0.0015D);
            BlockOverlayWidget.drawBox(matrices.peek(), vc, box, hb.thickness, r, g, b, 1f);
        }
        matrices.pop();
    }
}
