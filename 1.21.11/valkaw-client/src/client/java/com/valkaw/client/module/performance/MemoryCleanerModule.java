package com.valkaw.client.module.performance;

import com.google.gson.JsonObject;
import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;
import com.valkaw.client.module.Setting;
import java.util.List;
import java.util.Locale;

/** Calls the garbage collector on a fixed interval while the game is running. */
public class MemoryCleanerModule extends Module {
	private double intervalSeconds = 30.0;
	private long lastRun;

	public MemoryCleanerModule() {
		super("memory-cleaner", "Memory Cleaner", Category.PERFORMANCE);
	}

	@Override
	public boolean keybindable() {
		return false;
	}

	@Override
	public void onEnable() {
		lastRun = System.currentTimeMillis();
	}

	@Override
	public void onTick() {
		long now = System.currentTimeMillis();
		if (now - lastRun >= (long) (intervalSeconds * 1000)) {
			lastRun = now;
			Runtime.getRuntime().gc();
		}
	}

	@Override
	public List<Setting> settings() {
		return List.of(
				Setting.slider("INTERVAL", 5, 300, 5, () -> intervalSeconds,
						value -> intervalSeconds = value,
						() -> String.format(Locale.ROOT, "%.0f s", intervalSeconds)),
				Setting.text("INTERVAL VALUE", () -> String.format(Locale.ROOT, "%.0f", intervalSeconds), value -> {
					try {
						intervalSeconds = Math.max(5, Math.min(300, Double.parseDouble(value.trim())));
					} catch (NumberFormatException ignored) {
						// Keep the previous value.
					}
				}));
	}

	@Override
	public String option() {
		return String.format(Locale.ROOT, "%.0f s", intervalSeconds);
	}

	@Override
	public void cycleOption() {
		intervalSeconds = intervalSeconds >= 120 ? 15 : intervalSeconds + 15;
	}

	@Override
	public void writeExtra(JsonObject json) {
		json.addProperty("intervalSeconds", intervalSeconds);
	}

	@Override
	public void readExtra(JsonObject json) {
		if (json.has("intervalSeconds")) {
			intervalSeconds = Math.max(5, Math.min(300, json.get("intervalSeconds").getAsDouble()));
		}
	}
}
