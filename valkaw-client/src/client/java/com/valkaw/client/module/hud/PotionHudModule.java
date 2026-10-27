package com.valkaw.client.module.hud;

import com.google.gson.JsonObject;
import com.valkaw.client.hud.Hud;
import com.valkaw.client.module.HudModule;
import com.valkaw.client.module.Setting;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.effect.MobEffectInstance;

public class PotionHudModule extends HudModule {
	private static final int MAX_LINES = 6;

	private boolean fancy;

	public PotionHudModule() {
		super("potions", "Potion HUD", 120, 34);
	}

	@Override
	protected int previewWidth() {
		return 90;
	}

	@Override
	protected int previewHeight() {
		return 26;
	}

	@Override
	public void render(GuiGraphics graphics, Font font) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			w = 0;
			h = 0;
			return;
		}
		Collection<MobEffectInstance> effects = player.getActiveEffects();
		if (effects.isEmpty()) {
			w = 0;
			h = 0;
			return;
		}
		int shown = Math.min(MAX_LINES, effects.size());
		int lineH = fancy ? 22 : 11;
		String header = Hud.label("POTION");
		int boxW = 8 + Hud.width(font, header);
		for (MobEffectInstance effect : effects) {
			boxW = Math.max(boxW, (fancy ? 24 : 4) + Hud.width(font, label(effect)) + 6
					+ Hud.width(font, value(effect)));
		}
		boxW = Math.max(boxW, 74);
		int boxH = 15 + shown * lineH;
		graphics.fill(x, y, x + boxW, y + boxH, Hud.BG);
		graphics.fill(x, y, x + 1, y + boxH, Hud.color());
		Hud.text(graphics, font, header, x + 4, y + 2);
		graphics.fill(x + 4, y + 12, x + 4 + Hud.width(font, header), y + 13, Hud.color());

		int line = 0;
		for (MobEffectInstance effect : effects) {
			if (line >= shown) {
				break;
			}
			int lineY = y + 16 + line * lineH;
			int textX = x + 4;
			if (fancy) {
				Hud.text(graphics, font, label(effect), x + 23, lineY + 3);
				Hud.text(graphics, font, value(effect), x + 23 + Hud.width(font, label(effect)) + 4, lineY + 3);
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
						Gui.getMobEffectSprite(effect.getEffect()), x + 3, lineY, 18, 18);
			} else {
				Hud.text(graphics, font, label(effect), textX, lineY);
				int separator = textX + Hud.width(font, label(effect)) + 3;
				Hud.text(graphics, font, "|", separator, lineY);
				Hud.text(graphics, font, value(effect), separator + Hud.width(font, "|") + 3, lineY);
			}
			line++;
		}
		w = boxW;
		h = boxH;
	}

	private static String label(MobEffectInstance effect) {
		String name = effect.getEffect().value().getDisplayName().getString();
		return name.length() > 14 ? name.substring(0, 14) : name;
	}

	private static String value(MobEffectInstance effect) {
		String amplifier = switch (effect.getAmplifier()) {
			case 0 -> "";
			case 1 -> "II ";
			case 2 -> "III ";
			default -> (effect.getAmplifier() + 1) + " ";
		};
		if (effect.isInfiniteDuration()) {
			return amplifier + "∞";
		}
		int seconds = Math.max(0, effect.getDuration() / 20);
		int minutes = seconds / 60;
		int rest = seconds % 60;
		if (minutes >= 60) {
			return amplifier + String.format("%02d:%02d:%02d", minutes / 60, minutes % 60, rest);
		}
		return amplifier + String.format("%02d:%02d", minutes, rest);
	}

	@Override
	public List<Setting> settings() {
		return List.of(Setting.choice("STYLE", () -> fancy ? "FANCY" : "SIMPLE", () -> fancy = !fancy));
	}

	@Override
	public void writeExtra(JsonObject json) {
		super.writeExtra(json);
		json.addProperty("fancy", fancy);
	}

	@Override
	public void readExtra(JsonObject json) {
		super.readExtra(json);
		if (json.has("fancy")) {
			fancy = json.get("fancy").getAsBoolean();
		}
	}
}
