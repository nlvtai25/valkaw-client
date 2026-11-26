package com.valkaw.client;

import com.valkaw.client.keybinds.Keybinds;
import com.valkaw.client.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public class ValkawClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModuleManager.get().init();
		Keybinds.register();
		ClientTickEvents.START_CLIENT_TICK.register(client -> ModuleManager.get().tick());
		SnapShot.register();
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

	/** Window title: Valkaw <profile> <mc> <version>, e.g. Valkaw Sodium 1.21.11 v1.0.6.3. */
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
