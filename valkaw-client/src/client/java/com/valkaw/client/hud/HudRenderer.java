package com.valkaw.client.hud;

import com.valkaw.client.module.HudModule;
import com.valkaw.client.module.ModuleManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class HudRenderer {
	private static final int GAP = 2;
	private static final Comparator<HudModule> BY_X_Y = Comparator.comparingInt(HudModule::x)
			.thenComparingInt(HudModule::y);
	private static final Comparator<HudModule> BY_Y = Comparator.comparingInt(HudModule::y);
	/** Reused across frames so layout allocates nothing once warm. */
	private static final List<List<HudModule>> COLUMNS = new ArrayList<>();
	private static int used;

	private HudRenderer() {
	}

	/** Stacks HUD modules that overlap horizontally, using their real rendered height. */
	public static void layout(List<HudModule> modules, int screenWidth, boolean editor) {
		for (HudModule module : modules) {
			if (module.isEnabled()) {
				module.applyAnchor(screenWidth);
			}
		}
		modules.sort(BY_X_Y);
		used = 0;
		for (HudModule module : modules) {
			if (!module.isEnabled()) {
				continue;
			}
			if (editor && !module.editorWhenEmpty() && module.height() <= 0) {
				continue;
			}
			int w = editor ? module.editorWidth() : module.width();
			int h = editor ? module.editorHeight() : module.height();
			if (w <= 0 || h <= 0) {
				continue;
			}
			List<HudModule> column = null;
			for (int i = 0; i < used; i++) {
				List<HudModule> candidate = COLUMNS.get(i);
				if (overlaps(candidate, module.x(), w, editor)) {
					column = candidate;
					break;
				}
			}
			if (column == null) {
				if (used == COLUMNS.size()) {
					COLUMNS.add(new ArrayList<>());
				}
				column = COLUMNS.get(used++);
				column.clear();
			}
			column.add(module);
		}

		for (int i = 0; i < used; i++) {
			List<HudModule> column = COLUMNS.get(i);
			column.sort(BY_Y);
			int y = column.get(0).y();
			for (HudModule module : column) {
				module.moveTo(module.x(), y);
				int height = editor ? module.editorHeight() : module.height();
				y += height > 0 ? height : 11;
				y += GAP;
			}
		}
	}

	private static boolean overlaps(List<HudModule> column, int x, int w, boolean editor) {
		for (HudModule other : column) {
			int ow = editor ? other.editorWidth() : other.width();
			if (ow <= 0) {
				ow = 48;
			}
			if (x < other.x() + ow && x + w > other.x()) {
				return true;
			}
		}
		return false;
	}

	public static void render(GuiGraphics graphics, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.screen != null || mc.options.hideGui) {
			return;
		}
		InputState.poll();
		int screenWidth = graphics.guiWidth();
		List<HudModule> modules = ModuleManager.get().hudModules();
		layout(modules, screenWidth, false);
		for (HudModule module : modules) {
			if (module.isEnabled()) {
				module.applyAnchor(screenWidth);
				Hud.use(module);
				module.render(graphics, mc.font);
				module.rememberSize();
			}
		}
	}
}
