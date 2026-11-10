package com.valkaw.client.gui;

import java.util.ArrayList;
import java.util.List;
import com.valkaw.client.module.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.lwjgl.glfw.GLFW;

/**
 * Main menu: a vertical button column on the left and the Valkaw wordmark
 * with the ringed planet filling the space beside it.
 */
public class ValkawMainMenu extends Screen
{
	private static final String VERSION = FabricLoader.getInstance().getModContainer("valkawclient")
			.map(container -> "v" + container.getMetadata().getVersion().getFriendlyString()).orElse("v1.0.1");
	private static final java.util.regex.Pattern NAME_CHAR = java.util.regex.Pattern.compile("[A-Za-z0-9_]");

	private final List<Nav> navs = new ArrayList<>(5);
	private static final int ACCOUNT_ROW = 18;

	private double clock;
	private float open;
	private boolean accountOpen;
	private boolean accountTyping;
	private String accountInput = "";
	private int accountX;
	private int accountY;
	private int accountW;
	private int accountH;
	private int panelX;
	private int panelY;
	private int panelW;

	// Quiet starfield for the backdrop. Deterministic, tiny, animated by clock.
	private static final int STAR_COUNT = 46;
	private static final float[] STAR_X = new float[STAR_COUNT];
	private static final float[] STAR_Y = new float[STAR_COUNT];
	private static final float[] STAR_TW = new float[STAR_COUNT];
	static
	{
		java.util.Random rand = new java.util.Random(11L);
		for (int i = 0; i < STAR_COUNT; i++)
		{
			STAR_X[i] = rand.nextFloat();
			STAR_Y[i] = rand.nextFloat();
			STAR_TW[i] = rand.nextFloat() * (float) Math.PI * 2.0F;
		}
	}

	public ValkawMainMenu()
	{
		super(Component.literal("Valkaw Client"));
	}

	@Override
	protected void init()
	{
		clearWidgets();
		navs.clear();
		navs.add(new Nav("arrow", "Play", Ui.ACCENT, true,
				() -> minecraft.setScreen(new SelectWorldScreen(this))));
		navs.add(new Nav("users", "Servers", Ui.COPPER, false,
				() -> minecraft.setScreen(new JoinMultiplayerScreen(this))));
		navs.add(new Nav("wrench", "Options", Ui.TEXT_2, false,
				() -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))));
		navs.add(new Nav("layers", "Client", Ui.ACCENT, false,
				() -> minecraft.setScreen(new ClientSettingsScreen(this))));
		navs.add(new Nav("power", "Quit", Ui.RED, false, () -> minecraft.stop()));
	}

	// ------------------------------------------------------------- geometry

	private int sideX()
	{
		return width < 560 ? 16 : 32;
	}

	private int sideW()
	{
		return width < 620 ? 54 : 172;
	}

	private int itemH()
	{
		return height < 330 ? 30 : 36;
	}

	private int gap()
	{
		return height < 330 ? 6 : 8;
	}

	private int sideTop()
	{
		int block = navs.size() * itemH() + (navs.size() - 1) * gap();
		return Math.max(66, (height - block) / 2);
	}

	private int panelY()
	{
		return sideTop() - 14;
	}

	private int panelH()
	{
		return navs.size() * itemH() + (navs.size() - 1) * gap() + 28;
	}

	private int rightX()
	{
		return sideX() + sideW() + 26;
	}

	private int rightW()
	{
		return Math.max(120, width - rightX() - (width < 560 ? 16 : 32));
	}

	// --------------------------------------------------------------- render

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
	{
		float dt = Ui.dt();
		open = Math.min(1.0F, open + dt * 5.0F);
		int lift = Math.round((1.0F - Ui.ease(open)) * 12.0F);

		drawBackdrop(graphics, dt);
		drawWordmark(graphics);
		drawSidebar(graphics, mouseX, mouseY + lift);
		drawAccount(graphics);
		drawFoot(graphics);

		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void drawBackdrop(GuiGraphics graphics, float dt)
	{
		clock += dt;
		graphics.fillGradient(0, 0, width, height, Ui.BG_TOP, Ui.BG_BOTTOM);		drawStars(graphics);

		int cx = rightX() + rightW() / 2;
		int cy = Math.round(height * 0.66F);
		// Match the web's proportions: the whole 1180-wide sheet (rings, orbit
		// paths and all) must fit the window width or the moons slip off-screen.
		// Fit the whole ring sheet inside the area to the right of the sidebar
		// menu at any window size (mirrors the launcher's responsive scene).
		int radius = Math.max(96, Math.min(
				Math.min(Math.round(width * 0.135F), Math.round(height * 0.16F)),
				Math.round(rightW() * 0.22F)));
		// Saturn itself is drawn slightly smaller than the orbit field, so the
		// moons keep their current absolute size while the planet reads smaller.
		int planetRadius = Math.max(72, Math.round(radius * 0.85F));
		// Moons orbit at the ring field radius (which tracks the planet), but
		// their disc size keeps the full field radius so shrinking Saturn does
		// not also shrink the moons. This mirrors the web/launcher ratio.
		// Layer order mirrors the web/launcher scene (far to near):
		// 0. ring particles — orbit before the planet texture, so the disc
		//    occludes the ones passing behind it (the rings stay beneath);
		// 1. the full Saturn texture (disc + ring bands) — the base scene;
		// 2. back moons — over the ring bands (they sit above the rings);
		// 3. the clean planet disc redrawn over the back moons, so Saturn
		//    occludes them while the rings stay beneath them;
		// 4. front moons — over everything.
		float ringScale = planetRadius / SATURN_TEXTURE_R;
		drawRingParticles(graphics, cx, cy, ringScale);
		Ui.planet(graphics, cx, cy, planetRadius, (float) clock);
		drawMoons(graphics, cx, cy, planetRadius, radius, false);
		Ui.planetDisc(graphics, cx, cy, planetRadius);
		drawMoons(graphics, cx, cy, planetRadius, radius, true);

		graphics.fillGradient(0, 0, width, 72, 0x66000000, 0x00000000);
		graphics.fillGradient(0, height - 96, width, height, 0x00000000, 0x77000000);
	}

	/** Draw the spinning ring particles before the planet texture, so the disc
	 occludes the ones passing behind it (the rings themselves stay beneath).
	 Layout mirrors the web: x = cx + r·scale·cos(a), y = cy + r·scale·0.28·sin(a)
	 with ω ∝ r^-1.5 (Kepler) and clock as elapsed seconds. */
	private void drawRingParticles(GuiGraphics graphics, int cx, int cy, float scale)
	{
		for (float[] p : RING_PARTICLES)
		{
			double a = p[2] + p[1] * clock;
			float x = cx + p[0] * scale * (float) Math.cos(a);
			float y = cy + p[0] * scale * RING_K * (float) Math.sin(a);
			float size = Math.max(0.9F, 0.9F + p[3] * 0.7F);
			int rgb = p[3] > 0.75F ? 0x8E867C : 0xD8CDBB;
			int alpha = (int) ((p[3] > 0.75F ? 0.4F : 0.5F) * 255F);
			Ui.discAA(graphics, x, y, size, (alpha << 24) | rgb);
		}
	}

	/** Orbit data for six real Saturnian moons (Janus, Mimas, Enceladus,
	 Tethys, Dione, Rhea): radius (×planet), period, phase, colour. Shared
	 with the launcher and website so all three render the same system. */
	private static final float[][] MOON_ORBITS = {
			{ 2.79F, 30F, 0.6F, 0x009A9AA0 }, // Janus
			{ 3.05F, 34F, 1.0F, 0x00E3B24B }, // Mimas
			{ 3.20F, 37F, 5.6F, 0x009CC7F0 }, // Enceladus
			{ 3.35F, 39F, 1.9F, 0x00E8DFCB }, // Tethys
			{ 3.58F, 44F, 4.0F, 0x00E3B24B }, // Dione
			{ 3.99F, 51F, 0.2F, 0x009CC7F0 }, // Rhea
	};

	/** Moon disc radius as a fraction of the planet radius, mirroring the
	 web/launcher sizes (small < cream < gold < ice). */
	private static final float[] MOON_SIZE_FRAC = {
			0.045F, 0.072F, 0.084F, 0.056F, 0.072F, 0.084F,
	};

	/** Highlight colour (RGB) for each moon, making it read as a 3D sphere. */
	private static final int[] MOON_HIGHLIGHT = {
			0xE9E2D2, 0xF6E7BA, 0xE8F4FF, 0xF6F0E3, 0xF6E7BA, 0xE8F4FF,
	};

	/** Texture-space planet radius (matches Ui.SATURN_R: 168px in the
	 1180×660 sheet); used only to scale ring particles to the screen. */
	private static final float SATURN_TEXTURE_R = 168F;

	/** Ring particles: hundreds of ice/rock grains orbit at Kepler speeds so the
	 ring reads as a spinning disc — the same model as the website's
	 RingParticles and the launcher. r is in texture px, omega ∝ r^-1.5,
	 phase random, and the Cassini division (213–233) is left empty. The seed is
	 fixed so every launch shows the same field. */
	private static final float[][] RING_PARTICLES = buildRingParticles();

	private static float[][] buildRingParticles()
	{
		java.util.Random rand = new java.util.Random(20261109L);
		float[][] out = new float[340][];
		int n = 0;
		int guard = 0;
		while (n < out.length && guard++ < 20000)
		{
			float r = 150F + rand.nextFloat() * (292F - 150F);
			if (r > 213F && r < 233F)
			{
				continue; // Cassini division stays dark
			}
			double period = 8.0 * Math.pow(r / 184.0, 1.5); // ≈8s at C ring outer edge
			float omega = (float) (2.0 * Math.PI / period);
			out[n++] = new float[] { r, omega, rand.nextFloat() * (float) (Math.PI * 2.0), rand.nextFloat() };
		}
		return n == out.length ? out : java.util.Arrays.copyOf(out, n);
	}

	/** The scene tilt shared with the baked texture, in radians. */
	private static final float TILT = (float) Math.toRadians(-14.0);
	private static final float TILT_COS = (float) Math.cos(TILT);
	private static final float TILT_SIN = (float) Math.sin(TILT);
	private static final float RING_K = 0.28F;

	/** Six moons travel the tilted orbit plane; front halves pass in front of the planet.
	 orbitRadius scales the orbit (tracking the planet); sizeRadius scales the disc.
	 Keeping them separate lets Saturn shrink without shrinking the moons. */
	private void drawMoons(GuiGraphics graphics, int cx, int cy, int orbitRadius, int sizeRadius, boolean frontHalf)
	{
		for (int i = 0; i < MOON_ORBITS.length; i++)
		{
			float[] orbit = MOON_ORBITS[i];
			double angle = orbit[2] + clock * (2.0 * Math.PI / orbit[1]);
			double sin = Math.sin(angle);
			boolean front = sin > 0.0D;
			if (front != frontHalf)
			{
				continue;
			}
			double lx = orbit[0] * orbitRadius * Math.cos(angle);
			double ly = orbit[0] * RING_K * orbitRadius * sin;
			// Keep exact sub-pixel position; the anti-aliased disc paints edge
			// pixels with proportional alpha, so small per-frame steps glide
			// smoothly without intensity flicker or pixel snapping.
			float fx = (float) (cx + lx * TILT_COS - ly * TILT_SIN);
			float fy = (float) (cy + 0.22F * orbitRadius + lx * TILT_SIN + ly * TILT_COS);
			float mr = Math.max(2.0F, sizeRadius * MOON_SIZE_FRAC[i]);
			// Back half: real occultation — the disc stays fully opaque with its
			// normal colours; it is simply hidden where the planet/rings overlap
			// it (drawn before the planet). No artificial dimming or fading.
			int alpha = front ? 225 : 255;
			int hlAlpha = front ? 170 : 255;
			int base = (((int) orbit[3] & 0xFFFFFF) | (alpha << 24));
			int highlight = ((MOON_HIGHLIGHT[i] & 0xFFFFFF) | (hlAlpha << 24));
			Ui.sphereAA(graphics, fx, fy, mr, base, highlight);
		}
	}

	/** A slow, faint starfield. Tiny fills, deterministic layout, no allocations. */
	private void drawStars(GuiGraphics graphics)
	{
		for (int i = 0; i < STAR_COUNT; i++)
		{
			float drift = (float) (clock * (0.012F + STAR_TW[i] * 0.004F)) % 1.0F;
			int sx = (int) (STAR_X[i] * width);
			int sy = (int) ((STAR_Y[i] + drift) % 1.0F * height);
			if (sy >= height)
			{
				continue;
			}
			float tw = (float) Math.sin(clock * 1.4F + STAR_TW[i]);
			int alpha = 90 + (int) (tw * 70);
			int size = STAR_TW[i] > 4.4F ? 2 : 1;
			graphics.fill(sx, sy, sx + size, sy + size, (Math.max(30, Math.min(190, alpha)) << 24) | 0xFFFFFF);
		}
	}

	private static String rendererName;
	private static String subtitle;
	private static String lastUser;
	private static String lastInitial = "?";

	private void drawWordmark(GuiGraphics graphics)
	{
		int cx = rightX() + rightW() / 2;
		int markW = Math.round(rightW() * 0.78F);
		int markH = Ui.wordmarkHeight(markW);
		int markY = Math.max(24, Math.round(height * 0.14F));

		int logo = Math.min(84, Math.round(markH * 1.15F));
		int gap = Math.max(10, logo / 3);
		if (logo >= 24 && cx - markW / 2 - gap - logo > rightX())
		{
			Ui.logo(graphics, cx - markW / 2 - gap - logo / 2, markY + markH / 2, logo);
		}

		Ui.wordmark(graphics, cx, markY, markW);

		if (subtitle == null)
		{
			subtitle = "CLIENT  ·  " + VERSION + "  ·  " + renderer();
		}
		Ui.textCenter(graphics, font, subtitle, cx, markY + markH + 10, Ui.MUTED);
	}

	private void drawSidebar(GuiGraphics graphics, int mouseX, int mouseY)
	{
		int x = sideX();
		int y = panelY();
		int w = sideW();
		int h = panelH();
		boolean narrow = w < 100;

		Ui.round(graphics, x, y, w, h, Ui.R_PANEL, 0x99000000);
		Ui.roundOutline(graphics, x, y, w, h, Ui.R_PANEL, Ui.LINE_SOFT);

		for (int i = 0; i < navs.size(); i++)
		{
			Nav nav = navs.get(i);
			int iy = sideTop() + i * (itemH() + gap());
			boolean hot = mouseX >= x && mouseX < x + w && mouseY >= iy && mouseY < iy + itemH();
			int fill;
			if (nav.primary)
			{
				fill = hot ? Ui.lighten(Ui.ACCENT, 0.12F) : Ui.ACCENT;
			}
			else
			{
				fill = hot ? 0x1FFFFFFF : 0x0FFFFFFF;
			}

			Ui.round(graphics, x + 8, iy, w - 16, itemH(), Ui.R_CARD, fill);
			if (hot && !nav.primary)
			{
				Ui.roundOutline(graphics, x + 8, iy, w - 16, itemH(), Ui.R_CARD, 0x33FFFFFF);
			}

			int iconColor = nav.primary ? Ui.INK : (hot ? Ui.TEXT : nav.accent);
			int iconX = narrow ? x + w / 2 : x + 26;
			Ui.icon(graphics, nav.icon, iconX, iy + itemH() / 2, iconColor);

			if (!narrow)
			{
				graphics.drawString(font, nav.label, x + 44, iy + itemH() / 2 - 4,
						nav.primary ? Ui.INK : (hot ? Ui.TEXT : Ui.TEXT_2), false);
			}
		}
	}

	private void drawAccount(GuiGraphics graphics)
	{
		String name = minecraft.getUser().getName();
		String initial = initial(name);
		boolean online = minecraft.getConnection() != null;
		String status = online ? "Online" : "Offline";
		int statusColor = online ? Ui.GREEN : Ui.MUTED;
		int x = sideX();
		int y = 14;
		boolean full = minecraft.getWindow().isFullscreen() || (width >= 560 && height >= 400);

		if (!full)
		{
			// Small window: keep it to one avatar tile so nothing crowds the sidebar.
			accountX = x;
			accountY = y;
			accountW = accountH = 26;
			Ui.round(graphics, x, y, 26, 26, 8, accountOpen ? 0x2AFFFFFF : 0x14FFFFFF);
			Ui.round(graphics, x + 5, y + 5, 16, 16, 6, Ui.alpha(Ui.COPPER, 0.5F));
			Ui.textCenter(graphics, font, initial, x + 13, y + 9, Ui.INK);
			Ui.circle(graphics, x + 21, y + 21, 2, statusColor);
			drawAccountPanel(graphics);
			return;
		}

		int w = Math.max(sideW(), 168);
		accountX = x;
		accountY = y;
		accountW = w;
		accountH = 30;
		Ui.round(graphics, x, y, w, 30, Ui.R_CARD, accountOpen ? 0x2AFFFFFF : 0x12FFFFFF);
		Ui.round(graphics, x + 5, y + 5, 20, 20, 7, Ui.alpha(Ui.COPPER, 0.42F));
		Ui.textCenter(graphics, font, initial, x + 15, y + 11, Ui.INK);
		graphics.drawString(font, Ui.fit(font, name, w - 44), x + 30, y + 5, Ui.TEXT, false);
		Ui.circle(graphics, x + 33, y + 21, 2, statusColor);
		graphics.drawString(font, status, x + 39, y + 17, statusColor, false);
		Ui.chevron(graphics, x + w - 12, y + 15, Ui.MUTED, !accountOpen);
		drawAccountPanel(graphics);
	}

	/** Saved names plus a field for a new one, anchored under the pill. */
	private void drawAccountPanel(GuiGraphics graphics)
	{
		if (!accountOpen)
		{
			return;
		}
		List<String> names = ModuleManager.get().accounts();
		panelX = accountX;
		panelY = accountY + accountH + 4;
		panelW = Math.max(accountW, 176);
		int panelH = (names.size() + 1) * ACCOUNT_ROW + 10;
		if (panelY + panelH > height - 8)
		{
			panelY = Math.max(8, height - 8 - panelH);
		}
		Ui.round(graphics, panelX, panelY, panelW, panelH, Ui.R_CARD, 0xE60C0F16);
		Ui.roundOutline(graphics, panelX, panelY, panelW, panelH, Ui.R_CARD, Ui.LINE_SOFT);

		String active = minecraft.getUser().getName();
		int rowY = panelY + 5;
		for (String entry : names)
		{
			boolean current = entry.equalsIgnoreCase(active);
			if (current)
			{
				Ui.round(graphics, panelX + 4, rowY - 1, panelW - 8, ACCOUNT_ROW, 5, 0x1FFFFFFF);
			}
			Ui.circle(graphics, panelX + 11, rowY + 6, 2, current ? Ui.ACCENT : Ui.MUTED);
			graphics.drawString(font, Ui.fit(font, entry, panelW - 42), panelX + 18, rowY + 2,
					current ? Ui.TEXT : Ui.TEXT_2, false);
			rowY += ACCOUNT_ROW;
		}

		graphics.drawString(font, "+", panelX + 10, rowY + 2, Ui.MUTED, false);
		if (accountTyping)
		{
			String caret = System.currentTimeMillis() / 500 % 2 == 0 ? "_" : "";
			graphics.drawString(font, Ui.fit(font, accountInput + caret, panelW - 42), panelX + 18, rowY + 2,
					Ui.TEXT, false);
		}
		else
		{
			graphics.drawString(font, "New name...", panelX + 18, rowY + 2, Ui.MUTED, false);
		}
	}

	/** Row index under the cursor: 0..n-1 saved names, n the new-name field, -1 outside. */
	private int accountRowAt(double mx, double my)
	{
		if (!accountOpen || mx < panelX || mx >= panelX + panelW)
		{
			return -1;
		}
		int top = panelY + 5;
		int rows = ModuleManager.get().accounts().size() + 1;
		if (my < top || my >= top + rows * ACCOUNT_ROW)
		{
			return -1;
		}
		return (int) ((my - top) / ACCOUNT_ROW);
	}

	private void drawFoot(GuiGraphics graphics)
	{
		String left = width < 620 ? "Valkaw Client " + VERSION
				: "Valkaw Client " + VERSION + "   ·   Minecraft 1.21.11   ·   Fabric";
		graphics.drawString(font, left, rightX(), height - 22, Ui.MUTED, false);
		if (width >= 760)
		{
			String hint = "Right Shift  ·  Client Settings";
			graphics.drawString(font, hint, width - sideX() - Ui.width(font, hint), height - 22, Ui.MUTED, false);
		}
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
	{
		List<String> names = ModuleManager.get().accounts();
		int row = accountRowAt(event.x(), event.y());
		if (row >= 0)
		{
			if (row < names.size())
			{
				ModuleManager.get().setUsername(names.get(row));
				accountOpen = false;
			}
			accountTyping = row >= names.size();
			accountInput = "";
			return true;
		}
		if (event.x() >= accountX && event.x() < accountX + accountW
				&& event.y() >= accountY && event.y() < accountY + accountH)
		{
			accountOpen = !accountOpen;
			accountTyping = false;
			accountInput = "";
			return true;
		}
		if (accountOpen)
		{
			accountOpen = false;
			accountTyping = false;
			accountInput = "";
		}
		int nav = navAt(event.x(), event.y());
		if (nav >= 0)
		{
			AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
			navs.get(nav).action.run();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean charTyped(CharacterEvent event)
	{
		if (!accountTyping)
		{
			return super.charTyped(event);
		}
		String typed = event.codepointAsString();
		if (!typed.isEmpty() && accountInput.length() < 16 && NAME_CHAR.matcher(typed).matches())
		{
			accountInput += typed;
		}
		return true;
	}

	@Override
	public boolean shouldCloseOnEsc()
	{
		// Esc must never close the main menu: closing it hands the player to the
		// vanilla title screen, which is not what this client shows.
		return false;
	}

	@Override
	public void onClose()
	{
		// Swallow the close request for the same reason.
	}

	@Override
	public boolean keyPressed(KeyEvent event)
	{
		int key = event.key();
		if (accountTyping)
		{
			if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)
			{
				if (!accountInput.isBlank())
				{
					ModuleManager.get().setUsername(accountInput);
					accountOpen = false;
				}
				accountTyping = false;
				accountInput = "";
			}
			else if (key == GLFW.GLFW_KEY_ESCAPE)
			{
				accountTyping = false;
				accountInput = "";
			}
			else if (key == GLFW.GLFW_KEY_BACKSPACE && !accountInput.isEmpty())
			{
				accountInput = accountInput.substring(0, accountInput.length() - 1);
			}
			return true;
		}
		if (accountOpen)
		{
			if (key == GLFW.GLFW_KEY_ESCAPE)
			{
				accountOpen = false;
			}
			return true;
		}
		if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_5)
		{
			int index = key - GLFW.GLFW_KEY_1;
			if (index < navs.size())
			{
				AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
				navs.get(index).action.run();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	private int navAt(double mouseX, double mouseY)
	{
		int x = sideX();
		int w = sideW();
		if (mouseX < x || mouseX >= x + w)
		{
			return -1;
		}
		for (int i = 0; i < navs.size(); i++)
		{
			int iy = sideTop() + i * (itemH() + gap());
			if (mouseY >= iy && mouseY < iy + itemH())
			{
				return i;
			}
		}
		return -1;
	}

	// -------------------------------------------------------------- helpers

	private static String initial(String name)
	{
		if (!name.equals(lastUser))
		{
			lastUser = name;
			lastInitial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
		}
		return lastInitial;
	}

	private static String renderer()
	{
		if (rendererName != null)
		{
			return rendererName;
		}
		Minecraft client = Minecraft.getInstance();
		// The resource manager can still be null on the very first frames; only
		// cache once it exists so "Vanilla" is never frozen in too early.
		if (client.getResourceManager() == null)
		{
			return "Vanilla";
		}
		if (FabricLoader.getInstance().isModLoaded("vulkanmod"))
		{
			rendererName = "VulkanMod";
		}
		else if (FabricLoader.getInstance().isModLoaded("sodium"))
		{
			rendererName = "Sodium";
		}
		else
		{
			rendererName = "Vanilla";
		}
		return rendererName;
	}

	private record Nav(String icon, String label, int accent, boolean primary, Runnable action)
	{
	}
}
