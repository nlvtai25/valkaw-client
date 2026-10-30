package com.valkaw.client.module;

import com.google.gson.JsonObject;

/**
 * Base module. State (enabled/keybind) is serialized by {@link ModuleManager};
 * subclasses extend the json entry through {@link #writeExtra}/{@link #readExtra}.
 */
public abstract class Module {
	private final String id;
	private final String name;
	private final Category category;
	private final boolean action;
	private final TextStyle style;
	private boolean enabled;
	private int key = -1;
	private boolean keyHeld;

	protected Module(String id, String name, Category category) {
		this(id, name, category, false);
	}

	protected Module(String id, String name, Category category, boolean action) {
		this.id = id;
		this.name = name;
		this.category = category;
		this.action = action;
		this.style = new TextStyle(category == Category.UTILITY ? 0xFFB18CFF
				: category == Category.PERFORMANCE ? 0xFF63F5B0 : 0xFF7DF9FF);
	}

	public TextStyle style() {
		return style;
	}

	/** Effective colour, honouring the global style when enabled. */
	public int color() {
		return style.useGlobal() ? ModuleManager.get().globalStyle().color() : style.color();
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public Category category() {
		return category;
	}

	/** Action modules fire {@link #onKey()} instead of toggling. */
	public boolean isAction() {
		return action;
	}

	/**
	 * Key-held modules (zoom, freelook) have no useful on/off state: they work
	 * only while the key is down, so the settings UI hides their switch.
	 */
	public boolean keyOnly() {
		return false;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean on) {
		if (enabled == on) {
			return;
		}
		enabled = on;
		if (on) {
			onEnable();
		} else {
			onDisable();
		}
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	public int key() {
		return key;
	}

	public void setKey(int key) {
		this.key = key;
	}

	/** True once per physical key press. */
	public boolean pollPress(boolean down) {
		boolean pressed = down && !keyHeld;
		keyHeld = down;
		return pressed;
	}

	public String status() {
		return enabled ? "ON" : "OFF";
	}

	/** Keybind chip is only shown for key-driven modules (HUD/performance modules are keyless). */
	public boolean keybindable() {
		return category == Category.UTILITY;
	}

	/** Inline option label shown in the settings row (layout/format), or null when there is none. */
	public String option() {
		return null;
	}

	public void cycleOption() {
	}

	/** Module-specific rows shown in its Options screen. */
	public java.util.List<Setting> settings() {
		return java.util.List.of();
	}

	public void onEnable() {
	}

	public void onDisable() {
	}

	public void onTick() {
	}

	public void onKey() {
	}

	public void writeExtra(JsonObject json) {
	}

	public void readExtra(JsonObject json) {
	}
}
