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

	private final List<Nav> navs = new ArrayList<>(5);
	private static final int ACCOUNT_ROW = 18;

	private float clock;
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
		graphics.fillGradient(0, 0, width, height, Ui.BG_TOP, Ui.BG_BOTTOM);

		int cx = rightX() + rightW() / 2;
		int cy = Math.round(height * 0.68F);
		int radius = Math.max(28, Math.min(Math.round(rightW() * 0.22F), Math.round(height * 0.19F)));
		Ui.planet(graphics, cx, cy, radius, clock);

		graphics.fillGradient(0, 0, width, 72, 0x66000000, 0x00000000);
		graphics.fillGradient(0, height - 96, width, height, 0x00000000, 0x77000000);
	}

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

		Ui.textCenter(graphics, font, "CLIENT  ·  " + VERSION + "  ·  " + renderer(),
				cx, markY + markH + 10, Ui.MUTED);
	}

	private void drawSidebar(GuiGraphics graphics, int mouseX, int mouseY)
	{
		int x = sideX();
		int y = panelY();
		int w = sideW();
		int h = panelH();
		boolean narrow = w < 100;

		Ui.round(graphics, x, y, w, h, Ui.R_PANEL, 0x99000000);
		graphics.renderOutline(x, y, w, h, Ui.LINE_SOFT);

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
				graphics.renderOutline(x + 8, iy, w - 16, itemH(), 0x33FFFFFF);
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
			Ui.textCenter(graphics, font, name.substring(0, 1).toUpperCase(), x + 13, y + 9, Ui.INK);
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
		Ui.textCenter(graphics, font, name.substring(0, 1).toUpperCase(), x + 15, y + 11, Ui.INK);
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
		graphics.renderOutline(panelX, panelY, panelW, panelH, Ui.LINE_SOFT);

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
			String hint = "Right Shift  ·  client settings";
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
		if (!typed.isEmpty() && accountInput.length() < 16 && typed.matches("[A-Za-z0-9_]"))
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

	private static String renderer()
	{
		Minecraft client = Minecraft.getInstance();
		if (FabricLoader.getInstance().isModLoaded("vulkanmod"))
		{
			return "VulkanMod";
		}
		if (client.getResourceManager() != null && FabricLoader.getInstance().isModLoaded("sodium"))
		{
			return "Sodium";
		}
		return "Vanilla";
	}

	private record Nav(String icon, String label, int accent, boolean primary, Runnable action)
	{
	}
}
