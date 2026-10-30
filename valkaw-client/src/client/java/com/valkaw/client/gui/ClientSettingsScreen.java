package com.valkaw.client.gui;

import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;
import com.valkaw.client.module.ModuleManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Full-screen module manager. Sidebar with categories on the left, module
 * cards on the right, no window frame so the whole screen is usable.
 */
public class ClientSettingsScreen extends Screen
{
	private static final Category[] TABS = Category.values();
	private static final int MARGIN = 16;
	private static final String VERSION = FabricLoader.getInstance().getModContainer("valkawclient")
			.map(container -> "v" + container.getMetadata().getVersion().getFriendlyString()).orElse("v1.0.1");

	private final Screen parent;
	private final Map<String, Ui.Anim> toggleAnims = new HashMap<>();
	private Category tab;
	private String query = "";
	private boolean searchFocused;
	private boolean enabledOnly;
	private boolean bindingEditor;
	private float scroll;
	private float scrollTarget;
	private List<Module> cached;
	private String cacheKey = "";

	/** Grid metrics, shared by the renderer and the click handler. */
	private static final int CARD_H = 62;
	private static final int CARD_GAP = 8;

	// Layout, refreshed once per frame and reused by the click handler.
	private int sX;
	private int sY;
	private int sW;
	private int sH;
	private int mX;
	private int mW;
	private int gTop;
	private int gH;
	private int searchX;
	private int searchY;
	private int searchW;
	private int chipY;
	private int allX;
	private int onX;
	private int closeX;
	private int closeY;
	private int btnHudX;
	private int btnHudW;
	private int btnResetX;
	private int btnResetW;
	private int btnKeyX;
	private int btnKeyW;
	private int btnBackX;
	private int btnBackW;

	public ClientSettingsScreen(Screen parent)
	{
		super(Component.literal("Client settings"));
		this.parent = parent;
	}

	@Override
	protected void init()
	{
		layout();
	}

	private float clock;
	private float lastDt;

	private void layout()
	{
		sX = MARGIN;
		sY = MARGIN;
		sW = width < 680 ? 52 : 172;
		sH = height - MARGIN * 2;
		mX = sX + sW + 14;
		mW = width - mX - MARGIN;
		gTop = 88;
		gH = height - gTop - 50;

		searchW = Math.min(186, Math.max(84, mW / 3));
		searchY = 24;
		searchX = mX + mW - searchW - 30;
		chipY = 56;
		onX = mX + mW - Ui.width(font, "On") - 20;
		allX = onX - Ui.width(font, "All") - 24;
		closeX = mX + mW - 12;
		closeY = 24;

		int footer = height - 32;
		btnBackW = Ui.width(font, "Back") + 20;
		btnBackX = mX + mW - btnBackW;
		btnKeyW = Ui.width(font, "Editor key") + 20;
		btnKeyX = btnBackX - 6 - btnKeyW;
		btnResetW = Ui.width(font, "Reset positions") + 20;
		btnResetX = btnKeyX - 6 - btnResetW;
		btnHudW = Ui.width(font, "HUD editor") + 20;
		btnHudX = btnResetX - 6 - btnHudW;
		if (btnHudX < mX + 190)
		{
			btnResetX = -1000;
			btnKeyX = -1000;
			btnHudX = btnBackX - 6 - btnHudW;
		}
		if (footer < 0)
		{
			btnHudX = btnBackX = btnResetX = btnKeyX = -1000;
		}
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
	{
		clock += lastDt = Ui.dt();
		layout();
		graphics.fillGradient(0, 0, width, height, Ui.BG_TOP, Ui.BG_BOTTOM);
		Ui.planet(graphics, Math.round(width * 0.74F), Math.round(height * 0.60F), Math.max(40, Math.min(Math.round(width * 0.26F), Math.round(height * 0.22F))), clock);
		graphics.fillGradient(0, 0, width, 96, 0x99000000, 0x00000000);
		graphics.fillGradient(0, height - 110, width, height, 0x00000000, 0x88000000);
		graphics.fillGradient(0, 0, 280, height, 0x55000000, 0x00000000);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
	{
		float dt = lastDt;
		layout();
		drawSidebar(graphics, mouseX, mouseY);
		drawHeader(graphics, mouseX, mouseY);
		drawGrid(graphics, mouseX, mouseY, dt);
		drawFooter(graphics, mouseX, mouseY);
		if (bindingEditor)
		{
			drawBindingBanner(graphics);
		}
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	// ------------------------------------------------------------- sidebar

	private int rowY(int index)
	{
		return sY + 50 + index * 32;
	}

	private void drawSidebar(GuiGraphics graphics, int mouseX, int mouseY)
	{
		Ui.round(graphics, sX, sY, sW, sH, 16, Ui.LINE_SOFT);
		Ui.round(graphics, sX + 1, sY + 1, sW - 2, sH - 2, 15, 0xFF15181F);

		boolean narrow = sW < 120;
		if (narrow)
		{
			Ui.logo(graphics, sX + sW / 2, sY + 26, 28);
		}
		else
		{
			Ui.logo(graphics, sX + 23, sY + 23, 26);
			Ui.text(graphics, font, "Client", sX + 42, sY + 12, Ui.TEXT);
			Ui.text(graphics, font, "Settings", sX + 42, sY + 23, Ui.MUTED);
		}

		for (int index = 0; index <= TABS.length; index++)
		{
			Category category = index == 0 ? null : TABS[index - 1];
			int y = rowY(index);
			boolean active = tab == category;
			boolean hot = over(mouseX, mouseY, sX + 6, y, sW - 12, 28);
			int accent = category == null ? Ui.TEXT_2 : accent(category);
			int count = category == null ? ModuleManager.get().all().size() : ModuleManager.get().byCategory(category).size();

			if (active)
			{
				Ui.round(graphics, sX + 6, y, sW - 12, 28, 8, Ui.alpha(accent, 0.14F));
				Ui.round(graphics, sX + 6, y + 6, 3, 16, 2, accent);
			}
			else if (hot)
			{
				Ui.round(graphics, sX + 6, y, sW - 12, 28, 8, 0x0FFFFFFF);
			}

			String label = category == null ? "All modules" : title(category);
			int tileX = narrow ? sX + (sW - 16) / 2 : sX + 14;
			Ui.round(graphics, tileX, y + 6, 16, 16, 6, Ui.alpha(accent, active ? 0.26F : 0.14F));
			Ui.icon(graphics, category == null ? "grid" : catIcon(category), tileX + 8, y + 14, accent);

			if (!narrow)
			{
				Ui.text(graphics, font, label, sX + 38, y + 10, active ? Ui.TEXT : Ui.TEXT_2);
				Ui.textRight(graphics, font, Integer.toString(count), sX + sW - 14, y + 10, active ? accent : Ui.MUTED);
			}
		}

		if (!narrow)
		{
			Ui.divider(graphics, sX + 12, sY + sH - 30, sW - 24);
			Ui.text(graphics, font, "Fabric " + VERSION, sX + 14, sY + sH - 22, Ui.MUTED);
		}
	}

	// -------------------------------------------------------------- header

	private void drawHeader(GuiGraphics graphics, int mouseX, int mouseY)
	{
		graphics.pose().pushMatrix();
		graphics.pose().translate(mX, 22);
		graphics.pose().scale(1.2F, 1.2F);
		graphics.drawString(font, "Modules", 0, 0, Ui.TEXT, false);
		graphics.pose().popMatrix();
		Ui.text(graphics, font, enabled() + " of " + ModuleManager.get().all().size() + " enabled", mX, 40, Ui.MUTED);

		boolean overAll = over(mouseX, mouseY, allX - 8, chipY - 3, Ui.width(font, "All") + 22, 18);
		boolean overOn = over(mouseX, mouseY, onX - 8, chipY - 3, Ui.width(font, "On") + 22, 18);
		chip(graphics, "All", allX, chipY, tab == null, overAll);
		chip(graphics, "On", onX, chipY, enabledOnly, overOn);

		boolean hot = over(mouseX, mouseY, searchX, searchY, searchW, 20);
		Ui.round(graphics, searchX, searchY, searchW, 20, 7, hot || searchFocused ? 0x1AFFFFFF : 0x0FFFFFFF);
		Ui.circle(graphics, searchX + 11, searchY + 10, 3, Ui.MUTED);
		graphics.fill(searchX + 13, searchY + 11, searchX + 20, searchY + 12, Ui.MUTED);
		String shown = query.isEmpty() ? (searchFocused ? "" : "Search modules") : Ui.fit(font, query, searchW - 42);
		Ui.text(graphics, font, shown, searchX + 22, searchY + 6, query.isEmpty() ? Ui.MUTED : Ui.TEXT);
		if (searchFocused)
		{
			int caret = searchX + 22 + Ui.width(font, shown) + 1;
			if ((System.currentTimeMillis() / 500) % 2 == 0)
			{
				graphics.fill(caret, searchY + 5, caret + 1, searchY + 15, Ui.TEXT);
			}
		}
		if (!query.isEmpty())
		{
			Ui.closeIcon(graphics, searchX + searchW - 13, searchY + 7, Ui.MUTED);
		}

		Ui.closeIcon(graphics, closeX, closeY, over(mouseX, mouseY, closeX - 4, closeY - 4, 15, 15) ? Ui.TEXT : Ui.MUTED);
		Ui.divider(graphics, mX, 78, mW);
	}

	private void chip(GuiGraphics graphics, String label, int x, int y, boolean active, boolean hot)
	{
		int w = Ui.width(font, label) + 14;
		Ui.round(graphics, x - 7, y - 4, w + 6, 18, 6, active ? Ui.alpha(Ui.ACCENT, 0.20F) : hot ? 0x1AFFFFFF : 0x0DFFFFFF);
		Ui.text(graphics, font, label, x, y + 1, active ? Ui.ACCENT : Ui.TEXT_2);
	}

	// ---------------------------------------------------------------- grid

	private void drawGrid(GuiGraphics graphics, int mouseX, int mouseY, float dt)
	{
		List<Module> list = filtered();
		int columns = mW >= 700 ? 3 : mW >= 430 ? 2 : 1;
		int gap = CARD_GAP;
		int cardW = (mW - gap * (columns - 1)) / columns;
		int cardH = CARD_H;
		int rowH = cardH + gap;
		int rows = (list.size() + columns - 1) / columns;
		float maxScroll = Math.max(0, rows * rowH - gH);
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		// A wheel notch moves 32 px; easing the follow turns those jumps into one
		// glide, so the grid no longer reads as a stepped 50 Hz scroll.
		scroll += (scrollTarget - scroll) * Math.min(1.0F, dt * 26.0F);
		if (Math.abs(scrollTarget - scroll) < 0.4F) {
			scroll = scrollTarget;
		}
		scroll = Math.max(0, Math.min(maxScroll, scroll));

		if (list.isEmpty())
		{
			Ui.text(graphics, font, query.isEmpty() ? "No modules here." : "Nothing matches \"" + query + "\".", mX, gTop + 8, Ui.MUTED);
			return;
		}

		graphics.enableScissor(mX - 2, gTop, mX + mW + 2, gTop + gH);
		for (int index = 0; index < list.size(); index++)
		{
			int x = mX + (index % columns) * (cardW + gap);
			int y = gTop + Math.round((index / columns) * rowH - scroll);
			if (y + cardH < gTop || y > gTop + gH)
			{
				continue;
			}
			boolean hot = over(mouseX, mouseY, x, y, cardW, cardH) && mouseY > gTop && mouseY < gTop + gH;
			drawCard(graphics, list.get(index), x, y, cardW, cardH, hot, dt);
		}
		graphics.disableScissor();

		if (maxScroll > 0)
		{
			Ui.scrollbar(graphics, mX + mW + 8, gTop, gH, scroll / maxScroll, Math.min(1.0F, (float) gH / (rows * rowH)), Ui.ACCENT);
		}
	}

	private void drawCard(GuiGraphics graphics, Module module, int x, int y, int cardW, int cardH, boolean hot, float dt)
	{
		Ui.Anim anim = toggleAnims.computeIfAbsent(module.id(), id -> new Ui.Anim(module.isEnabled() ? 1 : 0));
		anim.to(module.isEnabled() ? 1.0F : 0.0F).speed(18.0F);
		float on = anim.update(dt);
		int accent = accent(module.category());

		Ui.round(graphics, x, y, cardW, cardH, 10, hot ? 0x1AFFFFFF : Ui.LINE_SOFT);
		Ui.round(graphics, x + 1, y + 1, cardW - 2, cardH - 2, 9, hot ? 0xFF22262F : 0xFF191D24);

		Ui.round(graphics, x + 11, y + 11, 28, 28, 9, Ui.alpha(accent, 0.16F + on * 0.10F));
		Ui.icon(graphics, iconFor(module), x + 25, y + 25, accent);

		Ui.text(graphics, font, Ui.fit(font, module.name(), cardW - 96), x + 47, y + 12, Ui.TEXT);
		Ui.text(graphics, font, Ui.fit(font, module.id(), cardW - 96), x + 47, y + 23, Ui.MUTED);
		if (module.keyOnly())
		{
			Ui.text(graphics, font, "Hold " + keyLabel(module.key()), x + 47, y + 37, accent);
			Ui.round(graphics, x + cardW - 42, y + 12, 32, 16, 6, Ui.alpha(accent, 0.18F));
			Ui.textCenter(graphics, font, Ui.fit(font, keyLabel(module.key()), 26), x + cardW - 26, y + 16, accent);
		}
		else if (module.isAction())
		{
			// Actions have no on/off switch; the state line carries the value the
			// module currently holds (particle mode, readiness, ...).
			Ui.text(graphics, font, Ui.fit(font, module.status(), cardW - 96), x + 47, y + 37, accent);
		}
		else
		{
			Ui.text(graphics, font, module.isEnabled() ? "Enabled" : "Disabled", x + 47, y + 37,
					module.isEnabled() ? accent : Ui.MUTED);
			Ui.toggle(graphics, x + cardW - 34, y + 14, on, accent);
		}

		if (hot)
		{
			Ui.textRight(graphics, font, "Options", x + cardW - 20, y + cardH - 14, Ui.MUTED);
			Ui.chevron(graphics, x + cardW - 14, y + cardH - 20, Ui.MUTED, true);
		}
	}

	// -------------------------------------------------------------- footer

	private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY)
	{
		Ui.divider(graphics, mX, height - 42, mW);
		Ui.text(graphics, font, enabled() + " of " + ModuleManager.get().all().size() + " modules enabled", mX, height - 31, Ui.MUTED);

		Ui.button(graphics, font, btnHudX, height - 34, "HUD editor", Ui.ACCENT, over(mouseX, mouseY, btnHudX, height - 34, btnHudW, 22), true);
		if (btnResetX > 0)
		{
			Ui.button(graphics, font, btnResetX, height - 34, "Reset positions", Ui.ACCENT,
					over(mouseX, mouseY, btnResetX, height - 34, btnResetW, 22), false);
			Ui.button(graphics, font, btnKeyX, height - 34, "Editor key", Ui.ACCENT,
					over(mouseX, mouseY, btnKeyX, height - 34, btnKeyW, 22), false);
		}
		Ui.button(graphics, font, btnBackX, height - 34, "Back", Ui.ACCENT,
				over(mouseX, mouseY, btnBackX, height - 34, btnBackW, 22), false);
	}

	private void drawBindingBanner(GuiGraphics graphics)
	{
		int w = Math.min(320, width - 40);
		int x = (width - w) / 2;
		int y = height - 88;
		Ui.panel(graphics, x, y, w, 34, 10, 0xFF1B1F27);
		Ui.text(graphics, font, "Press a key", x + 12, y + 7, Ui.TEXT);
		Ui.text(graphics, font, "Esc clears the shortcut", x + 12, y + 19, Ui.MUTED);
	}

	// --------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
	{
		layout();
		double mx = event.x();
		double my = event.y();

		if (over(mx, my, closeX - 4, closeY - 4, 15, 15))
		{
			onClose();
			return true;
		}

		searchFocused = over(mx, my, searchX, searchY, searchW, 22);
		if (!query.isEmpty() && over(mx, my, searchX + searchW - 20, searchY + 4, 16, 16))
		{
			query = "";
			cached = null;
			return true;
		}
		if (searchFocused)
		{
			click();
			return true;
		}

		if (over(mx, my, allX - 8, chipY - 4, Ui.width(font, "All") + 22, 18))
		{
			tab = null;
			cached = null;
			scroll = scrollTarget = 0;
			click();
			return true;
		}
		if (over(mx, my, onX - 8, chipY - 4, Ui.width(font, "On") + 22, 18))
		{
			enabledOnly = !enabledOnly;
			cached = null;
			scroll = scrollTarget = 0;
			click();
			return true;
		}

		for (int index = 0; index <= TABS.length; index++)
		{
			if (over(mx, my, sX + 6, rowY(index), sW - 12, 28))
			{
				tab = index == 0 ? null : TABS[index - 1];
				cached = null;
				scroll = scrollTarget = 0;
				click();
				return true;
			}
		}

		if (btnHudX > 0 && over(mx, my, btnHudX, height - 34, btnHudW, 22))
		{
			click();
			minecraft.setScreen(new HudEditorScreen(this));
			return true;
		}
		if (btnResetX > 0 && over(mx, my, btnResetX, height - 34, btnResetW, 22))
		{
			ModuleManager.get().resetPositions();
			click();
			return true;
		}
		if (btnKeyX > 0 && over(mx, my, btnKeyX, height - 34, btnKeyW, 22))
		{
			bindingEditor = true;
			click();
			return true;
		}
		if (btnBackX > 0 && over(mx, my, btnBackX, height - 34, btnBackW, 22))
		{
			onClose();
			return true;
		}

		int index = indexAt(mx, my);
		if (index >= 0)
		{
			Module module = filtered().get(index);
			int columns = mW >= 700 ? 3 : mW >= 430 ? 2 : 1;
			int cardW = (mW - CARD_GAP * (columns - 1)) / columns;
			int x = mX + (index % columns) * (cardW + CARD_GAP);
			if (module.isAction())
			{
				// With settings the card opens them (that is where the value shown on
				// the card is cycled); a pure action simply fires.
				click();
				if (module.settings().isEmpty())
				{
					module.onKey();
				}
				else
				{
					minecraft.setScreen(new ModuleOptionsScreen(this, module));
				}
			}
			else if (module.keyOnly())
			{
				// Held-key modules have nothing to toggle: the card only opens its options.
				click();
				minecraft.setScreen(new ModuleOptionsScreen(this, module));
			}
			else if (mx >= x + cardW - 46 && my < cardTop(index) + CARD_H - 18)
			{
				module.toggle();
				ModuleManager.get().save();
				cached = null;
				click();
			}
			else
			{
				click();
				minecraft.setScreen(new ModuleOptionsScreen(this, module));
			}
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	private int cardTop(int index)
	{
		int columns = mW >= 700 ? 3 : mW >= 430 ? 2 : 1;
		return gTop + Math.round((index / columns) * (CARD_H + CARD_GAP) - scroll);
	}

	private int indexAt(double mx, double my)
	{
		if (my < gTop || my > gTop + gH || mx < mX || mx > mX + mW)
		{
			return -1;
		}
		int columns = mW >= 700 ? 3 : mW >= 430 ? 2 : 1;
		int gap = CARD_GAP;
		int cardW = (mW - gap * (columns - 1)) / columns;
		int column = (int) ((mx - mX) / (cardW + gap));
		if (column >= columns || mx - mX - column * (cardW + gap) > cardW)
		{
			return -1;
		}
		int row = (int) ((my - gTop + scroll) / (CARD_H + gap));
		int index = row * columns + column;
		return index < filtered().size() ? index : -1;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
	{
		if (mouseX >= mX && mouseX <= mX + mW && mouseY >= gTop && mouseY <= gTop + gH)
		{
			scrollTarget -= (float) scrollY * 32.0F;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event)
	{
		if (bindingEditor)
		{
			int key = event.key();
			ModuleManager.get().setEditorKey(key == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_UNKNOWN : key);
			ModuleManager.get().save();
			bindingEditor = false;
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_ESCAPE)
		{
			onClose();
			return true;
		}
		if (searchFocused && event.key() == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty())
		{
			query = query.substring(0, query.length() - 1);
			cached = null;
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event)
	{
		if (!searchFocused)
		{
			return super.charTyped(event);
		}
		String typed = event.codepointAsString();
		if (!typed.isEmpty() && query.length() < 24)
		{
			query += typed;
			cached = null;
			return true;
		}
		return super.charTyped(event);
	}

	// -------------------------------------------------------------- helpers

	private List<Module> filtered()
	{
		String key = (tab == null ? "ALL" : tab.name()) + '|' + enabledOnly + '|' + query;
		if (cached != null && key.equals(cacheKey))
		{
			return cached;
		}
		List<Module> source = tab == null ? ModuleManager.get().all() : ModuleManager.get().byCategory(tab);
		String needle = query.trim().toLowerCase(Locale.ROOT);
		List<Module> list = new ArrayList<>(source.size());
		for (Module module : source)
		{
			if (enabledOnly && !module.isEnabled())
			{
				continue;
			}
			if (!needle.isEmpty()
					&& !module.name().toLowerCase(Locale.ROOT).contains(needle)
					&& !module.id().toLowerCase(Locale.ROOT).contains(needle))
			{
				continue;
			}
			list.add(module);
		}
		cached = list;
		cacheKey = key;
		return list;
	}

	private int enabled()
	{
		int count = 0;
		for (Module module : ModuleManager.get().all())
		{
			if (module.isEnabled())
			{
				count++;
			}
		}
		return count;
	}

	private static String title(Category category)
	{
		return category == Category.PERFORMANCE ? "Performance" : category == Category.UTILITY ? "Utility" : "HUD";
	}

	private static int accent(Category category)
	{
		return category == Category.PERFORMANCE ? Ui.GREEN : category == Category.UTILITY ? Ui.COPPER : Ui.ACCENT;
	}

	private void click()
	{
		AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
	}

	/** Short uppercase label for a GLFW key code, e.g. C or LEFT ALT. */
	private String keyLabel(int key)
	{
		if (key < 0)
		{
			return "UNBOUND";
		}
		String name = com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(key)
				.getDisplayName().getString();
		return name.toUpperCase(Locale.ROOT);
	}

	private static boolean over(double mx, double my, int x, int y, int w, int h)
	{
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	@Override
	public void onClose()
	{
		ModuleManager.get().save();
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen()
	{
		return false;
	}

	private String catIcon(Category category)
	{
		return switch (category)
		{
			case HUD -> "layers";
			case PERFORMANCE -> "bolt";
			case UTILITY -> "wrench";
		};
	}

	private String iconFor(Module module)
	{
		String id = module.id();
		if (id.contains("fps")) return "gauge";
		if (id.contains("ping") || id.contains("network") || id.contains("latency")) return "signal";
		if (id.contains("coord") || id.contains("biome") || id.contains("direction")) return "compass";
		if (id.contains("armor") || id.contains("health") || id.contains("protection")) return "shield";
		if (id.contains("potion") || id.contains("effect")) return "flask";
		if (id.contains("cps") || id.contains("key") || id.contains("sprint")) return "keyboard";
		if (id.contains("memory") || id.contains("ram") || id.contains("tick")) return "chip";
		if (id.contains("time") || id.contains("clock") || id.contains("session")) return "clock";
		if (id.contains("chat") || id.contains("message")) return "chat";
		if (id.contains("scoreboard") || id.contains("tab") || id.contains("list")) return "layers";
		if (id.contains("crosshair")) return "crosshair";
		if (id.contains("hit") || id.contains("damage") || id.contains("reach")) return "target";
		if (id.contains("fog") || id.contains("water") || id.contains("weather") || id.contains("rain")) return "droplet";
		if (id.contains("bright") || id.contains("gamma") || id.contains("light")) return "sun";
		if (id.contains("zoom") || id.contains("eye")) return "eye";
		if (id.contains("particle") || id.contains("animation") || id.contains("effect")) return "sparkle";
		if (id.contains("item") || id.contains("durability") || id.contains("block")) return "cube";
		if (id.contains("player") || id.contains("nametag") || id.contains("entity")) return "users";
		if (id.contains("freelook") || id.contains("motion") || id.contains("speed")) return "arrow";
		return catIcon(module.category());
	}
}
