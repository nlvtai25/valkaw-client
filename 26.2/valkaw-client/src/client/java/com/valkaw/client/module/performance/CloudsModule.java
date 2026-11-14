package com.valkaw.client.module.performance;

import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;

/** Cycles the vanilla cloud setting (OFF / FAST / FANCY). */
public class CloudsModule extends Module {
	public CloudsModule() {
		super("clouds", "Clouds", Category.PERFORMANCE, true);
	}

	@Override
	public void onKey() {
		Minecraft mc = Minecraft.getInstance();
		CloudStatus[] values = CloudStatus.values();
		CloudStatus current = mc.options.cloudStatus().get();
		mc.options.cloudStatus().set(values[(current.ordinal() + 1) % values.length]);
	}

	@Override
	public String status() {
		return Minecraft.getInstance().options.cloudStatus().get().name();
	}

	@Override
	public java.util.List<com.valkaw.client.module.Setting> settings() {
		return java.util.List.of(com.valkaw.client.module.Setting.choice("MODE", () -> status(), this::onKey));
	}
}