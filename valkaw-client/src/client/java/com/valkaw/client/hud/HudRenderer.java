package com.valkaw.client.hud;

import com.valkaw.client.module.HudModule;
import com.valkaw.client.module.ModuleManager;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class HudRenderer {
	private HudRenderer()
	{
	}

	private static int lastLayoutWidth = -1;
	private static boolean lastScreenOpen = false;

	/** Positions every enabled element for the current window width. */
	public static void layout(List<HudModule> modules, int screenWidth)
	{
		layout(modules, screenWidth, null);
	}

	/**
	 * Positions HUD elements: screen anchors first, then modules attached to
	 * another element. Nothing is stacked automatically, so an element only
	 * follows something it was explicitly linked to in the editor.
	 *
	 * @param skip element being dragged by the editor, left where the player put it
	 */
	public static void layout(List<HudModule> modules, int screenWidth, HudModule skip)
	{
		for (HudModule module : modules)
		{
			if (module.isEnabled() && module != skip)
			{
				module.applyAnchor(screenWidth);
			}
		}
		// Children need the final position of their parent, so resolve until a
		// pass stops moving anything (a broken or absent link ends the chain).
		for (int pass = 0; pass < 4; pass++)
		{
			boolean moved = false;
			for (HudModule module : modules)
			{
				if (module.isEnabled() && module != skip && module.applyLink())
				{
					moved = true;
				}
			}
			if (!moved)
			{
				break;
			}
		}
	}

	public static void render(GuiGraphics graphics)
	{
		Minecraft mc = Minecraft.getInstance();
		// Screens (inventory, chat, containers) draw on top of the HUD, so the
		// numbers stay visible around them. F1 (hideGui) is the only switch-off.
		if (mc.player == null || mc.options.hideGui)
		{
			return;
		}
		InputState.poll();
		int screenWidth = graphics.guiWidth();
		List<HudModule> modules = ModuleManager.get().hudModules();
		if (mc.screen == null)
		{
			// Only re-anchor while no screen is open: the HUD editor positions the
			// element being dragged itself, so anchoring here would fight the mouse.
			// Positions only depend on the window width, so skip layout unless the
			// width changed or a screen just closed (which may have moved elements).
			if (screenWidth != lastLayoutWidth || lastScreenOpen)
			{
				layout(modules, screenWidth);
				lastLayoutWidth = screenWidth;
				lastScreenOpen = false;
			}
		}
		else
		{
			lastScreenOpen = true;
		}
		for (HudModule module : modules)
		{
			if (module.isEnabled())
			{
				Hud.use(module);
				module.render(graphics, mc.font);
				module.rememberSize();
			}
		}
	}
}
