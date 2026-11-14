package com.valkaw.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 26.2 removed the Fabric keybinding helper, and {@code KeyMapping} has no public
 * getter for the currently bound key. This exposes the protected {@code key} field
 * to replace {@code KeyBindingHelper.getBoundKeyOf(...)}.
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
	@Accessor("key")
	InputConstants.Key valkaw$getBoundKey();
}
