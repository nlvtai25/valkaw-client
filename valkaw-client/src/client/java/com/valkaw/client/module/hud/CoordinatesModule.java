package com.valkaw.client.module.hud;

import com.google.gson.JsonObject;
import com.valkaw.client.hud.Hud;
import com.valkaw.client.module.HudModule;
import com.valkaw.client.module.Layout;
import com.valkaw.client.module.Setting;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

public class CoordinatesModule extends HudModule {
	private static final Map<ResourceKey<Biome>, String> BIOME_NAMES = new HashMap<>();
	private static ResourceKey<Level> cachedDimension;
	private static String cachedDimensionName = "";

	private Layout layout = Layout.VERTICAL;
	private boolean showDimension = true;
	private boolean showBiome = true;
	private boolean showDirection = true;

	public CoordinatesModule() {
		super("coordinates", "Coordinates", 3, 69);
		setEnabled(true);
	}

	/** Same values/format the HUD shows; Copy Coordinates reuses this. */
	public static String xyz(LocalPlayer player) {
		return player.getBlockX() + " " + player.getBlockY() + " " + player.getBlockZ();
	}

	public static String biome(LocalPlayer player) {
		Optional<ResourceKey<Biome>> key = player.level().getBiome(player.blockPosition()).unwrapKey();
		if (key.isEmpty()) {
			return "Unknown";
		}
		// The translation key string is only built on a cache miss now.
		return BIOME_NAMES.computeIfAbsent(key.get(), biome -> {
			var id = biome.identifier();
			return Component.translatable("biome." + id.getNamespace() + "." + id.getPath()).getString();
		});
	}

	public static String dimension(LocalPlayer player) {
		ResourceKey<Level> key = player.level().dimension();
		if (key != cachedDimension) {
			cachedDimension = key;
			cachedDimensionName = key.identifier().getPath().toUpperCase();
		}
		return cachedDimensionName;
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
		String dimension = dimension(player);
		if (layout == Layout.HORIZONTAL) {
			int cursor = x;
			int first = Hud.chip(graphics, font, cursor, y, "XYZ", xyz(player));
			cursor += first + 2;
			if (showDimension) {
				cursor += Hud.chip(graphics, font, cursor, y, "Dim", dimension) + 2;
			}
			if (showDirection) {
				cursor += Hud.chip(graphics, font, cursor, y, "Facing", direction(player)) + 2;
			}
			if (showBiome) {
				cursor += Hud.chip(graphics, font, cursor, y, "Biome", biome(player)) + 2;
			}
			w = cursor - x - 2;
			h = 11;
			return;
		}
		int line = 1;
		w = Hud.chip(graphics, font, x, y, "XYZ", xyz(player));
		if (showDimension) {
			w = Math.max(w, Hud.chip(graphics, font, x, y + line++ * 11, "Dim", dimension));
		}
		if (showDirection) {
			w = Math.max(w, Hud.chip(graphics, font, x, y + line++ * 11, "Facing", direction(player)));
		}
		if (showBiome) {
			w = Math.max(w, Hud.chip(graphics, font, x, y + line++ * 11, "Biome", biome(player)));
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
