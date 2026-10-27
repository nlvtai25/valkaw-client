package com.valkaw.client.module.hud;

import com.valkaw.client.hud.Hud;
import com.valkaw.client.module.HudModule;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;

/** Samples the client world game-time to estimate server ticks per second. */
public class TpsModule extends HudModule {
	private long lastSample;
	private long lastTick;
	private double tps = 20.0;
	private String text = "20.0";

	public TpsModule() {
		super("tps", "TPS", 4, 225);
		setEnabled(true);
	}

	@Override
	public void render(GuiGraphics graphics, Font font) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			lastSample = 0;
			w = 0;
			h = 0;
			return;
		}
		long now = System.currentTimeMillis();
		long tick = level.getGameTime();
		if (lastSample == 0 || tick < lastTick) {
			lastSample = now;
			lastTick = tick;
		} else if (now - lastSample >= 500L) {
			double seconds = (now - lastSample) / 1000.0;
			tps = Math.max(0.0, Math.min(20.0, (tick - lastTick) / seconds));
			lastTick = tick;
			lastSample = now;
			text = String.format(Locale.ROOT, "%.1f", tps);
		}
		w = Hud.chip(graphics, font, x, y, "TPS", text);
		h = 11;
	}
}
