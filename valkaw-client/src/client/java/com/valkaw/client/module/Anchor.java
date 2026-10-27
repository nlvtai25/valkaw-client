package com.valkaw.client.module;

/** Screen side a HUD module sticks to when the window is resized. */
public enum Anchor {
	LEFT, RIGHT;

	public static Anchor parse(String name) {
		return "RIGHT".equalsIgnoreCase(name) ? RIGHT : LEFT;
	}
}
