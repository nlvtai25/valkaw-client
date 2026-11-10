package com.valkaw.client.module.performance;

import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ParticleStatus;

/** Cycles the vanilla particle setting (ALL / DECREASED / MINIMAL - vanilla has no OFF). */
public class ParticlesModule extends Module {
	public ParticlesModule() {
		super("particles", "Particles", Category.PERFORMANCE, true);
	}

	@Override
	public void onKey() {
		Minecraft mc = Minecraft.getInstance();
		ParticleStatus[] values = ParticleStatus.values();
		ParticleStatus current = mc.options.particles().get();
		mc.options.particles().set(values[(current.ordinal() + 1) % values.length]);
	}

	@Override
	public String status() {
		return Minecraft.getInstance().options.particles().get().name();
	}

	@Override
	public java.util.List<com.valkaw.client.module.Setting> settings() {
		return java.util.List.of(com.valkaw.client.module.Setting.choice("MODE", () -> status(), this::onKey));
	}
}