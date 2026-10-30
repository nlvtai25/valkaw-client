package com.valkaw.client.module.hud;

import com.google.gson.JsonObject;
import com.valkaw.client.hud.Hud;
import com.valkaw.client.module.HudModule;
import com.valkaw.client.module.Layout;
import com.valkaw.client.module.Setting;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public class CoordinatesModule extends HudModule {
	private Layout layout = Layout.VERTICAL;
	private boolean showDimension = true;
	private boolean showBiome = true;
	private boolean showDirection = true;

	public CoordinatesModule() {
		super("coordinates", "Coordinates", 4, 69);
		setEnabled(true);
	}

	/** Same values/format the HUD shows; Copy Coordinates reuses this. */
	public static String xyz(LocalPlayer player) {
		return player.getBlockX() + " " + player.getBlockY() + " " + player.getBlockZ();
	}

	public static String biome(LocalPlayer player) {
		return player.level().getBiome(player.blockPosition()).unwrapKey()
				.map(key -> Component.translatable("biome." + key.identifier().getNamespace() + "."
						+ key.identifier().getPath()).getString())
				.orElse("Unknown");
	}

	public static String direction(LocalPlayer player) {
		return player.getDirection().getName().toUpperCase();
	}

	@Override
	protected int previewWidth() {
		return 120;
	}

	@Override
	protected int previewHeight() {
		int lines = 1 + (showDimension ? 1 : 0) + (showDirection ? 1 : 0) + (showBiome ? 1 : 0);
		return lines * 11;
	}

	@Override
	public void render(GuiGraphics graphics, Font font) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			w = 0;
			h = 0;
			return;
		}
		String dimension = player.level().dimension().identifier().getPath().toUpperCase();
		if (layout == Layout.HORIZONTAL) {
			int cursor = x;
			int first = Hud.chip(graphics, font, cursor, y, "XYZ", xyz(player));
			cursor += first + 2;
			if (showDimension) {
				cursor += Hud.chipRaw(graphics, font, cursor, y, "DIM", dimension) + 2;
			}
			if (showDirection) {
				cursor += Hud.chipRaw(graphics, font, cursor, y, "FACING", direction(player)) + 2;
			}
			if (showBiome) {
				cursor += Hud.chipRaw(graphics, font, cursor, y, "BIOME", biome(player)) + 2;
			}
			w = cursor - x - 2;
			h = 11;
			return;
		}
		int line = 1;
		w = Hud.chip(graphics, font, x, y, "XYZ", xyz(player));
		if (showDimension) {
			w = Math.max(w, Hud.chipRaw(graphics, font, x, y + line++ * 11, "DIM", dimension));
		}
		if (showDirection) {
			w = Math.max(w, Hud.chipRaw(graphics, font, x, y + line++ * 11, "FACING", direction(player)));
		}
		if (showBiome) {
			w = Math.max(w, Hud.chipRaw(graphics, font, x, y + line++ * 11, "BIOME", biome(player)));
		}
		h = line * 11;
	}

	@Override
	public List<Setting> settings() {
		return List.of(
				Setting.choice("LAYOUT", () -> layout.name(), () -> layout = layout.next()),
				Setting.choice("DIMENSION", () -> showDimension ? "SHOW" : "HIDE", () -> showDimension = !showDimension),
				Setting.choice("BIOME", () -> showBiome ? "SHOW" : "HIDE", () -> showBiome = !showBiome),
				Setting.choice("DIRECTION", () -> showDirection ? "SHOW" : "HIDE",
						() -> showDirection = !showDirection));
	}

	@Override
	public String option() {
		return layout.name();
	}

	@Override
	public void cycleOption() {
		layout = layout.next();
	}

	@Override
	public void writeExtra(JsonObject json) {
		super.writeExtra(json);
		json.addProperty("layout", layout.name());
		json.addProperty("dimension", showDimension);
		json.addProperty("biome", showBiome);
		json.addProperty("direction", showDirection);
	}

	@Override
	public void readExtra(JsonObject json) {
		super.readExtra(json);
		if (json.has("layout")) {
			layout = Layout.parse(json.get("layout").getAsString());
		}
		if (json.has("dimension")) {
			showDimension = json.get("dimension").getAsBoolean();
		}
		if (json.has("biome")) {
			showBiome = json.get("biome").getAsBoolean();
		}
		if (json.has("direction")) {
			showDirection = json.get("direction").getAsBoolean();
		}
	}
}
