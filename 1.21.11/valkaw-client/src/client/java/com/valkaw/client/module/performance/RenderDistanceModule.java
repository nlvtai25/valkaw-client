package com.valkaw.client.module.performance;

import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;
import net.minecraft.client.Minecraft;

/** Cycles a small render distance preset through the vanilla option. */
public class RenderDistanceModule extends Module {
	private static final int[] PRESETS = {6, 8, 12, 16};

	public RenderDistanceModule() {
		super("render-distance", "Render Distance", Category.PERFORMANCE, true);
	}

	@Override
	public void onKey() {
		Minecraft mc = Minecraft.getInstance();
		int current = mc.options.renderDistance().get();
		int next = PRESETS[0];
		for (int preset : PRESETS) {
			if (preset > current) {
				next = preset;
				break;
			}
		}
		mc.options.renderDistance().set(next);
	}

	@Override
	public String status() {
		return Minecraft.getInstance().options.renderDistance().get() + " CHUNKS";
	}

	@Override
	public java.util.List<com.valkaw.client.module.Setting> settings() {
		return java.util.List.of(com.valkaw.client.module.Setting.choice("DISTANCE", () -> status(), this::onKey));
	}
}