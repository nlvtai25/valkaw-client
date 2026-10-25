package mc.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import mc.client.standalone.SkinStore;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;

/** Skins/Capes: thay bộ skin hiển thị phía client (body + cape), giữ nguyên elytra/model. */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class SkinMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void valkaw$customSkin(CallbackInfoReturnable<SkinTextures> cir) {
        if (!SkinStore.hasCustom()) return;
        // chỉ áp cho nhân vật của mình, không đè skin người khác
        if ((Object) this != net.minecraft.client.MinecraftClient.getInstance().player) return;
        SkinTextures o = cir.getReturnValue();
        if (o == null) return;
        cir.setReturnValue(new SkinTextures(
                SkinStore.body() != null ? SkinStore.body() : o.body(),
                SkinStore.cape() != null ? SkinStore.cape() : o.cape(),
                o.elytra(), SkinStore.model() != null ? SkinStore.model() : o.model(), o.secure()));
    }
}
