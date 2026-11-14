package com.valkaw.client.gui;

import java.util.HashMap;
import java.util.Map;
import com.valkaw.client.module.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * Valkaw interface kit. Soft dark surfaces, one rounded scale, one accent.
 * Everything draws with plain fills so the Vulkan and OpenGL builds match.
 */
public final class Ui {
	// Surfaces, darkest to lightest. Deep-space navy, tuned for the gold accent.
	public static final int BG_TOP = 0xFF0B0F1C;
	public static final int BG_BOTTOM = 0xFF141D33;
	public static final int SURFACE = 0xD9131A2E;
	public static final int SURFACE_2 = 0xFF1B2338;
	public static final int SURFACE_3 = 0xFF252E47;
	public static final int TRACK = 0xFF2C3652;

	// Structure.
	public static final int LINE = 0x248A9BC4;
	public static final int LINE_SOFT = 0x148A9BC4;

	// Accents.
	public static final int ACCENT = 0xFFE3B24B;
	public static final int COPPER = 0xFFC98A5B;
	public static final int GREEN = 0xFF9BC98A;
	public static final int AMBER = 0xFFF2C879;
	public static final int RED = 0xFFE07A6A;

	// Text.
	public static final int TEXT = 0xFFF2F1EC;
	public static final int TEXT_2 = 0xFFCBD6E6;
	public static final int MUTED = 0xFF8794AC;
	public static final int INK = 0xFF0A0E1A;

	// Corner radii.
	public static final int R_WINDOW = 16;
	public static final int R_PANEL = 14;
	public static final int R_CARD = 12;
	public static final int R_PILL = 8;

	private static final Style MONO = Style.EMPTY.withFont(new FontDescription.Resource(Minecraft.DEFAULT_FONT));
	private static final Map<String, Component> MONO_CACHE = new HashMap<>();
	private static final Map<String, Integer> WIDTHS = new HashMap<>();
	/** fit() results; labels repeat every frame while a screen is open. */
	private static final Map<String, String> FIT_CACHE = new HashMap<>();
	private static Font cachedFont;
	private static long lastFrame = System.nanoTime();
	/** Per-radius corner inset table; round() used to sqrt() once per row per call. */
	private static final int[][] INSETS = new int[65][];

	private Ui() {
	}

	// ---------------------------------------------------------------- motion

	/** Frame delta in seconds, clamped so a stalled frame never jumps the animation. */
	public static float dt() {
		long now = System.nanoTime();
		float dt = (now - lastFrame) / 1_000_000_000.0F;
		lastFrame = now;
		return Math.max(0.0F, Math.min(0.05F, dt));
	}

	/** Ease-out cubic: fast start, soft landing. */
	public static float ease(float t) {
		float c = clamp01(t);
		return 1.0F - (1.0F - c) * (1.0F - c) * (1.0F - c);
	}

	public static float clamp01(float value) {
		return Math.max(0.0F, Math.min(1.0F, value));
	}

	public static float lerp(float from, float to, float t) {
		return from + (to - from) * t;
	}

	/** A value that eases toward a target every frame. */
	public static final class Anim {
		private float value;
		private float target;
		private float speed = 14.0F;

		public Anim(float start) {
			value = start;
			target = start;
		}

		public Anim speed(float framesPerSecond) {
			speed = framesPerSecond;
			return this;
		}

		public Anim to(float next) {
			target = next;
			return this;
		}

		public float update(float dt) {
			float step = Math.min(1.0F, dt * speed);
			value += (target - value) * step;
			if (Math.abs(target - value) < 0.001F) {
				value = target;
			}
			return value;
		}

		public float update() {
			return update(Ui.dt());
		}

		public float value() {
			return value;
		}
	}

	// ---------------------------------------------------------------- colour

	public static int alpha(int color, float amount) {
		int a = (int) (((color >>> 24) & 0xFF) * clamp01(amount));
		return (a << 24) | (color & 0xFFFFFF);
	}

	/** Display label for a module category: "Performance", "Utility", "HUD". */
	public static String categoryLabel(Category category) {
		return category == Category.PERFORMANCE ? "Performance"
				: category == Category.UTILITY ? "Utility" : "HUD";
	}

	public static int mix(int from, int to, float t) {
		float f = clamp01(t);
		int a = (int) lerp((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, f);
		int r = (int) lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, f);
		int g = (int) lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, f);
		int b = (int) lerp(from & 0xFF, to & 0xFF, f);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** Reduce chroma while keeping opacity and lightness: blends the colour
	 toward its own grey value, so the "density" fades without transparency. */
	public static int desaturate(int color, float amount) {
		int r = (color >> 16) & 0xFF;
		int g = (color >> 8) & 0xFF;
		int b = color & 0xFF;
		int gray = (int) (r * 0.299F + g * 0.587F + b * 0.114F);
		int rgb = (gray << 16) | (gray << 8) | gray;
		return mix(color & 0xFFFFFF, rgb, amount) | (color & 0xFF000000);
	}

	public static int lighten(int color, float amount) {
		return mix(color, 0xFFFFFFFF, amount);
	}

	// ---------------------------------------------------------------- shapes

	public static void rect(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
		if (w > 0 && h > 0 && (color >>> 24) != 0) {
			graphics.fill(x, y, x + w, y + h, color);
		}
	}

	/**
	 * Rounded rectangle. Only the corner rows need per-scanline insets; the
	 * straight middle is one fill, so a tall panel costs ~2r+1 draws, not h.
	 */
	public static void round(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius, int color) {
		if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
			return;
		}
		int r = Math.min(radius, Math.min(w / 2, h / 2));
		if (r <= 0) {
			graphics.fill(x, y, x + w, y + h, color);
			return;
		}
		// r <= h/2, so the middle band never overlaps the corner rows.
		int[] insets = insets(r);
		for (int row = 0; row < r; row++) {
			int inset = insets[row];
			graphics.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
		}
		graphics.fill(x, y + r, x + w, y + h - r, color);
		for (int row = h - r; row < h; row++) {
			int inset = insets[h - 1 - row];
			graphics.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
		}
	}

	/** Hairline outline of a rounded rect, matching round()'s corners, so a
	 hover border does not read as a square box around a rounded fill. */
	public static void roundOutline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius, int color) {
		if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
			return;
		}
		int r = Math.min(radius, Math.min(w / 2, h / 2));
		if (r <= 0) {
			graphics.outline(x, y, w, h, color);
			return;
		}
		int[] insets = insets(r);
		for (int row = 0; row < r; row++) {
			int inset = insets[row];
			graphics.fill(x + inset, y + row, x + inset + 1, y + row + 1, color);
			graphics.fill(x + w - 1 - inset, y + row, x + w - inset, y + row + 1, color);
			graphics.fill(x + inset, y + h - 1 - row, x + inset + 1, y + h - row, color);
			graphics.fill(x + w - 1 - inset, y + h - 1 - row, x + w - inset, y + h - row, color);
		}
		graphics.fill(x + r, y, x + w - r, y + 1, color);
		graphics.fill(x + r, y + h - 1, x + w - r, y + h, color);
		graphics.fill(x, y + r, x + 1, y + h - r, color);
		graphics.fill(x + w - 1, y + r, x + w, y + h - r, color);
	}

	/** Corner inset per row for a given radius, computed once per radius. */
	private static int[] insets(int radius) {
		if (radius >= INSETS.length) {
			// Uncached fallback for unusually large radii.
			int[] table = new int[radius];
			for (int row = 0; row < radius; row++) {
				table[row] = inset(row, radius);
			}
			return table;
		}
		int[] table = INSETS[radius];
		if (table == null) {
			table = new int[radius];
			for (int row = 0; row < radius; row++) {
				table[row] = inset(row, radius);
			}
			INSETS[radius] = table;
		}
		return table;
	}

	private static int inset(int row, int radius) {
		double dy = radius - row - 0.5;
		double dx = Math.sqrt(Math.max(0.0, (double) radius * radius - dy * dy));
		return Math.max(0, radius - (int) Math.round(dx));
	}

	/** Card body: soft drop shadow, hairline border, solid surface. */
	public static void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius, int fill) {
		round(graphics, x + 1, y + 4, w, h, radius, 0x30000000);
		round(graphics, x, y, w, h, radius, LINE);
		round(graphics, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), fill);
	}

	/** Window body with a faint highlight along the top edge. */
	public static void window(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int accent) {
		round(graphics, x + 2, y + 8, w, h, R_WINDOW, 0x40000000);
		round(graphics, x, y, w, h, R_WINDOW, LINE);
		round(graphics, x + 1, y + 1, w - 2, h - 2, R_WINDOW - 1, SURFACE);
		round(graphics, x + R_WINDOW, y + 1, w - R_WINDOW * 2, 1, 0, 0x18FFFFFF);
	}

	private static final int[][] CIRCLE_SPANS = new int[33][];

	public static void circle(GuiGraphicsExtractor graphics, int cx, int cy, int radius, int color) {
		if ((color >>> 24) == 0 || radius < 0) {
			return;
		}
		int[] spans = spans(radius);
		for (int dy = -radius; dy <= radius; dy++) {
			int span = spans[dy + radius];
			graphics.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, color);
		}
	}

	/** A soft sphere: a base disc plus an offset highlight disc, so moons read
	 as round 3D-shaded bodies like the web/launcher scene. */
	public static void sphere(GuiGraphicsExtractor graphics, int cx, int cy, int radius, int base, int highlight) {
		if (radius < 1 || (base >>> 24) == 0) {
			return;
		}
		circle(graphics, cx, cy, radius, base);
		if ((highlight >>> 24) == 0 || radius < 3) {
			return;
		}
		int hr = Math.max(1, Math.round(radius * 0.55F));
		int hx = cx - Math.round(radius * 0.35F);
		int hy = cy - Math.round(radius * 0.35F);
		circle(graphics, hx, hy, hr, highlight);
	}

	/** Anti-aliased filled disc at a fractional centre: edge pixels get alpha
	 proportional to how much of the pixel the disc covers, so motion along
	 sub-pixel paths stays smooth and steady (no brightness flicker). */
	public static void discAA(GuiGraphicsExtractor graphics, float cx, float cy, float radius, int color) {
		if ((color >>> 24) == 0 || radius < 1.0F) {
			return;
		}
		int alpha = color >>> 24;
		int rgb = color & 0xFFFFFF;
		int minX = (int) Math.floor(cx - radius);
		int maxX = (int) Math.floor(cx + radius);
		int minY = (int) Math.floor(cy - radius);
		int maxY = (int) Math.floor(cy + radius);
		float r2 = radius * radius;
		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				float dx = (x + 0.5F) - cx;
				float dy = (y + 0.5F) - cy;
				float d2 = dx * dx + dy * dy;
				if (d2 > r2) {
					continue;
				}
				// Pixel coverage: 1 fully inside; fractional near the edge.
				float cov = radius - (float) Math.sqrt(d2);
				if (cov > 1.0F) {
					cov = 1.0F;
				}
				if (cov <= 0.0F) {
					continue;
				}
				int a = Math.max(1, Math.round(alpha * cov));
				graphics.fill(x, y, x + 1, y + 1, (a << 24) | rgb);
			}
		}
	}

	/** Soft shaded sphere with sub-pixel placement: base disc plus an offset
	 highlight disc, both anti-aliased. Replaces sphere() for animated moons. */
	public static void sphereAA(GuiGraphicsExtractor graphics, float cx, float cy, float radius, int base, int highlight) {
		if (radius < 1.0F || (base >>> 24) == 0) {
			return;
		}
		discAA(graphics, cx, cy, radius, base);
		if ((highlight >>> 24) == 0 || radius < 3.0F) {
			return;
		}
		float hr = Math.max(1.0F, radius * 0.55F);
		float hx = cx - radius * 0.35F;
		float hy = cy - radius * 0.35F;
		discAA(graphics, hx, hy, hr, highlight);
	}

	/** Half-width of each circle row, computed once per radius. */
	private static int[] spans(int radius) {
		if (radius >= CIRCLE_SPANS.length) {
			int[] table = new int[radius * 2 + 1];
			for (int dy = -radius; dy <= radius; dy++) {
				table[dy + radius] = (int) Math.sqrt(Math.max(0, radius * radius - dy * dy));
			}
			return table;
		}
		int[] table = CIRCLE_SPANS[radius];
		if (table == null) {
			table = new int[radius * 2 + 1];
			for (int dy = -radius; dy <= radius; dy++) {
				table[dy + radius] = (int) Math.sqrt(Math.max(0, radius * radius - dy * dy));
			}
			CIRCLE_SPANS[radius] = table;
		}
		return table;
	}

	/** Horizontal gradient approximated with vertical slices. */
	public static void hGradient(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int from, int to, int steps) {
		if (w <= 0 || h <= 0) {
			return;
		}
		int slices = Math.max(1, Math.min(steps, w));
		for (int i = 0; i < slices; i++) {
			int x1 = x + w * i / slices;
			int x2 = x + w * (i + 1) / slices;
			graphics.fill(x1, y, x2, y + h, mix(from, to, slices == 1 ? 0.0F : (float) i / (slices - 1)));
		}
	}

	public static void divider(GuiGraphicsExtractor graphics, int x, int y, int w) {
		graphics.fill(x, y, x + w, y + 1, LINE_SOFT);
	}

	public static void vDivider(GuiGraphicsExtractor graphics, int x, int y, int h) {
		graphics.fill(x, y, x + 1, y + h, LINE_SOFT);
	}

	// ---------------------------------------------------------------- text

	public static int width(Font font, String text) {
		if (text == null || text.isEmpty()) {
			return 0;
		}
		if (font != cachedFont) {
			WIDTHS.clear();
			cachedFont = font;
		}
		Integer cached = WIDTHS.get(text);
		if (cached != null) {
			return cached;
		}
		int measured = font.width(text);
		if (WIDTHS.size() > 512) {
			WIDTHS.clear();
		}
		WIDTHS.put(text, measured);
		return measured;
	}

	public static void text(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
		if (text != null && !text.isEmpty()) {
			graphics.text(font, text, x, y, color, false);
		}
	}

	public static void textRight(GuiGraphicsExtractor graphics, Font font, String text, int right, int y, int color) {
		text(graphics, font, text, right - width(font, text), y, color);
	}

	public static void textCenter(GuiGraphicsExtractor graphics, Font font, String text, int cx, int y, int color) {
		text(graphics, font, text, cx - width(font, text) / 2, y, color);
	}

	/** Small section label. Plain text now, no monospace or tracking. */
	public static void tracked(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
		text(graphics, font, text, x, y, color);
	}

	public static Component mono(String text) {
		Component cached = MONO_CACHE.get(text);
		if (cached != null) {
			return cached;
		}
		Component component = Component.literal(text).withStyle(MONO);
		if (MONO_CACHE.size() > 256) {
			MONO_CACHE.clear();
		}
		MONO_CACHE.put(text, component);
		return component;
	}

	/** Trims text to fit, appending an ellipsis when it does not. */
	public static String fit(Font font, String text, int maxWidth) {
		if (text == null || text.isEmpty()) {
			return text == null ? "" : text;
		}
		if (font.width(text) <= maxWidth) {
			return text;
		}
		String key = maxWidth + "|" + text;
		String cached = FIT_CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		// Measure directly on the font: width() would cache one throwaway entry per
		// candidate length and clear the shared cache once it fills up.
		int low = 0;
		int high = text.length();
		while (low < high) {
			int mid = (low + high + 1) >>> 1;
			if (font.width(text.substring(0, mid) + "...") <= maxWidth) {
				low = mid;
			} else {
				high = mid - 1;
			}
		}
		String fitted = text.substring(0, Math.max(1, low)) + "...";
		if (FIT_CACHE.size() > 512) {
			FIT_CACHE.clear();
		}
		FIT_CACHE.put(key, fitted);
		return fitted;
	}

	// ---------------------------------------------------------------- widgets

	/** 22x12 pill switch; {@code amount} is the eased knob position from 0 to 1. */
	public static void toggle(GuiGraphicsExtractor graphics, int x, int y, float amount, int accent) {
		round(graphics, x, y, 22, 12, 6, mix(TRACK, accent, amount));
		if (amount > 0.02F) {
			round(graphics, x, y, 22, 12, 6, alpha(accent, amount * 0.9F));
		}
		int knobX = x + 6 + Math.round(10 * amount);
		circle(graphics, knobX, y + 6, 4, 0xFFF7F9FC);
	}

	/** Thin slider; {@code t} is the eased position. */
	public static void slider(GuiGraphicsExtractor graphics, int x, int y, int w, float t, int accent) {
		round(graphics, x, y - 1, w, 4, 2, TRACK);
		int filled = Math.max(0, Math.round(w * t));
		if (filled > 0) {
			round(graphics, x, y - 1, filled, 4, 2, accent);
		}
		int knob = x + filled;
		circle(graphics, knob, y + 1, 4, alpha(0xFF000000, 0.35F));
		circle(graphics, knob, y + 1, 3, 0xFFF7F9FC);
	}

	public static void scrollbar(GuiGraphicsExtractor graphics, int x, int y, int h, float offset, float visible, int accent) {
		round(graphics, x, y, 3, h, 2, 0x1FFFFFFF);
		int thumb = Math.max(20, Math.round(h * visible));
		int travel = Math.max(1, h - thumb);
		round(graphics, x, y + Math.round(travel * clamp01(offset)), 3, thumb, 2, alpha(0xFFFFFFFF, 0.22F));
	}

	public static void chip(GuiGraphicsExtractor graphics, Font font, int x, int y, String label, int accent) {
		int w = width(font, label) + 10;
		round(graphics, x, y, w, 13, 4, alpha(accent, 0.12F));
		text(graphics, font, label, x + 5, y + 3, accent);
	}

	/** Small right-pointing chevron drawn from rects so it stays pixel sharp. */
	public static void chevron(GuiGraphicsExtractor graphics, int x, int y, int color, boolean right) {
		for (int i = 0; i < 4; i++) {
			int px = right ? x + i : x + 3 - i;
			graphics.fill(px, y + i, px + 1, y + i + 1, color);
			graphics.fill(px, y + 6 - i, px + 1, y + 7 - i, color);
		}
	}

	public static void closeIcon(GuiGraphicsExtractor graphics, int x, int y, int color) {
		for (int i = 0; i < 7; i++) {
			graphics.fill(x + i, y + i, x + i + 1, y + i + 1, color);
			graphics.fill(x + 6 - i, y + i, x + 7 - i, y + i + 1, color);
		}
	}

	public static void checkIcon(GuiGraphicsExtractor graphics, int x, int y, int color) {
		for (int i = 0; i < 3; i++) {
			graphics.fill(x + i, y + 2 + i, x + i + 1, y + 3 + i, color);
		}
		for (int i = 0; i < 5; i++) {
			graphics.fill(x + 2 + i, y + 4 - i, x + 3 + i, y + 5 - i, color);
		}
	}

	/** Compact button. Returns its width so callers can lay out rows of them. */
	public static int button(GuiGraphicsExtractor graphics, Font font, int x, int y, String label, int accent,
			boolean hover, boolean filled) {
		int w = width(font, label) + 16;
		if (filled) {
			round(graphics, x, y, w, 18, R_PILL, accent);
			text(graphics, font, label, x + 8, y + 5, INK);
		} else {
			round(graphics, x, y, w, 18, R_PILL, hover ? 0x1FFFFFFF : 0x0FFFFFFF);
			text(graphics, font, label, x + 8, y + 5, hover ? TEXT : TEXT_2);
		}
		return w;
	}

	// Ring system, radii as a multiple of the planet and the colour of each band.

	/** Saturn: a banded planet inside a tilted ring system, built from plain fills. */


	/** Dark band the rings cast across the planet. */

	/**
	 * Fills one elliptical ring band row by row so the edges stay smooth.
	 * {@code side} -1 draws the half behind the planet, 1 the half in front, 0 both.
	 */

	/** Horizontal extent of a rotated ellipse at the given row, stored in SPAN. */




	// ---------------------------------------------------------------- icons

	/**
	 * Line icon set drawn from plain fills, centred on {@code cx}/{@code cy}.
	 * Keys are short names; unknown keys fall back to a 2x2 grid.
	 */
	public static void icon(GuiGraphicsExtractor graphics, String key, int cx, int cy, int color) {
		int soft = alpha(color, 0.5F);
		switch (key) {
			case "power" -> {
				// The gap belongs at the top, where the stem enters the ring; a gap
				// on the right read as a chopped-off right edge.
				for (int d = 290; d <= 610; d += 4) {
					double a = Math.toRadians(d);
					int px = cx + (int) Math.round(7 * Math.cos(a));
					int py = cy + (int) Math.round(7 * Math.sin(a));
					graphics.fill(px, py, px + 1, py + 1, color);
				}
				graphics.fill(cx, cy - 8, cx + 1, cy - 1, color);
			}
			case "layers" -> {
				round(graphics, cx - 7, cy - 6, 14, 3, 2, color);
				round(graphics, cx - 5, cy - 1, 10, 3, 2, soft);
				round(graphics, cx - 3, cy + 3, 6, 3, 2, alpha(color, 0.32F));
			}
			case "bolt" -> {
				for (int i = 0; i < 7; i++) {
					int x = cx + 3 - i / 3;
					graphics.fill(x - 1, cy - 7 + i, x + 2, cy - 6 + i, color);
				}
				for (int i = 0; i < 7; i++) {
					int x = cx - 2 + i / 3;
					graphics.fill(x - 1, cy - 1 + i, x + 2, cy + i, color);
				}
			}
			case "wrench" -> {
				for (int i = 0; i < 9; i++) {
					graphics.fill(cx - 5 + i, cy + 5 - i, cx - 3 + i, cy + 7 - i, color);
				}
				circle(graphics, cx + 3, cy - 4, 3, soft);
				circle(graphics, cx + 3, cy - 4, 1, 0x00000000);
			}
			case "gauge" -> {
				for (int a = 165; a <= 375; a += 5) {
					double rad = Math.toRadians(a);
					int px = cx + (int) Math.round(6 * Math.cos(rad));
					int py = cy + (int) Math.round(6 * Math.sin(rad));
					graphics.fill(px, py, px + 1, py + 1, color);
				}
				for (int i = 0; i < 5; i++) {
					graphics.fill(cx + i, cy - i / 2 + 2, cx + i + 1, cy - i / 2 + 3, color);
				}
				graphics.fill(cx - 1, cy + 2, cx + 1, cy + 4, color);
			}
			case "signal" -> {
				for (int bar = 0; bar < 3; bar++) {
					int h = 3 + bar * 3;
					round(graphics, cx - 6 + bar * 5, cy + 6 - h, 3, h, 1, color);
				}
			}
			case "compass" -> {
				for (int a = 0; a < 360; a += 6) {
					double rad = Math.toRadians(a);
					int px = cx + (int) Math.round(7 * Math.cos(rad));
					int py = cy + (int) Math.round(7 * Math.sin(rad));
					graphics.fill(px, py, px + 1, py + 1, soft);
				}
				graphics.fill(cx - 1, cy - 5, cx + 1, cy + 1, color);
				graphics.fill(cx - 1, cy + 1, cx + 4, cy + 3, color);
			}
			case "shield" -> {
				for (int dy = -6; dy <= 6; dy++) {
					int hw = dy < 0 ? 6 : Math.max(1, 6 - (dy + 1) / 2);
					graphics.fill(cx - hw, cy + dy, cx + hw, cy + dy + 1, dy < 0 ? color : soft);
				}
			}
			case "flask" -> {
				round(graphics, cx - 2, cy - 7, 4, 5, 2, color);
				round(graphics, cx - 6, cy - 3, 12, 10, 5, color);
			}
			case "keyboard" -> {
				round(graphics, cx - 7, cy - 4, 14, 9, 3, soft);
				for (int i = 0; i < 4; i++) {
					graphics.fill(cx - 5 + i * 3, cy - 2, cx - 4 + i * 3, cy - 1, color);
				}
				graphics.fill(cx - 4, cy + 1, cx + 4, cy + 2, color);
			}
			case "chip" -> {
				graphics.fill(cx - 5, cy - 5, cx + 5, cy - 4, color);
				graphics.fill(cx - 5, cy + 4, cx + 5, cy + 5, color);
				graphics.fill(cx - 5, cy - 5, cx - 4, cy + 5, color);
				graphics.fill(cx + 4, cy - 5, cx + 5, cy + 5, color);
				graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, soft);
				for (int i = 0; i < 3; i++) {
					graphics.fill(cx - 6, cy - 3 + i * 3, cx - 4, cy - 2 + i * 3, color);
					graphics.fill(cx + 4, cy - 3 + i * 3, cx + 6, cy - 2 + i * 3, color);
				}
			}
			case "clock" -> {
				for (int a = 0; a < 360; a += 5) {
					double rad = Math.toRadians(a);
					int px = cx + (int) Math.round(6 * Math.cos(rad));
					int py = cy + (int) Math.round(6 * Math.sin(rad));
					graphics.fill(px, py, px + 1, py + 1, color);
				}
				graphics.fill(cx, cy - 4, cx + 1, cy + 1, color);
				graphics.fill(cx, cy, cx + 4, cy + 1, color);
			}
			case "chat" -> {
				round(graphics, cx - 7, cy - 6, 14, 10, 3, soft);
				graphics.fill(cx - 3, cy + 3, cx + 1, cy + 6, color);
				for (int i = 0; i < 3; i++) {
					graphics.fill(cx - 4 + i * 3, cy - 2, cx - 3 + i * 3, cy - 1, color);
				}
			}
			case "eye" -> {
				for (int dx = -7; dx <= 7; dx++) {
					int h = 5 - Math.abs(dx) / 2;
					graphics.fill(cx + dx, cy - h, cx + dx + 1, cy + h + 1, dx % 3 == 0 ? color : soft);
				}
				circle(graphics, cx, cy, 2, color);
			}
			case "target" -> {
				for (int a = 0; a < 360; a += 6) {
					double rad = Math.toRadians(a);
					int px = cx + (int) Math.round(7 * Math.cos(rad));
					int py = cy + (int) Math.round(7 * Math.sin(rad));
					graphics.fill(px, py, px + 1, py + 1, soft);
				}
				circle(graphics, cx, cy, 2, color);
			}
			case "crosshair" -> {
				graphics.fill(cx - 7, cy, cx - 2, cy + 1, color);
				graphics.fill(cx + 2, cy, cx + 8, cy + 1, color);
				graphics.fill(cx, cy - 7, cx + 1, cy - 2, color);
				graphics.fill(cx, cy + 2, cx + 1, cy + 8, color);
			}
			case "cube" -> {
				graphics.fill(cx - 6, cy, cx, cy + 1, color);
				graphics.fill(cx, cy, cx + 6, cy + 1, color);
				graphics.fill(cx - 6, cy - 5, cx - 4, cy + 1, color);
				graphics.fill(cx + 4, cy - 5, cx + 6, cy + 1, color);
				graphics.fill(cx - 2, cy - 8, cx + 2, cy - 3, soft);
			}
			case "users" -> {
				circle(graphics, cx - 3, cy - 4, 3, color);
				round(graphics, cx - 8, cy + 1, 10, 6, 3, soft);
				circle(graphics, cx + 4, cy - 2, 2, alpha(color, 0.7F));
				round(graphics, cx + 1, cy + 2, 7, 5, 2, alpha(color, 0.4F));
			}
			case "sun" -> {
				circle(graphics, cx, cy, 3, color);
				graphics.fill(cx - 1, cy - 8, cx + 1, cy - 5, soft);
				graphics.fill(cx - 1, cy + 6, cx + 1, cy + 9, soft);
				graphics.fill(cx - 8, cy - 1, cx - 5, cy + 1, soft);
				graphics.fill(cx + 6, cy - 1, cx + 9, cy + 1, soft);
			}
			case "droplet" -> {
				graphics.fill(cx - 1, cy - 8, cx + 1, cy - 4, color);
				circle(graphics, cx, cy + 1, 4, soft);
				circle(graphics, cx, cy + 1, 2, color);
			}
			case "arrow" -> {
				graphics.fill(cx - 7, cy, cx + 3, cy + 1, color);
				graphics.fill(cx + 1, cy - 4, cx + 2, cy + 5, color);
				graphics.fill(cx + 3, cy - 3, cx + 4, cy + 4, color);
				graphics.fill(cx + 5, cy - 2, cx + 6, cy + 3, color);
			}
			case "sparkle" -> {
				graphics.fill(cx - 1, cy - 7, cx + 1, cy + 7, color);
				graphics.fill(cx - 7, cy - 1, cx + 7, cy + 1, color);
				graphics.fill(cx - 3, cy - 3, cx + 3, cy + 3, soft);
			}
			default -> {
				for (int i = 0; i < 2; i++) {
					for (int j = 0; j < 2; j++) {
						round(graphics, cx - 6 + i * 7, cy - 6 + j * 7, 5, 5, 2, color);
					}
				}
			}
		}
	}

	/** Dims the world behind a screen and lays a faint grid over it. */
	public static void backdrop(GuiGraphicsExtractor graphics, int width, int height, boolean grid) {
		graphics.fillGradient(0, 0, width, height, 0xF70B0C10, 0xFA14161C);
		graphics.fillGradient(0, 0, width / 2, height, 0x101F2A4A, 0x00000000);
		if (grid) {
			for (int x = 0; x < width; x += 48) {
				graphics.fill(x, 0, x + 1, height, 0x08FFFFFF);
			}
			for (int y = 0; y < height; y += 48) {
				graphics.fill(0, y, width, y + 1, 0x08FFFFFF);
			}
		}
	}

	private static final Identifier SATURN =
			Identifier.fromNamespaceAndPath("valkawclient", "textures/gui/saturn.png");
	/** Disc-only copy of saturn.png (alpha zeroed outside the planet sphere), used
	 to re-occlude back moons without letting the ring bands cover them. */
	private static final Identifier SATURN_DISC =
			Identifier.fromNamespaceAndPath("valkawclient", "textures/gui/saturn-disc.png");
	private static final Identifier LOGO =
			Identifier.fromNamespaceAndPath("valkawclient", "textures/gui/logo.png");
	private static final int SATURN_W = 1180;
	private static final int SATURN_H = 660;
	private static final int SATURN_R = 168;
	private static final Identifier WORDMARK =
			Identifier.fromNamespaceAndPath("valkawclient", "textures/gui/wordmark.png");
	private static final int WORDMARK_W = 2048;
	private static final int WORDMARK_H = 413;

	/** Pre-rendered Saturn: one textured quad instead of thousands of fills per frame. */
	public static void planet(GuiGraphicsExtractor graphics, int cx, int cy, int radius, float time)
	{
		if (radius < 8)
		{
			return;
		}
		float scale = radius / (float) SATURN_R;
		int w = Math.round(SATURN_W * scale);
		int h = Math.round(SATURN_H * scale);
		graphics.blit(RenderPipelines.GUI_TEXTURED, SATURN, cx - w / 2, cy - h / 2, 0.0F, 0.0F,
				w, h, SATURN_W, SATURN_H, SATURN_W, SATURN_H);
	}

	/** Redraw only the planet's disc region of the Saturn texture (source rect
	 0,0,336,336 around the disc centre 590,341). Used to re-occlude back moons
	 after they are drawn over the far ring band. */
	public static void disc(GuiGraphicsExtractor graphics, int cx, int cy, int radius)
	{
		if (radius < 8)
		{
			return;
		}
		float scale = radius / (float) SATURN_R;
		int w = Math.round(336 * scale);
		int h = Math.round(336 * scale);
		graphics.blit(RenderPipelines.GUI_TEXTURED, SATURN, cx - w / 2, cy - h / 2, 422.0F, 173.0F,
				w, h, 336, 336, SATURN_W, SATURN_H);
	}

	/** Redraw only the planet's disc (clean alpha-masked texture) on top of
	 anything drawn since the full Saturn texture. Used to re-occlude back moons
	 while keeping the ring bands beneath them. */
	public static void planetDisc(GuiGraphicsExtractor graphics, int cx, int cy, int radius)
	{
		if (radius < 8)
		{
			return;
		}
		float scale = radius / (float) SATURN_R;
		int w = Math.round(SATURN_W * scale);
		int h = Math.round(SATURN_H * scale);
		graphics.blit(RenderPipelines.GUI_TEXTURED, SATURN_DISC, cx - w / 2, cy - h / 2, 0.0F, 0.0F,
				w, h, SATURN_W, SATURN_H, SATURN_W, SATURN_H);
	}

	/** Golden V with a Saturn ring, drawn centred at cx, cy. */
	public static void logo(GuiGraphicsExtractor graphics, int cx, int cy, int size)
	{
		if (size < 8)
		{
			return;
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, cx - size / 2, cy - size / 2, 0.0F, 0.0F,
				size, size, 1024, 1024, 1024, 1024);
	}

	/** Height the wordmark takes at the given width, so callers can place text under it. */
	public static int wordmarkHeight(int width)
	{
		return Math.max(1, Math.round(WORDMARK_H * width / (float) WORDMARK_W));
	}

	/**
	 * The VALKAW wordmark as a texture. Scaling bitmap text that far turns the
	 * glyph edges into stairs, so the brand is pre-rendered and blitted instead.
	 */
	public static void wordmark(GuiGraphicsExtractor graphics, int cx, int y, int width)
	{
		if (width < 24)
		{
			return;
		}
		int height = wordmarkHeight(width);
		graphics.blit(RenderPipelines.GUI_TEXTURED, WORDMARK, cx - width / 2, y, 0.0F, 0.0F,
				width, height, WORDMARK_W, WORDMARK_H, WORDMARK_W, WORDMARK_H);
	}

}
