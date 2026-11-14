package com.valkaw.client.keybinds;

import com.mojang.blaze3d.platform.InputConstants;
import com.valkaw.client.module.Module;
import com.valkaw.client.module.ModuleManager;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Mirrors every key-driven module onto a vanilla key bind so the player can change
 * it in Options > Controls, under the "Valkaw Client" category, and have that
 * binding persist in options.txt like any other key.
 */
public final class Keybinds {
	private static final Map<String, KeyMapping> MAPPINGS = new LinkedHashMap<>();
	private static final Map<String, Integer> LAST = new LinkedHashMap<>();
	private static final String NAME = "key.valkawclient.";
	private static boolean registered;

	private Keybinds()
	{
	}

	public static void register()
	{
		if (registered)
		{
			return;
		}
		registered = true;
		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath("valkawclient", "main"));
		for (Module module : ModuleManager.get().all())
		{
			if (!module.keybindable())
			{
				continue;
			}
			MAPPINGS.put(module.id(), KeyBindingHelper.registerKeyBinding(new KeyMapping(
					NAME + module.id().replace('-', '_'), InputConstants.Type.KEYSYM, module.key(), category)));
		}
	}

	/** Key state as bound in Controls, which may differ from the key stored on the module. */
	public static boolean down(Module module)
	{
		if (!module.keybindable())
		{
			return false;
		}
		KeyMapping mapping = MAPPINGS.get(module.id());
		if (mapping != null)
		{
			return mapping.isDown();
		}
		int key = module.key();
		Minecraft minecraft = Minecraft.getInstance();
		return key >= 0 && minecraft.screen == null
				&& InputConstants.isKeyDown(minecraft.getWindow(), key);
	}

	/**
	 * Keeps the two places a key can be edited in step: our own binding screen
	 * and the vanilla Controls screen. Whichever side moved last wins.
	 *
	 * <p>Direction detection: the module key and the vanilla mapping normally
	 * agree. When they diverge, the side whose value still matches the last
	 * synced value is the one that stayed still, so the other side moved and
	 * its value is adopted.
	 */
	public static void sync()
	{
		if (MAPPINGS.isEmpty())
		{
			return;
		}
		boolean changed = false;
		for (Map.Entry<String, KeyMapping> entry : MAPPINGS.entrySet())
		{
			Module module = ModuleManager.get().byId(entry.getKey());
			if (module == null)
			{
				continue;
			}
			KeyMapping mapping = entry.getValue();
			int bound = KeyBindingHelper.getBoundKeyOf(mapping).getValue();
			int key = module.key();
			Integer last = LAST.get(module.id());
			if (last == null)
			{
				// First sight: trusted the live vanilla binding, which may have
				// loaded a different key from options.txt than the module's own
				// config; align both sides on it.
				if (key != bound)
				{
					module.setKey(bound);
					changed = true;
				}
				LAST.put(module.id(), bound);
				continue;
			}
			if (bound == key)
			{
				LAST.put(module.id(), key);
				continue;
			}
			if (bound == last)
			{
				// The module side moved (our binding screen): push it to the vanilla mapping.
				mapping.setKey(key < 0 ? InputConstants.UNKNOWN : InputConstants.Type.KEYSYM.getOrCreate(key));
				LAST.put(module.id(), key);
				continue;
			}
			// The vanilla side moved (player edited Controls): adopt it on the module.
			module.setKey(bound);
			changed = true;
			LAST.put(module.id(), bound);
		}
		if (changed)
		{
			ModuleManager.get().save();
		}
	}

	/**
	 * Pushes the module's current key straight to its vanilla key mapping (used by the
	 * in-mod binding screen so it matches Options > Controls immediately).
	 */
	public static void apply(Module module)
	{
		KeyMapping mapping = MAPPINGS.get(module.id());
		if (mapping == null)
		{
			return;
		}
		int key = module.key();
		mapping.setKey(key < 0 ? InputConstants.UNKNOWN : InputConstants.Type.KEYSYM.getOrCreate(key));
		LAST.put(module.id(), module.key());
		Minecraft mc = Minecraft.getInstance();
		if (mc != null && mc.options != null)
		{
			mc.options.save();
		}
	}
}
