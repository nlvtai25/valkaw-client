package com.valkaw.client.module.utility;

import com.valkaw.client.keybinds.Keybinds;
import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Hides the bodies of all other players. Nametags are intentionally kept
 * visible. The actual hiding is applied by LivingEntityRendererMixin.
 */
public class HidePlayersModule extends Module {
	public HidePlayersModule() {
		super("hideplayers", "Hide Players", Category.UTILITY);
	}

	/** Show an action-bar confirmation when toggled via its keybind. */
	@Override
	public void toggle() {
		boolean on = !isEnabled();
		super.toggle();
		if (Keybinds.down(this)) {
			String text = "§fHide Players: " + (on ? "§aON" : "§cOFF");
			Minecraft.getInstance().gui.setOverlayMessage(Component.literal(text), false);
		}
	}
}
