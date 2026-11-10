package com.valkaw.client.hud;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Frame-rate mouse sampling shared by CPS and Keystrokes (no mixin needed). */
public final class InputState {
	private static final long[] LEFT = new long[8];
	private static final long[] RIGHT = new long[8];
	private static int leftIndex;
	private static int rightIndex;
	private static boolean leftDown;
	private static boolean rightDown;

	private InputState() {
	}

	public static void poll() {
		Minecraft mc = Minecraft.getInstance();
		long window = mc.getWindow().handle();
		boolean left = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		boolean right = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
		if (mc.screen == null) {
			long now = System.currentTimeMillis();
			if (left && !leftDown) {
				LEFT[leftIndex++ & 7] = now;
			}
			if (right && !rightDown) {
				RIGHT[rightIndex++ & 7] = now;
			}
		}
		leftDown = left;
		rightDown = right;
	}

	public static boolean left() {
		return leftDown;
	}

	public static boolean right() {
		return rightDown;
	}

	public static int leftCps() {
		return count(LEFT);
	}

	public static int rightCps() {
		return count(RIGHT);
	}

	private static int count(long[] stamps) {
		long cutoff = System.currentTimeMillis() - 1000L;
		int clicks = 0;
		for (long stamp : stamps) {
			if (stamp > cutoff) {
				clicks++;
			}
		}
		return clicks;
	}
}
