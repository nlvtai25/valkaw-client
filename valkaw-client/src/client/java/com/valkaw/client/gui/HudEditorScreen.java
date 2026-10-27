package com.valkaw.client.gui;

import com.valkaw.client.hud.Hud;
import com.valkaw.client.hud.HudRenderer;
import com.valkaw.client.hud.InputState;
import com.valkaw.client.module.Anchor;
import com.valkaw.client.module.HudModule;
import com.valkaw.client.module.ModuleManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class HudEditorScreen extends Screen {
	private static final int CYAN = 0xFF7DF9FF;
	private static final int PURPLE = 0xFFB18CFF;
	private static final int SNAP = 8;
	private static final int MARGIN = 4;
	private static final int LINK = 18;

	private final Screen parent;
	private HudModule dragging;
	private int dragOffX;
	private int dragOffY;
	private int guideX = -1;
	private int guideY = -1;

	public HudEditorScreen() {
		this(null);
	}

	public HudEditorScreen(Screen parent) {
		super(Component.literal("HUD Editor"));
		this.parent = parent;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0x66101828);
		for (int x = 0; x < width; x += 20) {
			graphics.fill(x, 0, x + 1, height, 0x147DF9FF);
		}
		for (int y = 0; y < height; y += 20) {
			graphics.fill(0, y, width, y + 1, 0x147DF9FF);
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		InputState.poll();
		if (dragging == null) {
			HudRenderer.layout(ModuleManager.get().hudModules(), width, true);
		}

		for (HudModule module : ModuleManager.get().hudModules()) {
			if (!module.isEnabled()) {
				continue;
			}
			module.applyAnchor(width);
			Hud.use(module);
			module.render(graphics, font);
			module.rememberSize();
			if (hidden(module)) {
				continue;
			}
			if (module.width() <= 0 || module.height() <= 0) {
				Hud.prefix(graphics, font, module.x(), module.y(), module.prefix());
			}
			boolean hover = over(module, mouseX, mouseY);
			graphics.renderOutline(module.x() - 1, module.y() - 1, width(module) + 2, height(module) + 2,
					hover || module == dragging ? CYAN : 0x557DF9FF);
		}

		if (guideX >= 0) {
			graphics.fill(guideX, 0, guideX + 1, height, 0xAA7DF9FF);
		}
		if (guideY >= 0) {
			graphics.fill(0, guideY, width, guideY + 1, 0xAA7DF9FF);
		}
		graphics.fill(0, 20, 2, height - 20, 0x337DF9FF);
		graphics.fill(width - 2, 20, width, height - 20, 0x337DF9FF);
		graphics.drawString(font, "L", 4, 8, 0x887DF9FF, false);
		graphics.drawString(font, "R", width - 10, 8, 0x887DF9FF, false);
		if (dragging == null) {
			String hint = "DRAG TO A SIDE TO LINK";
			graphics.drawString(font, hint, (width - font.width(hint)) / 2, 8, 0x667DF9FF, false);
		}
		if (dragging != null) {
			String label = dragging.name().toUpperCase() + "  " + dragging.x() + " , " + dragging.y();
			graphics.drawString(font, label, 8, height - 14, PURPLE, false);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			for (HudModule module : ModuleManager.get().hudModules()) {
				if (module.isEnabled() && !hidden(module) && over(module, event.x(), event.y())) {
					dragging = module;
					dragOffX = (int) event.x() - module.x();
					dragOffY = (int) event.y() - module.y();
					guideX = -1;
					guideY = -1;
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging != null) {
			int w = width(dragging);
			int h = height(dragging);
			int x = clamp((int) event.x() - dragOffX, 0, width - w);
			int y = clamp((int) event.y() - dragOffY, 0, height - h);
			Snap snap = snap(dragging, x, y, w, h);
			dragging.moveTo(snap.x, snap.y);
			guideX = snap.guideX;
			guideY = snap.guideY;
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			int centerX = dragging.x() + width(dragging) / 2;
			if (dragging.x() <= LINK) {
				dragging.linkTo(Anchor.LEFT, width);
			} else if (dragging.x() + width(dragging) >= width - LINK) {
				dragging.linkTo(Anchor.RIGHT, width);
			} else {
				dragging.setAnchor(centerX < width / 2 ? Anchor.LEFT : Anchor.RIGHT, width);
			}
			dragging = null;
			guideX = -1;
			guideY = -1;
			ModuleManager.get().save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void onClose() {
		ModuleManager.get().save();
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Magnetic snap to the four sides of other enabled modules and the screen margins. */
	private Snap snap(HudModule moving, int x, int y, int w, int h) {
		int[] bestX = {SNAP + 1, -1};
		int[] bestY = {SNAP + 1, -1};
		consider(bestX, MARGIN, x);
		consider(bestX, width - w - MARGIN, x);
		consider(bestY, MARGIN, y);
		consider(bestY, height - h - MARGIN, y);

		for (HudModule other : ModuleManager.get().hudModules()) {
			if (other == moving || !other.isEnabled() || hidden(other)) {
				continue;
			}
			int ow = width(other);
			int oh = height(other);
			int ox = other.x();
			int oy = other.y();
			consider(bestX, ox - w, x);
			consider(bestX, ox + ow, x);
			consider(bestX, ox, x);
			consider(bestX, ox + ow - w, x);
			consider(bestY, oy - h, y);
			consider(bestY, oy + oh, y);
			consider(bestY, oy, y);
			consider(bestY, oy + oh - h, y);
		}

		int sx = x;
		int sy = y;
		int gx = -1;
		int gy = -1;
		if (bestX[0] <= SNAP) {
			sx = bestX[1];
			gx = sx;
		}
		if (bestY[0] <= SNAP) {
			sy = bestY[1];
			gy = sy;
		}
		return new Snap(sx, sy, gx, gy);
	}

	private static void consider(int[] best, int candidate, int target) {
		int distance = Math.abs(candidate - target);
		if (distance <= best[0]) {
			best[0] = distance;
			best[1] = candidate;
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	/** Zero-size elements (combo idle, no potions) still need a grab area. */
	private static boolean hidden(HudModule module) {
		return (module.width() <= 0 || module.height() <= 0) && !module.editorWhenEmpty();
	}

	private static int width(HudModule module) {
		int w = module.editorWidth();
		return w > 0 ? w : 48;
	}

	private static int height(HudModule module) {
		int h = module.editorHeight();
		return h > 0 ? h : 11;
	}

	private static boolean over(HudModule module, double mouseX, double mouseY) {
		return mouseX >= module.x() && mouseX < module.x() + width(module) && mouseY >= module.y()
				&& mouseY < module.y() + height(module);
	}

	private record Snap(int x, int y, int guideX, int guideY) {
	}
}
