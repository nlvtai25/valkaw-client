package com.valkaw.client.module.utility;

import com.google.gson.JsonObject;
import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;
import com.valkaw.client.module.Setting;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ChatTimestampsModule extends Module {
	private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("hh:mm a");
	private boolean hour12;

	public ChatTimestampsModule() {
		super("chat-timestamps", "Chat Timestamps", Category.UTILITY);
	}

	/** Timestamp prefix used by {@code ChatComponentMixin}. */
	public String stamp() {
		return "[" + LocalTime.now().format(hour12 ? H12 : H24) + "] ";
	}

	@Override
	public boolean keybindable() {
		return false;
	}

	@Override
	public List<Setting> settings() {
		return List.of(Setting.choice("FORMAT", () -> hour12 ? "12H" : "24H", () -> hour12 = !hour12));
	}

	@Override
	public String option() {
		return hour12 ? "12H" : "24H";
	}

	@Override
	public void cycleOption() {
		hour12 = !hour12;
	}

	@Override
	public void writeExtra(JsonObject json) {
		json.addProperty("hour12", hour12);
	}

	@Override
	public void readExtra(JsonObject json) {
		if (json.has("hour12")) {
			hour12 = json.get("hour12").getAsBoolean();
		}
	}
}
