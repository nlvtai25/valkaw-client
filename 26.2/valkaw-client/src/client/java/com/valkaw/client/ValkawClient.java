package com.valkaw.client;

import com.valkaw.client.keybinds.Keybinds;
import com.valkaw.client.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;

public class ValkawClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModuleManager.get().init();
		Keybinds.register();
		ClientTickEvents.START_CLIENT_TICK.register(client -> ModuleManager.get().tick());
		SnapShot.register();
	}

	/**
	 * Resolves a ChatFormatting to its RGB value. 26.2 removed
	 * {@code ChatFormatting.getColor()}, so the color is reached through the
	 * style: {@code Style.withColor(cf).getColor().getValue()}. Returns null
	 * when the formatting carries no color (BOLD, ITALIC, RESET, ...), matching
	 * the old {@code getColor()} contract.
	 */
	public static Integer formattingRgb(ChatFormatting formatting) {
		if (formatting == null) {
			return null;
		}
		var color = Style.EMPTY.withColor(formatting).getColor();
		return color == null ? null : color.getValue();
	}

	/**
	 * Resolves the legacy format code character ('a', 'l', ...). 26.2 removed
	 * {@code ChatFormatting.getChar()}; the code is the last character of
	 * {@code toString()} (which is the prefix plus the code).
	 */
	public static char formattingCode(ChatFormatting formatting) {
		String s = formatting.toString();
		return s.isEmpty() ? '?' : s.charAt(s.length() - 1);
	}

	/**
	 * The running Minecraft version as reported by the Fabric loader, falling
	 * back to an empty string rather than a hardcoded label so the UI never
	 * advertises the wrong game version.
	 */
	public static String mcVersion() {
		return FabricLoader.getInstance().getModContainer("minecraft")
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("");
	}

	/** Window title: Valkaw <profile> <mc> <version>, e.g. Valkaw Sodium <mc> v1.0.6.2. */
	public static String windowTitle() {
		String profile = FabricLoader.getInstance().isModLoaded("vulkanmod") ? "Vulkan" : "Sodium";
		String mcVersion = mcVersion();
		String version = FabricLoader.getInstance().getModContainer("valkawclient")
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("1.0.1");
		if (!version.startsWith("v")) {
			version = "v" + version;
		}
		return "Valkaw " + profile + " " + mcVersion + " " + version;
	}
}
