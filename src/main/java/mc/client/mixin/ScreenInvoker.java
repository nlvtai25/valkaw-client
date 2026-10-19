package mc.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;

/** Cho phép thêm widget con vào Screen (addDrawableChild là protected). */
@Mixin(Screen.class)
public interface ScreenInvoker {
    @Invoker("addDrawableChild")
    <T extends Element & Drawable & Selectable> T valkaw$addDrawableChild(T widget);
}
