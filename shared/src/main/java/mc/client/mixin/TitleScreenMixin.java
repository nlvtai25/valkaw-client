package mc.client.mixin;

import java.util.ArrayList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import mc.client.hud.CustomHudOverlay;
import mc.client.hud.HudEditorScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/**
 * Ẩn 2 nút icon Language/Accessibility trên màn hình chính.
 * Ẩn luôn nút Realms (offline session -> bấm vào báo "Invalid session") và đặt
 * nút "Client Settings" đúng vào ô đó.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void valkaw$tweakTitle(CallbackInfo ci) {
        TitleScreen self = (TitleScreen) (Object) this;
        ScreenInvoker invoker = (ScreenInvoker) (Object) self;
        ButtonWidget multiplayer = null;
        ButtonWidget realms = null;
        Text mpText = Text.translatable("menu.multiplayer");
        Text realmsText = Text.translatable("menu.online");
        for (Element e : new ArrayList<Element>(self.children())) {
            if (e instanceof ClickableWidget w && w.getWidth() == 20 && w.getHeight() == 20) {
                invoker.valkaw$remove(e);
            }
            if (e instanceof ButtonWidget b) {
                if (b.getMessage().equals(mpText)) {
                    multiplayer = b;
                } else if (b.getMessage().equals(realmsText)) {
                    realms = b;
                    invoker.valkaw$remove(b);   // khong de nut Realms chiem o + bat Invalid session
                }
            }
        }
        if (multiplayer == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        int x = multiplayer.getX();
        int y = multiplayer.getY() + 24;
        if (realms != null) { x = realms.getX(); y = realms.getY(); }
        ButtonWidget clientSettings = ButtonWidget.builder(Text.literal("Client Settings"),
                b -> mc.setScreen(new HudEditorScreen(CustomHudOverlay::save, self)))
                .dimensions(x, y, multiplayer.getWidth(), multiplayer.getHeight())
                .build();
        invoker.valkaw$addDrawableChild(clientSettings);
    }
}
