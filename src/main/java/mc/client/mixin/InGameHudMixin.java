package mc.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import mc.client.hud.HudManager;
import mc.client.hud.HudWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;

/** Ẩn crosshair vanilla khi module Custom Crosshair đang bật (tránh bị chồng 2 crosshair). */
@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void mcclient$hideVanillaCrosshair(DrawContext ctx, RenderTickCounter tick, CallbackInfo ci) {
        HudWidget w = HudManager.get().byId("crosshair");
        if (w != null && w.enabled) ci.cancel();
    }
}
