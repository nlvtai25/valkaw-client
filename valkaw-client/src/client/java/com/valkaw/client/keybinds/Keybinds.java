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
			if (!module.keybindable() || module.key() < 0)
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
	 * Keeps the two places a key can be edited in step: our own binding screen and
	 * the vanilla Controls screen. Whichever side moved last wins.
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
			int bound = KeyBindingHelper.getBoundKeyOf(entry.getValue()).getValue();
			int key = module.key();
			Integer last = LAST.get(module.id());
			if (last == null)
			{
				LAST.put(module.id(), key);
				continue;
			}
			if (bound != last && key == last)
			{
				module.setKey(bound);
				changed = true;
			}
			else if (key != last && bound == last)
			{
				entry.getValue().setKey(key < 0 ? InputConstants.UNKNOWN : InputConstants.Type.KEYSYM.getOrCreate(key));
			}
			LAST.put(module.id(), module.key());
		}
		if (changed)
		{
			ModuleManager.get().save();
		}
	}
}
