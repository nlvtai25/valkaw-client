package com.valkaw.client.gui;

import com.valkaw.client.hud.Hud;
import com.valkaw.client.hud.HudRenderer;
import com.valkaw.client.hud.InputState;
import com.valkaw.client.module.Anchor;
import com.valkaw.client.module.HudModule;
import com.valkaw.client.module.Link;
import com.valkaw.client.module.ModuleManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Drag HUD elements around, drop them on an edge to pin them there. */
public class HudEditorScreen extends Screen {
	private static final int SNAP = 8;
	private static final int MARGIN = 4;
	private static final int LINK = 18;
	/** How close two edges have to be before they count as touching. */
	private static final int TOUCH = 3;

	private final Screen parent;
	private HudModule dragging;
	private HudModule linkParent;
	private Link linkSide = Link.NONE;
	private int linkX;
	private int linkY;
	private int dragOffX;
	private int dragOffY;
	private int guideX = -1;
	private int guideY = -1;
	private int hoverX;
	private int hoverY;
	private int hoverW;
	private int hoverH;
	private boolean hovered;

	public HudEditorScreen() {
		this(null);
	}

	public HudEditorScreen(Screen parent) {
		super(Component.literal("HUD Editor"));
		this.parent = parent;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fillGradient(0, 0, width, height, 0xD2050914, 0xE60A1020);
		for (int x = 0; x < width; x += 28) {
			graphics.fill(x, 0, x + 1, height, 0x0E7DF9FF);
		}
		for (int y = 0; y < height; y += 28) {
			graphics.fill(0, y, width, y + 1, 0x0E7DF9FF);
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		InputState.poll();
		// The dragged element is skipped so it keeps the position under the pointer.
		HudRenderer.layout(ModuleManager.get().hudModules(), width, true, dragging);

		hovered = false;
		for (HudModule module : ModuleManager.get().hudModules()) {
			if (!module.isEnabled()) {
				continue;
			}
			Hud.use(module);
			module.render(graphics, font);
			module.rememberSize();
			if (hidden(module)) {
				continue;
			}
			if (module.width() <= 0 || module.height() <= 0) {
				Hud.prefix(graphics, font, module.x(), module.y(), module.prefix());
			}
			boolean hot = over(module, mouseX, mouseY) && dragging == null;
			if (hot) {
				hovered = true;
				hoverX = module.x();
				hoverY = module.y();
				hoverW = width(module);
				hoverH = height(module);
			}
			drawFrame(graphics, module, hot || module == dragging);
			HudModule parent = module.parent();
			if (parent != null) {
				// Existing links stay visible so the player knows what follows what.
				seam(graphics, module, opposite(module.linkSide()), 0x667DF9FF);
			}
		}

		if (hovered) {
			drawTag(graphics, hoverX, hoverY, hoverW, "MOVE", Ui.TEXT_2);
		}

		if (guideX >= 0) {
			graphics.fill(guideX, 0, guideX + 1, height, 0xAA7DF9FF);
			graphics.fill(guideX - 1, 0, guideX + 2, height, 0x227DF9FF);
		}
		if (guideY >= 0) {
			graphics.fill(0, guideY, width, guideY + 1, 0xAA7DF9FF);
			graphics.fill(0, guideY - 1, width, guideY + 2, 0x227DF9FF);
		}

		drawEdgeZones(graphics);
		drawToolbar(graphics, mouseX, mouseY);
		if (dragging != null) {
			String label = linkParent != null
					? "LINK " + linkSide.name()
					: dragging.name().toUpperCase() + "  " + dragging.x() + ":" + dragging.y();
			drawTag(graphics, dragging.x(), dragging.y() + height(dragging) + 4, width(dragging), label, Ui.ACCENT);
			if (linkParent != null) {
				graphics.renderOutline(linkParent.x() - 1, linkParent.y() - 1, width(linkParent) + 2, height(linkParent) + 2,
						Ui.ACCENT);
				seam(graphics, linkParent, linkSide, Ui.ACCENT);
				seam(graphics, dragging, opposite(linkSide), Ui.ACCENT);
			}
		}
	}

	/** Paints the edge two linked elements share. */
	private void seam(GuiGraphics graphics, HudModule module, Link side, int color)
	{
		int x = module.x();
		int y = module.y();
		int w = width(module);
		int h = height(module);
		if (side == Link.RIGHT)
		{
			graphics.fill(x + w - 2, y, x + w, y + h, color);
		} else if (side == Link.LEFT) {
			graphics.fill(x, y, x + 2, y + h, color);
		} else if (side == Link.BELOW) {
			graphics.fill(x, y + h - 2, x + w, y + h, color);
		} else {
			graphics.fill(x, y, x + w, y + 2, color);
		}
	}

	private static Link opposite(Link side)
	{
		if (side == Link.RIGHT)
		{
			return Link.LEFT;
		}
		if (side == Link.LEFT)
		{
			return Link.RIGHT;
		}
		return side == Link.BELOW ? Link.ABOVE : Link.BELOW;
	}

	private void drawFrame(GuiGraphics graphics, HudModule module, boolean active) {
		int x = module.x();
		int y = module.y();
		int w = width(module);
		int h = height(module);
		int color = active ? Ui.ACCENT : 0x557DF9FF;
		graphics.renderOutline(x - 1, y - 1, w + 2, h + 2, color);
		if (active) {
			graphics.renderOutline(x - 2, y - 2, w + 4, h + 4, 0x337DF9FF);
			corner(graphics, x - 1, y - 1, Ui.ACCENT);
			corner(graphics, x + w + 1, y - 1, Ui.ACCENT);
			corner(graphics, x - 1, y + h + 1, Ui.ACCENT);
			corner(graphics, x + w + 1, y + h + 1, Ui.ACCENT);
		}
	}

	private static void corner(GuiGraphics graphics, int x, int y, int color) {
		graphics.fill(x - 2, y - 2, x + 3, y - 1, color);
		graphics.fill(x - 2, y + 1, x + 3, y + 2, color);
		graphics.fill(x - 2, y - 1, x - 1, y + 1, color);
		graphics.fill(x + 1, y - 1, x + 2, y + 1, color);
	}

	private void drawTag(GuiGraphics graphics, int x, int y, int w, String label, int color) {
		int tw = Ui.width(font, label) + 10;
		int tx = x + w / 2 - tw / 2;
		int ty = Math.max(2, y - 14);
		Ui.round(graphics, tx, ty, tw, 12, 4, 0xE60A1220);
		graphics.drawString(font, label, tx + 5, ty + 3, color, false);
	}

	private void drawEdgeZones(GuiGraphics graphics) {
		boolean nearLeft = dragging != null && dragging.x() <= LINK;
		boolean nearRight = dragging != null && dragging.x() + width(dragging) >= width - LINK;
		int left = nearLeft ? Ui.ACCENT : 0x337DF9FF;
		int right = nearRight ? Ui.ACCENT : 0x337DF9FF;
		graphics.fill(0, 24, 2, height - 24, left);
		graphics.fill(width - 2, 24, width, height - 24, right);
		graphics.drawString(font, "L", 4, 10, nearLeft ? Ui.ACCENT : Ui.MUTED, false);
		graphics.drawString(font, "R", width - 10, 10, nearRight ? Ui.ACCENT : Ui.MUTED, false);
	}

	private void drawToolbar(GuiGraphics graphics, int mouseX, int mouseY) {
		int w = Math.min(430, width - 20);
		int x = (width - w) / 2;
		int y = 6;
		Ui.panel(graphics, x, y, w, 24, Ui.R_PILL, 0xF40A1220);
		graphics.drawString(font, "HUD EDITOR", x + 10, y + 8, Ui.ACCENT, false);

		String hint = dragging == null ? "DRAG TO MOVE  ·  TOUCH EDGES TO LINK" : "TOUCH AN EDGE TO LINK  ·  DROP IN THE OPEN TO FREE";
		int hintW = Ui.width(font, hint);
		if (w > hintW + 150) {
			graphics.drawString(font, hint, x + w / 2 - hintW / 2, y + 8, Ui.MUTED, false);
		}

		int doneW = Ui.width(font, "Done") + 16;
		int doneX = x + w - doneW - 6;
		boolean hot = over(mouseX, mouseY, doneX, y + 3, doneW, 18);
		Ui.button(graphics, font, doneX, y + 3, "Done", Ui.ACCENT, hot, true);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			int w = Math.min(430, width - 20);
			int x = (width - w) / 2;
			int doneW = Ui.width(font, "Done") + 16;
			if (over(event.x(), event.y(), x + w - doneW - 6, 9, doneW, 18)) {
				AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
				onClose();
				return true;
			}

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
			Target target = touch(dragging, snap.x, snap.y, w, h);
			if (target != null) {
				// Flush against another element: keep it exactly side by side.
				linkParent = target.parent;
				linkSide = target.side;
				linkX = target.x;
				linkY = target.y;
				dragging.moveTo(linkX, linkY);
				guideX = -1;
				guideY = -1;
			} else {
				linkParent = null;
				linkSide = Link.NONE;
				dragging.moveTo(snap.x, snap.y);
				guideX = snap.guideX;
				guideY = snap.guideY;
			}
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			if (linkParent != null) {
				dragging.linkTo(linkParent.id(), linkSide);
				dragging.moveTo(linkX, linkY);
			} else if (dragging.x() <= LINK) {
				dragging.linkTo(Anchor.LEFT, width);
			} else if (dragging.x() + width(dragging) >= width - LINK) {
				dragging.linkTo(Anchor.RIGHT, width);
			} else {
				// Dropped in the open: it is free again, only the screen anchor stays.
				dragging.unlink();
				int centerX = dragging.x() + width(dragging) / 2;
				dragging.setAnchor(centerX < width / 2 ? Anchor.LEFT : Anchor.RIGHT, width);
			}
			dragging = null;
			linkParent = null;
			linkSide = Link.NONE;
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

	/**
	 * Finds the element the dragged one is flush against. Only edges with no gap
	 * that also overlap along the other axis count, so dropping an element below
	 * another one is not a link.
	 */
	private Target touch(HudModule moving, int x, int y, int w, int h)
	{
		Target best = null;
		for (HudModule other : ModuleManager.get().hudModules())
		{
			if (other == moving || !other.isEnabled() || hidden(other) || other.parent() == moving)
			{
				continue;
			}
			int ox = other.x();
			int oy = other.y();
			int ow = width(other);
			int oh = height(other);
			if (overlap(y, h, oy, oh) > 2)
			{
				if (Math.abs(x - (ox + ow)) <= TOUCH)
				{
					best = closer(best, new Target(other, Link.RIGHT, ox + ow, oy), x, y);
				}
				if (Math.abs(x + w - ox) <= TOUCH)
				{
					best = closer(best, new Target(other, Link.LEFT, ox - w, oy), x, y);
				}
			}
			if (overlap(x, w, ox, ow) > 2)
			{
				if (Math.abs(y - (oy + oh)) <= TOUCH)
				{
					best = closer(best, new Target(other, Link.BELOW, ox, oy + oh), x, y);
				}
				if (Math.abs(y + h - oy) <= TOUCH)
				{
					best = closer(best, new Target(other, Link.ABOVE, ox, oy - h), x, y);
				}
			}
		}
		return best;
	}

	private static Target closer(Target best, Target candidate, int x, int y)
	{
		if (best == null)
		{
			return candidate;
		}
		return score(candidate, x, y) < score(best, x, y) ? candidate : best;
	}

	private static int score(Target target, int x, int y)
	{
		return Math.abs(target.x() - x) + Math.abs(target.y() - y);
	}

	private static int overlap(int a, int alen, int b, int blen)
	{
		return Math.min(a + alen, b + blen) - Math.max(a, b);
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

	private static boolean over(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private record Snap(int x, int y, int guideX, int guideY) {
	}

	/** Element the dragged one is flush against, and the side it would join. */
	private record Target(HudModule parent, Link side, int x, int y) {
	}
}
