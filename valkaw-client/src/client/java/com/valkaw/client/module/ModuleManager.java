package com.valkaw.client.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.valkaw.client.gui.ClientSettingsScreen;
import com.valkaw.client.gui.HudEditorScreen;
import com.valkaw.client.keybinds.Keybinds;
import com.valkaw.client.module.hud.ArmorHudModule;
import com.valkaw.client.module.hud.ComboModule;
import com.valkaw.client.module.hud.CoordinatesModule;
import com.valkaw.client.module.hud.CrosshairModule;
import com.valkaw.client.module.hud.CpsModule;
import com.valkaw.client.module.hud.FpsModule;
import com.valkaw.client.module.hud.KeystrokesModule;
import com.valkaw.client.module.hud.PcInfoModule;
import com.valkaw.client.module.hud.PingModule;
import com.valkaw.client.module.hud.PotionHudModule;
import com.valkaw.client.module.hud.ServerIpModule;
import com.valkaw.client.module.hud.SneakHudModule;
import com.valkaw.client.module.hud.SpeedModule;
import com.valkaw.client.module.hud.SprintHudModule;
import com.valkaw.client.module.hud.TimeModule;
import com.valkaw.client.module.hud.TpsModule;
import com.valkaw.client.module.performance.ClearFluidsModule;
import com.valkaw.client.module.performance.MenuBackdropModule;
import com.valkaw.client.module.performance.CloudsModule;
import com.valkaw.client.module.performance.NoFogModule;
import com.valkaw.client.module.performance.ParticlesModule;
import com.valkaw.client.module.performance.RenderDistanceModule;
import com.valkaw.client.module.utility.BlockOutlineModule;
import com.valkaw.client.module.utility.ChatTimestampsModule;
import com.valkaw.client.module.utility.CopyChatModule;
import com.valkaw.client.module.utility.CopyCoordsModule;
import com.valkaw.client.module.utility.FreelookModule;
import com.valkaw.client.module.utility.FullbrightModule;
import com.valkaw.client.module.utility.NoBobbingModule;
import com.valkaw.client.module.utility.NoFireModule;
import com.valkaw.client.module.utility.NoHurtCamModule;
import com.valkaw.client.module.utility.NoPumpkinModule;
import com.valkaw.client.module.utility.ToggleSneakModule;
import com.valkaw.client.module.utility.ToggleSprintModule;
import com.valkaw.client.module.utility.ZoomModule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModuleManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("valkawclient");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final ModuleManager INSTANCE = new ModuleManager();

	private final List<Module> modules = new ArrayList<>();
	private final List<HudModule> hudModules = new ArrayList<>();
	private final Map<Category, List<Module>> categorized = new EnumMap<>(Category.class);
	private final Map<String, Module> byId = new LinkedHashMap<>();
	private final TextStyle globalStyle = new TextStyle(0xFF7DF9FF);
	private static final int SETTINGS_KEY = GLFW.GLFW_KEY_RIGHT_SHIFT;
	private int editorKey = GLFW.GLFW_KEY_F6;
	private String username = "";
	private final List<String> accounts = new ArrayList<>();
	private boolean editorKeyHeld;
	private boolean settingsKeyHeld;
	private boolean initialized;

	private ModuleManager() {
	}

	public static ModuleManager get() {
		return INSTANCE;
	}

	/** Used by mixins; safe before a world exists. */
	public static boolean isOn(String id) {
		Module module = INSTANCE.byId.get(id);
		return module != null && module.isEnabled();
	}

	public void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		register(new FpsModule());
		register(new CpsModule());
		register(new KeystrokesModule());
		register(new CoordinatesModule());
		register(new PingModule());
		register(new ArmorHudModule());
		register(new PotionHudModule());
		register(new ComboModule());
		register(new SpeedModule());
		register(new PcInfoModule());
		register(new TimeModule());
		register(new TpsModule());
		register(new ServerIpModule());
		register(new SprintHudModule());
		register(new SneakHudModule());
		register(new CrosshairModule());
		register(new ToggleSprintModule());
		register(new ToggleSneakModule());
		register(new ZoomModule());
		register(new NoHurtCamModule());
		register(new NoFireModule());
		register(new NoPumpkinModule());
		register(new ChatTimestampsModule());
		register(new CopyCoordsModule());
		register(new CopyChatModule());
		register(new FreelookModule());
		register(new FullbrightModule());
		register(new NoBobbingModule());
		register(new BlockOutlineModule());
		register(new ParticlesModule());
		register(new CloudsModule());
		register(new RenderDistanceModule());
		register(new NoFogModule());
		register(new ClearFluidsModule());
		register(new MenuBackdropModule());
		load();
		LOGGER.info("Valkaw modules ready: {}", modules.size());
	}

	private void register(Module module) {
		modules.add(module);
		byId.put(module.id(), module);
		if (module instanceof HudModule hud) {
			hudModules.add(hud);
		}
	}

	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		boolean settingsDown = InputConstants.isKeyDown(mc.getWindow(), SETTINGS_KEY);
		if (settingsDown && !settingsKeyHeld && mc.screen == null) {
			mc.setScreen(new ClientSettingsScreen(null));
		}
		settingsKeyHeld = settingsDown;

		boolean editorDown = editorKey >= 0 && InputConstants.isKeyDown(mc.getWindow(), editorKey);
		if (editorDown && !editorKeyHeld && mc.screen == null) {
			mc.setScreen(new HudEditorScreen());
		}
		editorKeyHeld = editorDown;

		Keybinds.sync();
		for (Module module : modules) {
			boolean down = mc.screen == null && Keybinds.down(module);
			if (module.pollPress(down)) {
				if (module.isAction()) {
					module.onKey();
				} else {
					module.toggle();
				}
				save();
			}
			if (module.isEnabled()) {
				module.onTick();
			}
		}
	}

	public List<Module> all() {
		return modules;
	}

	public List<Module> byCategory(Category category) {
		List<Module> list = categorized.get(category);
		if (list == null) {
			list = new ArrayList<>();
			for (Module module : modules) {
				if (module.category() == category) {
					list.add(module);
				}
			}
			list.sort(Comparator.comparing(Module::name, String.CASE_INSENSITIVE_ORDER));
			categorized.put(category, list);
		}
		return list;
	}

	public List<HudModule> hudModules() {
		return hudModules;
	}

	public Module byId(String id) {
		return byId.get(id);
	}

	public TextStyle globalStyle() {
		return globalStyle;
	}

	public int editorKey() {
		return editorKey;
	}

	/** Player name picked in-game; empty means "keep whatever the launcher passed". */
	public String username()
	{
		return username;
	}

	public void setUsername(String name)
	{
		username = cleanName(name);
		if (!username.isBlank() && accounts.stream().noneMatch(entry -> entry.equalsIgnoreCase(username)))
		{
			accounts.add(username);
		}
		save();
	}

	/** Names the player saved from the main menu, newest last. */
	public List<String> accounts()
	{
		return accounts;
	}

	public void removeAccount(String name)
	{
		accounts.removeIf(entry -> entry.equalsIgnoreCase(cleanName(name)));
		if (username.equalsIgnoreCase(cleanName(name)))
		{
			username = accounts.isEmpty() ? "" : accounts.get(0);
		}
		save();
	}

	private static String cleanName(String name)
	{
		String clean = name == null ? "" : name.trim();
		return clean.length() > 16 ? clean.substring(0, 16) : clean;
	}

	public void setEditorKey(int key) {
		editorKey = key;
		save();
	}

	public void resetPositions() {
		for (HudModule module : hudModules()) {
			module.resetPosition();
		}
		save();
	}

	private Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("valkaw-client.json");
	}

	public void save() {
		JsonObject root = new JsonObject();
		root.addProperty("editorKey", editorKey);
		root.addProperty("username", username);
		JsonArray saved = new JsonArray();
		for (String entry : accounts)
		{
			saved.add(entry);
		}
		root.add("accounts", saved);
		JsonObject global = new JsonObject();
		globalStyle.write(global);
		root.add("globalStyle", global);
		JsonObject entries = new JsonObject();
		for (Module module : modules) {
			JsonObject entry = new JsonObject();
			entry.addProperty("enabled", module.isEnabled());
			entry.addProperty("key", module.key());
			JsonObject style = new JsonObject();
			module.style().write(style);
			entry.add("style", style);
			module.writeExtra(entry);
			entries.add(module.id(), entry);
		}
		root.add("modules", entries);
		Path path = configPath();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(root));
		} catch (IOException e) {
			LOGGER.warn("Could not save Valkaw config", e);
		}
	}

	private void load() {
		Path path = configPath();
		if (Files.exists(path)) {
			try {
				apply(JsonParser.parseString(Files.readString(path)).getAsJsonObject());
			} catch (Exception e) {
				LOGGER.warn("Could not read Valkaw config", e);
			}
			return;
		}
		// A fresh install starts from the shipped layout, so everyone gets the
		// same HUD arrangement and module defaults instead of bare positions.
		try (InputStream stream = ModuleManager.class.getResourceAsStream("/assets/valkawclient/default-config.json")) {
			if (stream == null) {
				return;
			}
			apply(JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
			username = "";
			accounts.clear();
			save();
		} catch (Exception e) {
			LOGGER.warn("Could not load the bundled Valkaw layout", e);
		}
	}

	private void apply(JsonObject root) {
		if (root.has("editorKey")) {
			editorKey = root.get("editorKey").getAsInt();
		}
		if (root.has("username")) {
			username = root.get("username").getAsString();
		}
		if (root.has("accounts")) {
			for (var entry : root.getAsJsonArray("accounts")) {
				String name = cleanName(entry.getAsString());
				if (!name.isBlank()) {
					accounts.add(name);
				}
			}
		}
		if (root.has("globalStyle")) {
			globalStyle.read(root.getAsJsonObject("globalStyle"));
		}
		JsonObject entries = root.getAsJsonObject("modules");
		for (Module module : modules) {
			if (entries == null || !entries.has(module.id())) {
				continue;
			}
			JsonObject entry = entries.getAsJsonObject(module.id());
			if (entry.has("key")) {
				module.setKey(entry.get("key").getAsInt());
			}
			if (entry.has("style")) {
				module.style().read(entry.getAsJsonObject("style"));
			}
			module.readExtra(entry);
			if (entry.has("enabled")) {
				module.setEnabled(entry.get("enabled").getAsBoolean());
			}
		}
	}
}
