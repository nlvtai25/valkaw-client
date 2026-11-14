package com.valkaw.client.mixin;

import com.mojang.blaze3d.platform.IconSet;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IconSet.class)
public class IconSetMixin {
	private static final String[] ICONS = {
			"icon_16x16.png", "icon_32x32.png", "icon_48x48.png", "icon_128x128.png", "icon_256x256.png"
	};

	/**
	 * Vanilla reads the window icon from the built-in pack only, so a mod jar can
	 * never replace it. Handing back our own icons makes the game window and the
	 * taskbar show the Valkaw logo.
	 */
	@Inject(method = "getStandardIcons", at = @At("RETURN"), cancellable = true)
	private void valkaw$windowIcon(PackResources pack, CallbackInfoReturnable<List<IoSupplier<InputStream>>> cir)
	{
		List<IoSupplier<InputStream>> icons = new ArrayList<>(ICONS.length);
		for (String icon : ICONS)
		{
			if (IconSetMixin.class.getResource("/assets/minecraft/icons/" + icon) == null)
			{
				return;
			}
			icons.add(() -> IconSetMixin.class.getResourceAsStream("/assets/minecraft/icons/" + icon));
		}
		cir.setReturnValue(icons);
	}
}
