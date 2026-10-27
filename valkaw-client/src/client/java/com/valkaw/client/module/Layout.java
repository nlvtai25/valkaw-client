package com.valkaw.client.module;

/** HUD arrangement shared by the Armor HUD and Coordinates modules. */
public enum Layout {
	HORIZONTAL, VERTICAL;

	public Layout next() {
		return this == HORIZONTAL ? VERTICAL : HORIZONTAL;
	}

	public static Layout parse(String name) {
		return "VERTICAL".equalsIgnoreCase(name) ? VERTICAL : HORIZONTAL;
	}
}
