package mc.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class BlockOverlayWidget extends ColorBoxWidget {
    public BlockOverlayWidget() {
        super("blockoverlay", "Block Overlay");
        this.enabled = false;
        this.minThickness = 10f;
    }

    public static void registerEvents() { WorldRenderEvents.BEFORE_DEBUG_RENDER.register(BlockOverlayWidget::onRender); }

    private static void onRender(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return;
        HudWidget w = HudManager.get().byId("blockoverlay");
        if (w == null || !w.enabled) return;
        if (!(w instanceof BlockOverlayWidget bo)) return;
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return;
        BlockHitResult hit = (BlockHitResult) mc.crosshairTarget;
        BlockPos pos = hit.getBlockPos();
        var state = mc.world.getBlockState(pos);
        if (state.isAir()) return;
        Box box = state.getOutlineShape(mc.world, pos, net.minecraft.block.ShapeContext.of(mc.player)).getBoundingBox().expand(0.0015D);
        MatrixStack matrices = ctx.matrices();
        if (matrices == null) return;
        Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos();
        matrices.push();
        matrices.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
        int c = bo.renderColor(System.currentTimeMillis());
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        float a = bo.colorMode == 1 ? ((c >>> 24) & 0xFF) / 255f : 0.40F;
        VertexConsumer vc = ctx.consumers() != null ? ctx.consumers().getBuffer(RenderLayers.lines()) : null;
        if (vc != null) drawBox(matrices.peek(), vc, box, bo.thickness, r, g, b, a);
        matrices.pop();
    }

    static void drawBox(MatrixStack.Entry entry, VertexConsumer vc, Box box, float thickness, int r, int g, int b, float a) {
        float x1=(float)box.minX,x2=(float)box.maxX,y1=(float)box.minY,y2=(float)box.maxY,z1=(float)box.minZ,z2=(float)box.maxZ;
        line(entry,vc,x1,y1,z1,x2,y1,z1,r,g,b,a,thickness); line(entry,vc,x2,y1,z1,x2,y1,z2,r,g,b,a,thickness);
        line(entry,vc,x2,y1,z2,x1,y1,z2,r,g,b,a,thickness); line(entry,vc,x1,y1,z2,x1,y1,z1,r,g,b,a,thickness);
        line(entry,vc,x1,y2,z1,x2,y2,z1,r,g,b,a,thickness); line(entry,vc,x2,y2,z1,x2,y2,z2,r,g,b,a,thickness);
        line(entry,vc,x2,y2,z2,x1,y2,z2,r,g,b,a,thickness); line(entry,vc,x1,y2,z2,x1,y2,z1,r,g,b,a,thickness);
        line(entry,vc,x1,y1,z1,x1,y2,z1,r,g,b,a,thickness); line(entry,vc,x2,y1,z1,x2,y2,z1,r,g,b,a,thickness);
        line(entry,vc,x2,y1,z2,x2,y2,z2,r,g,b,a,thickness); line(entry,vc,x1,y1,z2,x1,y2,z2,r,g,b,a,thickness);
    }

    private static void line(MatrixStack.Entry entry, VertexConsumer vc, float x1,float y1,float z1,float x2,float y2,float z2,int r,int g,int b,float a,float thickness) {
        if (thickness <= 1f) { vc.vertex(entry,x1,y1,z1).color(r/255f,g/255f,b/255f,a).normal(0,0,0).lineWidth(1); vc.vertex(entry,x2,y2,z2).color(r/255f,g/255f,b/255f,a).normal(0,0,0).lineWidth(1); return; }
        float dx=x2-x1,dy=y2-y1,dz=z2-z1,len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz); if(len==0)return; dx/=len; dy/=len; dz/=len;
        float tx=0,ty=0,tz=0; if(Math.abs(dx)>Math.abs(dy)){tx=-dz;tz=dx;}else{ty=dz;tz=-dy;}
        float nlen=(float)Math.sqrt(tx*tx+ty*ty+tz*tz); if(nlen==0)return; tx/=nlen; ty/=nlen; tz/=nlen;
        int half=Math.max(1,Math.round(thickness/2f));
        for(int i=-half;i<=half;i++){ float off=i*0.0004f; vc.vertex(entry,x1+tx*off,y1+ty*off,z1+tz*off).color(r/255f,g/255f,b/255f,a).normal(0,0,0).lineWidth(1); vc.vertex(entry,x2+tx*off,y2+ty*off,z2+tz*off).color(r/255f,g/255f,b/255f,a).normal(0,0,0).lineWidth(1); }
    }
}
