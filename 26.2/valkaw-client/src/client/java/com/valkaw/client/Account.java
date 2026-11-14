package com.valkaw.client;

import com.valkaw.client.module.ModuleManager;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.User;

/**
 * Offline identity: the name is set in-game from the main menu account pill,
 * so the launcher only has to start the game.
 */
public final class Account
{
	private static User cached;
	private static String cachedName = "";

	private Account()
	{
	}

	/** Null until the player picks a name, so the launcher value keeps working. */
	public static User user()
	{
		String name = ModuleManager.get().username();
		if (name == null || name.isBlank())
		{
			return null;
		}
		if (cached != null && name.equals(cachedName))
		{
			return cached;
		}
		// Same derivation vanilla uses for offline players, so worlds and stats
		// stay attached to one name.
		UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
		cached = new User(name, uuid, "0", Optional.empty(), Optional.empty());
		cachedName = name;
		return cached;
	}
}
