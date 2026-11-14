package com.valkaw.client.hud;

import com.valkaw.client.gui.HudEditorScreen;
import com.valkaw.client.module.HudModule;
import com.valkaw.client.module.ModuleManager;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class HudRenderer {
	private HudRenderer()
	{
	}

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

	private static boolean layoutDirty = true;
	private static int lastScreenWidth = -1;

	/** Marks HUD layout as dirty so the next render() re-anchors elements. Called by editor / modules when positions change. */
	public static void markDirty()
	{
		layoutDirty = true;
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
		boolean inEditor = mc.screen instanceof HudEditorScreen;
		if (!inEditor && (layoutDirty || screenWidth != lastScreenWidth))
		{
			layout(modules, screenWidth);
			lastScreenWidth = screenWidth;
			layoutDirty = false;
		}
		for (HudModule module : modules)
		{
			if (module.isEnabled())
			{
				int prevW = module.editorWidth();
				int prevH = module.editorHeight();
				Hud.use(module);
				module.render(graphics, mc.font);
				module.rememberSize();
				if (!inEditor && (module.editorWidth() != prevW || module.editorHeight() != prevH))
				{
					layout(modules, screenWidth);
					lastScreenWidth = screenWidth;
				}
			}
		}
	}
}
