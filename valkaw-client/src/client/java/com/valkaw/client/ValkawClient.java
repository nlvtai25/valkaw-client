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
	}

	/** Window title: Valkaw <profile> <version>, e.g. Valkaw Sodium v1.0.1. */
	public static String windowTitle() {
		String profile = FabricLoader.getInstance().isModLoaded("vulkanmod") ? "Vulkan" : "Sodium";
		String version = FabricLoader.getInstance().getModContainer("valkawclient")
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("1.0.1");
		if (!version.startsWith("v")) {
			version = "v" + version;
		}
		return "Valkaw " + profile + " " + version;
	}
}
