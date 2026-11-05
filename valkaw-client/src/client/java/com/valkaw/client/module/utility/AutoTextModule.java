package com.valkaw.client.module.utility;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Auto Text sends a chat message or command when its keybind is pressed. Every
 * entry has its own text and its own keybind, which may include modifiers
 * (Ctrl/Shift/Alt) and is deliberately NOT mirrored into the vanilla Controls
 * screen — this module manages its keys entirely on its own.
 */
public class AutoTextModule extends Module {
	/** Bit flags for the modifier keys stored on an entry. */
	public static final int MOD_CTRL = 1;
	public static final int MOD_SHIFT = 2;
	public static final int MOD_ALT = 4;

	/** Hard cap on the number of entries, to avoid accidental runaway growth. */
	public static final int MAX_ENTRIES = 255;

	/** One auto-send line: the text to send and the key combination that fires it. */
	public static final class Entry {
		public String text = "";
		public int key = -1;
		public int mods;

		public Entry() {
		}

		public Entry(String text, int key, int mods) {
			this.text = text;
			this.key = key;
			this.mods = mods;
		}
	}

	private final List<Entry> entries = new ArrayList<>();
	private final List<Boolean> held = new ArrayList<>();

	public AutoTextModule() {
		super("auto-text", "Auto Text", Category.UTILITY);
		setEnabled(true);
	}

	/** This module handles its own keys; nothing is registered in vanilla Controls. */
	@Override
	public boolean keybindable() {
		return false;
	}

	public List<Entry> entries() {
		return entries;
	}

	/** Replace the entry list with a copy of the given entries. */
	public void setEntries(List<Entry> newEntries) {
		entries.clear();
		for (Entry entry : newEntries) {
			entries.add(new Entry(entry.text, entry.key, entry.mods));
		}
	}

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.player.connection == null || mc.screen != null) {
			for (int i = 0; i < held.size(); i++) {
				held.set(i, false);
			}
			return;
		}
		while (held.size() < entries.size()) {
			held.add(false);
		}
		for (int i = 0; i < entries.size(); i++) {
			Entry entry = entries.get(i);
			boolean down = isDown(entry);
			if (down && !held.get(i)) {
				send(mc, entry.text);
			}
			held.set(i, down);
		}
	}

	private static boolean isDown(Entry entry) {
		if (entry.key < 0) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		Window window = mc.getWindow();
		if (entry.mods != 0) {
			boolean ctrl = (entry.mods & MOD_CTRL) != 0;
			boolean shift = (entry.mods & MOD_SHIFT) != 0;
			boolean alt = (entry.mods & MOD_ALT) != 0;
			boolean ctrlDown = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
					|| InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
			boolean shiftDown = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
					|| InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
			boolean altDown = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT)
					|| InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
			if (ctrl != ctrlDown || shift != shiftDown || alt != altDown) {
				return false;
			}
		}
		return InputConstants.isKeyDown(window, entry.key);
	}

	private static void send(Minecraft mc, String text) {
		if (text == null || text.isBlank()) {
			return;
		}
		if (text.startsWith("/")) {
			mc.player.connection.sendCommand(text.substring(1));
		} else {
			mc.player.connection.sendChat(text);
		}
	}

	@Override
	public void writeExtra(JsonObject json) {
		JsonArray array = new JsonArray();
		for (Entry entry : entries) {
			JsonObject obj = new JsonObject();
			obj.addProperty("text", entry.text);
			obj.addProperty("key", entry.key);
			obj.addProperty("mods", entry.mods);
			array.add(obj);
		}
		json.add("entries", array);
	}

	@Override
	public void readExtra(JsonObject json) {
		entries.clear();
		held.clear();
		if (!json.has("entries")) {
			return;
		}
		JsonArray array = json.getAsJsonArray("entries");
		for (JsonElement element : array) {
			if (entries.size() >= MAX_ENTRIES) {
				break;
			}
			JsonObject obj = element.getAsJsonObject();
			Entry entry = new Entry();
			if (obj.has("text")) {
				entry.text = obj.get("text").getAsString();
			}
			if (obj.has("key")) {
				entry.key = obj.get("key").getAsInt();
			}
			if (obj.has("mods")) {
				entry.mods = obj.get("mods").getAsInt();
			}
			entries.add(entry);
		}
	}
}
