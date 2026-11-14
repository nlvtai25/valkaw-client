package com.valkaw.client.module.utility;

import com.google.gson.JsonObject;
import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;
import com.valkaw.client.module.Setting;
import java.util.List;
import java.util.Locale;

/** Custom block-outline colour and thickness; the renderer keeps vanilla shape-aware geometry. */
public class BlockOutlineModule extends Module {
	private float thickness = 2.0F;

	public BlockOutlineModule() {
		super("block-outline", "Block Outline", Category.UTILITY);
	}

	@Override
	public boolean keybindable() {
		return false;
	}

	public float thickness() {
		return thickness;
	}

	@Override
	public List<Setting> settings() {
		return List.of(
				Setting.slider("THICKNESS", 1.0, 6.0, 0.5, () -> thickness,
						value -> thickness = (float) value,
						() -> String.format(Locale.ROOT, "%.1f", thickness)),
				Setting.text("THICKNESS VALUE", () -> String.format(Locale.ROOT, "%.1f", thickness), value -> {
					try {
						thickness = Math.max(1.0F, Math.min(6.0F, Float.parseFloat(value.trim())));
					} catch (NumberFormatException ignored) {
						// Keep the previous value.
					}
				}));
	}

	@Override
	public void writeExtra(JsonObject json) {
		super.writeExtra(json);
		json.addProperty("thickness", thickness);
	}

	@Override
	public void readExtra(JsonObject json) {
		super.readExtra(json);
		if (json.has("thickness")) {
			thickness = json.get("thickness").getAsFloat();
		}
	}
}
