package com.valkaw.client.gui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class ValkawMainMenu extends Screen {
	private static final Logger LOGGER = LoggerFactory.getLogger("valkawclient");
	private static final boolean BERYL = FabricLoader.getInstance().isModLoaded("beryl");
	private static final Style MONO = Style.EMPTY.withFont(new FontDescription.Resource(Minecraft.UNIFORM_FONT));
	private static final Component BRAND = Component.literal("VALKAW").withStyle(MONO);
	private static final Component BRAND_TAIL = Component.literal("CLIENT").withStyle(MONO);
	private static final Component SUBTITLE = Component.literal("1.21.11  //  FABRIC").withStyle(MONO);
	private static final Component BUILD = Component.literal("BUILD v1.0.0").withStyle(MONO);
	private static final Component NAV_LABEL = Component.literal("// NAVIGATION").withStyle(MONO);
	private static final Component SESSION_LABEL = Component.literal("// SESSION").withStyle(MONO);
	private static final Component READY = Component.literal("READY TO PLAY").withStyle(MONO);
	private static final Component FABRIC_LABEL = Component.literal("FABRIC LOADER").withStyle(MONO);
	private static final Component FABRIC_VALUE = Component.literal("0.20.1").withStyle(MONO);
	private static final Component RENDER_LABEL = Component.literal("RENDERER").withStyle(MONO);
	private static final Component CLIENT_LABEL = Component.literal("CLIENT BUILD").withStyle(MONO);
	private static final Component CLIENT_VALUE = Component.literal("v1.0.0").withStyle(MONO);
	private static final Component USER_LABEL = Component.literal("USER").withStyle(MONO);
	private static final Component MEMORY_LABEL = Component.literal("MEMORY").withStyle(MONO);
	private static final Component WINDOW_LABEL = Component.literal("WINDOW").withStyle(MONO);
	private static final Component JAVA_LABEL = Component.literal("JAVA").withStyle(MONO);
	private static final Component NOMINAL = Component.literal("SYSTEM NOMINAL").withStyle(MONO);
	private static final Component FOOTER_LEFT = Component.literal("VALKAW CLIENT  v1.0.0").withStyle(MONO);
	private static final Component FOOTER_RIGHT = Component.literal("FABRIC 0.20.1").withStyle(MONO);

	private static final int CYAN = 0xFF7DF9FF;
	private static final int PURPLE = 0xFFB18CFF;
	private static final int TEXT = 0xFFD7E3FF;
	private static final int MUTED = 0xFF7183A8;
	private static final int DANGER = 0xFFFF7A9A;
	private static final int GREEN = 0xFF63F5B0;

	private final Star[] stars = new Star[90];
	private String status = "";
	private long statusUntil;
	private long openedAt;

	public ValkawMainMenu() {
		super(Component.literal("Valkaw Client"));
		Random random = new Random(0x5EED);
		for (int i = 0; i < stars.length; i++) {
			stars[i] = new Star(random.nextFloat(), random.nextFloat(), 1 + random.nextInt(2),
					random.nextFloat() * 6.2832F, 0.35F + random.nextFloat());
		}
		openedAt = System.nanoTime();
		LOGGER.info("Valkaw main menu opened");
	}

	@Override
	protected void init() {
		clearWidgets();

		int navX = contentX();
		int navY = contentY() + 2;
		int navW = navWidth();
		int navH = navHeight();
		int navGap = navGap();
		String playLabel = navW >= 126 ? "00  SINGLEPLAYER" : "00  PLAY";
		addRenderableWidget(new ValkawButton(navX, navY, navW, navH, label(playLabel), CYAN, true, true,
				() -> minecraft.setScreen(new SelectWorldScreen(this))));

		int row = navY + navH + navGap;
		addRenderableWidget(new ValkawButton(navX, row, navW, navH, label("01  MULTIPLAYER"), PURPLE, false, true,
				() -> minecraft.setScreen(new JoinMultiplayerScreen(this))));
		addRenderableWidget(new ValkawButton(navX, row + navH + navGap, navW, navH, label("02  SETTINGS"), PURPLE,
				false, true, () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))));
		String clientLabel = navW >= 126 ? "03  CLIENT SETTINGS" : "03  CLIENT";
		addRenderableWidget(new ValkawButton(navX, row + (navH + navGap) * 2, navW, navH, label(clientLabel), PURPLE,
				false, true, () -> minecraft.setScreen(new ClientSettingsScreen(this))));
		addRenderableWidget(new ValkawButton(navX, row + (navH + navGap) * 3, navW, navH, label("04  QUIT"), DANGER,
				false, true, () -> minecraft.stop()));
	}

	private static Component label(String text) {
		return Component.literal(text).withStyle(MONO);
	}

	private String rendererName() {
		if (FabricLoader.getInstance().isModLoaded("vulkanmod")) {
			return "VULKAN";
		}
		if (FabricLoader.getInstance().isModLoaded("sodium")) {
			return "SODIUM";
		}
		return "CUSTOM";
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		int hx = heroX();
		int hy = contentY();
		int hw = heroWidth();
		int hh = heroHeight();

		drawHeader(graphics);
		drawHero(graphics, hx, hy, hw, hh);
		drawFooter(graphics);

		super.render(graphics, mouseX, mouseY, partialTick);

		String message = System.currentTimeMillis() < statusUntil ? status : "";
		if (!message.isEmpty()) {
			graphics.drawCenteredString(font, Component.literal(message).withStyle(MONO),
					width / 2, height - 34, DANGER);
		}
	}

	private void drawHeader(GuiGraphics graphics) {
		int x = contentX();
		int right = x + contentWidth();
		int y = 17;

		graphics.fill(x, y, x + 4, y + 19, CYAN);
		drawScaled(graphics, font, BRAND, x + 10, y - 2, 1.7F, 0xFFFFFFFF);
		int tailX = x + 12 + (int) (font.width(BRAND) * 1.7F) + 7;
		graphics.drawString(font, BRAND_TAIL, tailX, y + 7, CYAN, false);
		graphics.drawString(font, SUBTITLE, x + 10, y + 22, MUTED, false);

		int buildX = right - font.width(BUILD);
		graphics.drawString(font, BUILD, buildX, y + 14, MUTED, false);

		graphics.fill(x, 49, right, 50, BERYL ? 0xFF1A2A3E : 0x337DF9FF);
	}

	private void drawHero(GuiGraphics graphics, int x, int y, int w, int h) {
		if (BERYL) {
			graphics.fill(x, y, x + w, y + h, 0xFF0A1120);
			for (int gx = x + 12; gx < x + w; gx += 24) {
				graphics.fill(gx, y + 1, gx + 1, y + h - 1, 0xFF101A2C);
			}
			for (int gy = y + 12; gy < y + h; gy += 24) {
				graphics.fill(x + 1, gy, x + w - 1, gy + 1, 0xFF101A2C);
			}
			drawV(graphics, x + w / 2, y + 92, 54, 0xFF0B1A2C);
			graphics.renderOutline(x, y, w, h, 0xFF2A3F5C);
			graphics.fill(x, y, x + w, y + 1, CYAN);
			graphics.fill(x, y, x + 2, y + h, 0xFF1E4A5A);
			corner(graphics, x, y, w, h);
		} else {
			graphics.fill(x, y, x + w, y + h, 0xCC0A1120);
			graphics.fillGradient(x, y, x + w, y + h, 0x331A2A55, 0xCC080C18);
			for (int gx = x + 12; gx < x + w; gx += 24) {
				graphics.fill(gx, y + 1, gx + 1, y + h - 1, 0x067DF9FF);
			}
			for (int gy = y + 12; gy < y + h; gy += 24) {
				graphics.fill(x + 1, gy, x + w - 1, gy + 1, 0x067DF9FF);
			}
			drawV(graphics, x + w / 2, y + 92, 54, 0x147DF9FF);
			graphics.renderOutline(x, y, w, h, 0x667DF9FF);
			graphics.fill(x, y, x + w, y + 1, CYAN);
			graphics.fill(x, y, x + 2, y + h, 0x557DF9FF);
			corner(graphics, x, y, w, h);
		}

		int pad = 16;
		graphics.drawString(font, SESSION_LABEL, x + pad, y + 12, MUTED, false);
		graphics.fill(x + pad, y + 26, x + pad + 4, y + 30, GREEN);
		graphics.drawString(font, READY, x + pad + 10, y + 24, GREEN, false);
		graphics.fill(x + pad, y + 40, x + w - pad, y + 41, BERYL ? 0xFF162438 : 0x227DF9FF);

		if (h >= 180) {
			int rowY = y + 54;
			int gap = h >= 222 ? 19 : 22;
			stat(graphics, x + pad, rowY, FABRIC_LABEL, FABRIC_VALUE);
			stat(graphics, x + pad, rowY + gap, RENDER_LABEL, label(rendererName()));
			stat(graphics, x + pad, rowY + gap * 2, CLIENT_LABEL, CLIENT_VALUE);
			if (h >= 200) {
				stat(graphics, x + pad, rowY + gap * 3, USER_LABEL,
						Component.literal(minecraft.getUser().getName()).withStyle(MONO));
			}
			if (h >= 222) {
				long mb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
				stat(graphics, x + pad, rowY + gap * 4, MEMORY_LABEL,
						Component.literal(mb + " MB").withStyle(MONO));
				stat(graphics, x + pad, rowY + gap * 5, WINDOW_LABEL,
						Component.literal(minecraft.getWindow().getWidth() + " x "
								+ minecraft.getWindow().getHeight()).withStyle(MONO));
				stat(graphics, x + pad, rowY + gap * 6, JAVA_LABEL,
						Component.literal(javaVersion()).withStyle(MONO));
			}
			drawStringShadow(graphics, "SYSTEM NOMINAL", x + pad, y + h - 18, GREEN);
			for (int i = 0; i < 3; i++) {
				graphics.fill(x + pad + 96 + i * 7, y + h - 18, x + pad + 100 + i * 7, y + h - 14,
						i == 0 ? GREEN : (BERYL ? 0xFF2A3A55 : 0x667DF9FF));
			}
		}

		int navX = contentX();
		graphics.drawString(font, NAV_LABEL, navX, contentY() - 12, MUTED, false);
	}

	private static String javaVersion() {
		String v = System.getProperty("java.version", "?");
		int dot = v.indexOf('.');
		return dot > 0 ? v.substring(0, dot) : v;
	}

	private void stat(GuiGraphics graphics, int x, int y, Component name, Component value) {
		graphics.drawString(font, name, x, y, MUTED, false);
		int valueX = heroX() + heroWidth() - 16 - font.width(value);
		graphics.drawString(font, value, valueX, y, TEXT, false);
	}

	private void drawFooter(GuiGraphics graphics) {
		int x = contentX();
		int right = x + contentWidth();
		int y = height - 16;

		graphics.fill(x, y - 8, right, y - 7, 0xFF162438);
		graphics.drawString(font, FOOTER_LEFT, x, y, MUTED, false);
		graphics.drawString(font, FOOTER_RIGHT, right - font.width(FOOTER_RIGHT), y, MUTED, false);
	}

	private static void drawScaled(GuiGraphics graphics, Font font, Component text, float x, float y, float scale,
			int color) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.drawString(font, text, 0, 0, color, false);
		graphics.pose().popMatrix();
	}

	private static void drawStringShadow(GuiGraphics graphics, String text, int x, int y, int color) {
		graphics.drawString(Minecraft.getInstance().font, Component.literal(text).withStyle(MONO), x, y, color, false);
	}

	private static void drawV(GuiGraphics graphics, int cx, int cy, int size, int color) {
		for (int i = 0; i < size; i++) {
			int half = size / 2;
			int x = cx - half + i / 2;
			int y = cy - size / 2 + i;
			graphics.fill(x, y, x + 2, y + 1, color);
			graphics.fill(cx + half - i / 2, y, cx + half - i / 2 + 2, y + 1, color);
		}
	}

	private static void corner(GuiGraphics graphics, int x, int y, int w, int h) {
		int c = 14;
		graphics.fill(x, y, x + c, y + 2, PURPLE);
		graphics.fill(x, y, x + 2, y + c, PURPLE);
		graphics.fill(x + w - c, y, x + w, y + 2, PURPLE);
		graphics.fill(x + w - 2, y, x + w, y + c, PURPLE);
		graphics.fill(x, y + h - 2, x + c, y + h, PURPLE);
		graphics.fill(x, y + h - c, x + 2, y + h, PURPLE);
		graphics.fill(x + w - c, y + h - 2, x + w, y + h, PURPLE);
		graphics.fill(x + w - 2, y + h - c, x + w, y + h, PURPLE);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if (BERYL) {
			renderBerylBackground(graphics, mouseX, mouseY);
			return;
		}
		graphics.fillGradient(0, 0, width, height, 0xFF03050B, 0xFF0A1020);
		graphics.fillGradient(width / 2, 0, width, height / 2, 0x001E1B4A, 0x182A1B5A);
		graphics.fillGradient(0, height * 2 / 3, width, height, 0x00000000, 0x0C1E7FFF);
		graphics.fillGradient(0, 0, width, 42, 0x99000000, 0x00000000);
		graphics.fillGradient(0, height - 30, width, height, 0x00000000, 0x99000000);

		long now = System.nanoTime();
		float elapsed = (now - openedAt) / 1_000_000_000.0F;
		float mx = (mouseX - width / 2.0F) * 0.012F;
		float my = (mouseY - height / 2.0F) * 0.012F;

		for (Star star : stars) {
			float twinkle = 0.40F + 0.60F * (float) Math.sin(elapsed * star.depth + star.phase);
			int alpha = (int) (twinkle * 170.0F);
			int x = (int) (star.x * width + mx * star.depth);
			int y = (int) (star.y * height + my * star.depth);
			int color = (alpha << 24) | 0xDDEEFF;
			graphics.fill(x, y, x + star.size, y + star.size, color);
		}
	}

	/** Beryl's shader pass mis-blends animated/translucent GUI quads on Vulkan. */
	private void renderBerylBackground(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.fillGradient(0, 0, width, height, 0xFF03050B, 0xFF0A1020);
		graphics.fill(0, 0, width, 42, 0xFF02040A);
		graphics.fill(0, height - 30, width, height, 0xFF02040A);
		float mx = (mouseX - width / 2.0F) * 0.012F;
		float my = (mouseY - height / 2.0F) * 0.012F;
		for (Star star : stars) {
			int x = (int) (star.x * width + mx * star.depth);
			int y = (int) (star.y * height + my * star.depth);
			graphics.fill(x, y, x + star.size, y + star.size, 0xFFB8C8E0);
		}
	}

	private int margin() {
		return width < 520 ? 16 : 24;
	}

	private int contentWidth() {
		return Math.min(680, width - margin() * 2);
	}

	private int contentX() {
		return (width - contentWidth()) / 2;
	}

	private int contentY() {
		return 68;
	}

	private int navWidth() {
		return contentWidth() >= 430 ? 132 : 112;
	}

	private int navHeight() {
		return height < 260 ? 22 : 26;
	}

	private int navGap() {
		return height < 260 ? 5 : 8;
	}

	private int heroX() {
		return contentX() + navWidth() + 14;
	}

	private int heroWidth() {
		return contentWidth() - navWidth() - 14;
	}

	private int heroHeight() {
		return Math.max(120, Math.min(258, height - contentY() - 52));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	private record Star(float x, float y, int size, float phase, float depth) {
	}

	private static final class ValkawButton extends AbstractWidget {
		private final Runnable action;
		private final int accent;
		private final boolean primary;
		private final boolean nav;

		private ValkawButton(int x, int y, int width, int height, Component message, int accent, boolean primary,
				boolean nav, Runnable action) {
			super(x, y, width, height, message);
			this.accent = accent;
			this.primary = primary;
			this.nav = nav;
			this.action = action;
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			boolean hot = isHoveredOrFocused();
			int x = getX();
			int y = getY();
			int w = getWidth();
			int h = getHeight();

			if (hot && !BERYL) {
				graphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, primary ? 0x337DF9FF : 0x1FB18CFF);
			}
			graphics.fill(x, y, x + w, y + h, BERYL ? (hot ? 0xFF182740 : 0xFF0E1626)
					: (hot ? 0xD0182740 : 0xA00E1626));
			graphics.renderOutline(x, y, w, h, hot ? accent : (BERYL ? 0xFF2A3A55 : 0x446A7EA8));
			graphics.fill(x, y, x + (primary ? 3 : 2), y + h, accent);

			if (nav) {
				graphics.drawString(Minecraft.getInstance().font, getMessage(), x + 10, y + (h - 8) / 2,
						hot ? 0xFFFFFFFF : 0xFFB9C8E6, false);
				if (hot) {
					graphics.drawString(Minecraft.getInstance().font, ">", x + w - 11, y + (h - 8) / 2, accent, false);
				}
			} else {
				graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), x + w / 2 + 2,
						y + (h - 8) / 2, hot ? 0xFFFFFFFF : TEXT);
			}
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			action.run();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}
}
