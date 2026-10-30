package com.valkaw.client.module;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public abstract class HudModule extends Module {
	private final int defaultX;
	private final int defaultY;
	protected int x;
	protected int y;
	protected int w;
	protected int h = 11;
	private Anchor anchor = Anchor.LEFT;
	private int rightOffset = 4;
	private String linkId;
	private Link linkSide = Link.NONE;
	private int lastW;
	private int lastH;

	protected HudModule(String id, String name, int x, int y) {
		super(id, name, Category.HUD);
		this.defaultX = x;
		this.defaultY = y;
		this.x = x;
		this.y = y;
	}

	public abstract void render(GuiGraphics graphics, Font font);

	/** Short label used by the HUD editor when the module has nothing to show yet. */
	public String prefix() {
		return name().toUpperCase().replace(" HUD", "");
	}

	/** False for status HUDs that should disappear from the editor while their toggle is off. */
	public boolean editorWhenEmpty() {
		return true;
	}

	public int x() {
		return x;
	}

	public int y() {
		return y;
	}

	public int width() {
		return w;
	}

	public int height() {
		return h;
	}

	/** Editor size uses the last real render, so multi-line modules keep their box in the menu. */
	public int editorWidth() {
		return w > 0 ? w : (lastW > 0 ? lastW : previewWidth());
	}

	public int editorHeight() {
		return h > 0 ? h : (lastH > 0 ? lastH : previewHeight());
	}

	/** Default editor size before the module has rendered once with real data. */
	protected int previewWidth() {
		return 48;
	}

	protected int previewHeight() {
		return 11;
	}

	/** Called after rendering so the editor can keep the real content size between worlds. */
	public void rememberSize() {
		if (w > 0 && h > 0) {
			lastW = w;
			lastH = h;
		}
	}

	/** Right-anchored modules follow the right edge after a resize. */
	public void applyAnchor(int screenWidth) {
		if (linked())
		{
			// The position comes from the element this one is attached to.
			return;
		}
		if (anchor == Anchor.RIGHT)
		{
			x = Math.max(0, screenWidth - rightOffset - anchorWidth());
		}
	}

	public void setAnchor(Anchor side, int screenWidth) {
		if (side == Anchor.RIGHT)
		{
			rightOffset = Math.max(0, screenWidth - (x + anchorWidth()));
		}
		anchor = side;
	}

	/** Explicitly links this HUD to the left or right screen edge. */
	public void linkTo(Anchor side, int screenWidth) {
		unlink();
		anchor = side;
		if (side == Anchor.RIGHT)
		{
			rightOffset = 4;
			x = Math.max(0, screenWidth - anchorWidth() - rightOffset);
		} else {
			x = 4;
		}
	}

	/** Attaches this element to one side of another HUD element. */
	public void linkTo(String id, Link side) {
		linkId = id;
		linkSide = side == null ? Link.NONE : side;
	}

	public void unlink() {
		linkId = null;
		linkSide = Link.NONE;
	}

	public boolean linked() {
		return linkId != null && linkSide != Link.NONE;
	}

	public String linkId() {
		return linkId;
	}

	public Link linkSide() {
		return linkSide;
	}

	/**
	 * Snaps this element against the side of the element it is attached to.
	 * Returns false when the chain is broken, so callers fall back to the anchor.
	 */
	public boolean applyLink() {
		if (!linked())
		{
			return false;
		}
		HudModule parent = parent();
		if (parent == null)
		{
			return false;
		}
		if (linkSide == Link.RIGHT)
		{
			x = parent.x() + parent.anchorWidth();
			y = parent.y();
		} else if (linkSide == Link.LEFT) {
			x = parent.x() - anchorWidth();
			y = parent.y();
		} else if (linkSide == Link.BELOW) {
			x = parent.x();
			y = parent.y() + parent.anchorHeight();
		} else {
			x = parent.x();
			y = parent.y() - anchorHeight();
		}
		return true;
	}

	/** The element this one is attached to, or null when the chain is broken. */
	public HudModule parent() {
		if (!linked())
		{
			return null;
		}
		Object target = ModuleManager.get().byId(linkId);
		if (!(target instanceof HudModule parent) || parent == this || !parent.isEnabled())
		{
			return null;
		}
		// A mutual link would make both elements chase each other every frame.
		HudModule walk = parent;
		for (int depth = 0; walk != null && depth < 8; depth++)
		{
			if (walk == this)
			{
				return null;
			}
			walk = walk.parent();
		}
		return parent;
	}

	private int anchorWidth() {
		return width() > 0 ? width() : editorWidth();
	}

	private int anchorHeight() {
		return height() > 0 ? height() : editorHeight();
	}

	public Anchor anchor() {
		return anchor;
	}

	public void moveTo(int nx, int ny) {
		x = nx;
		y = ny;
	}

	public void resetPosition() {
		unlink();
		anchor = Anchor.LEFT;
		x = defaultX;
		y = defaultY;
	}

	@Override
	public void writeExtra(JsonObject json) {
		json.addProperty("x", x);
		json.addProperty("y", y);
		json.addProperty("anchor", anchor.name());
		json.addProperty("rightOffset", rightOffset);
		json.addProperty("lastW", lastW);
		json.addProperty("lastH", lastH);
		if (linked()) {
			json.addProperty("link", linkId);
			json.addProperty("linkSide", linkSide.name());
		}
	}

	@Override
	public void readExtra(JsonObject json) {
		if (json.has("x")) {
			x = json.get("x").getAsInt();
		}
		if (json.has("y")) {
			y = json.get("y").getAsInt();
		}
		if (json.has("anchor")) {
			anchor = Anchor.parse(json.get("anchor").getAsString());
		}
		if (json.has("rightOffset")) {
			rightOffset = json.get("rightOffset").getAsInt();
		}
		if (json.has("lastW")) {
			lastW = json.get("lastW").getAsInt();
		}
		if (json.has("lastH")) {
			lastH = json.get("lastH").getAsInt();
		}
		if (json.has("link")) {
			linkId = json.get("link").getAsString();
			linkSide = json.has("linkSide") ? Link.parse(json.get("linkSide").getAsString()) : Link.NONE;
		}
	}
}
