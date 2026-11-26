package com.valkaw.client;

import com.valkaw.client.gui.ChatSymbolPanel;
import com.valkaw.client.gui.ValkawMainMenu;
import com.valkaw.client.gui.ClientSettingsScreen;
import com.valkaw.client.gui.CrosshairEditorScreen;
import com.valkaw.client.gui.HudEditorScreen;
import com.valkaw.client.module.ModuleManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biomes;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot autopilot behind -Dvalkaw.snap=<scene>. Inactive without the flag.
 * Scenes: menu | settings | hud | crosshair | chat | day | beach.
 * The flow: wait -> act (open screens / teleport) -> settle -> Screenshot.grab
 * into screenshots/valkaw-<scene>.png -> stop the client.
 */
public final class SnapShot {
	private static String scene = "";
	private static boolean ready;
	private static boolean shot;
	private static int waitTicks = 80;
	private static int settleTicks = 30;
	private static int tick;
	private static boolean acted;
	private static int settle;
	private static int spiral;
	private static int spiralRing;
	private static boolean searching;
	private static int framePhase;
	private static int waitPhase;
	private static List<BlockPos> spiralSteps;

	private SnapShot() {
	}

	public static void register() {
		scene = System.getProperty("valkaw.snap", "");
		if (scene.isBlank()) {
			return;
		}
		waitTicks = Integer.getInteger("valkaw.snapDelay", defaultWait(scene));
		settleTicks = Integer.getInteger("valkaw.snapSettle", defaultSettle(scene));
		ClientTickEvents.END_CLIENT_TICK.register(SnapShot::tick);
	}

	private static int defaultWait(String s) {
		return switch (s) {
			case "menu" -> 100;
			case "chat", "day", "beach" -> 160;
			default -> 60;
		};
	}

	private static int defaultSettle(String s) {
		return switch (s) {
			case "hud", "crosshair" -> 40;
			case "day", "beach" -> 80;
			case "menu" -> 0;
			default -> 30;
		};
	}

	private static boolean worldScene() {
		return scene.equals("day") || scene.equals("beach") || scene.equals("chat");
	}

	private static void tick(Minecraft mc) {
		tick++;
		if (!ready) {
			if (worldScene()) {
				// dismiss any menus that quick play left behind
				if (mc.player != null && mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen)) {
					mc.setScreenAndShow(null);
				}
				ready = mc.player != null && mc.level != null;
			} else {
				ready = mc.gui.screen() instanceof ValkawMainMenu;
			}
			if (ready) {
				tick = 0;
				System.out.println("VALKAW SNAP-READY");
			}
			return;
		}
		if (!acted) {
			if (tick < waitTicks) {
				return;
			}
			acted = true;
			settle = 0;
			System.out.println("VALKAW SNAP-ACTED");
			act(mc);
			return;
		}
		if (scene.equals("beach") && searching) {
			searchBeach(mc);
			return;
		}
		if ((scene.equals("beach") || scene.equals("day")) && framePhase > 0 && framePhase < 4) {
			frameShore(mc);
			return;
		}
		if (!shot) {
			if (worldScene() && settle >= settleTicks - 14 && mc.gui != null && mc.gui.hud.getChat() != null) {
				mc.gui.hud.getChat().clearMessages(true);
			}
			settle++;
			if (settle >= settleTicks) {
				shot = true;
				System.out.println("VALKAW SNAP-SHOT");
				if (Boolean.parseBoolean(System.getProperty("valkaw.snapGrab", "false"))) {
					Screenshot.grab(mc.gameDirectory, "valkaw-" + scene + ".png",
							mc.gameRenderer.mainRenderTarget(), 1, c -> {
							});
				}
				settle = 0;
			}
			return;
		}
		// hold for external capture; only self-close without snapHold
		if (!"1".equals(System.getProperty("valkaw.snapHold", "0"))) {
			settle++;
			if (settle >= 40) {
				mc.stop();
			}
		}
	}
	private static void act(Minecraft mc) {
		switch (scene) {
			case "settings" -> mc.setScreenAndShow(new ClientSettingsScreen(null));
			case "hud" -> mc.setScreenAndShow(new HudEditorScreen());
			case "crosshair" -> mc.setScreenAndShow(new CrosshairEditorScreen(null,
					(com.valkaw.client.module.hud.CrosshairModule) ModuleManager.get().byId("crosshair")));
			case "chat" -> {
				mc.setScreenAndShow(new ChatScreen("", false));
				if (mc.gui.screen() instanceof SnapChatAccess access) {
					access.valkawSetInput("Valkaw v1.0.6.3 &bAurora &lWELCOME&r &7- &fDreams forge reality");
				}
				ChatSymbolPanel.setOpen(true, true);
			}
			case "beach" -> {
				searching = true;
				spiralSteps = spiral();
			}
			case "day" -> {
				searching = false;
				framePhase = 2;
				settle = -40; // wait out the teleport below the settle budget
				mc.player.connection.sendCommand("tp 51 78 -110");
			}
			default -> {
			}
		}
	}

	/** Returns true while the search continues; false once a landing spot was found. */
	private static void searchBeach(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		spiral++;
		// two settle ticks between hops so the server applies the teleport
		if (spiral % 3 != 0) {
			return;
		}
		int step = (spiral / 3);
		if (spiralSteps == null || step >= spiralSteps.size()) {
			searching = false; // give up, shoot where we stand
			return;
		}
		BlockPos at = mc.player.blockPosition();
		if (mc.level.getBiome(at).is(Biomes.BEACH)) {
			searching = false;
			settle = 0;
			framePhase = 1;
			return;
		}
		BlockPos target = spiralSteps.get(Math.min(step, spiralSteps.size() - 1));
		int y = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				target.getX(), target.getZ()) + 2;
		mc.player.connection.sendCommand("tp " + target.getX() + " " + y + " " + target.getZ());
	}


/** Open ground search result: the brst candidate near the landing point. */
	private static float bestScore;
	private static int bestDx;
	private static int bestDz;
	private static int bestYaw;

	private static void frameShore(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		if (waitPhase > 0) {
			waitPhase--;
			return;
		}
		if (framePhase == 1) {
			// day pass: fly high first so the terrain streams in around us
			BlockPos at = mc.player.blockPosition();
			bestScore = -1;
			int[][] cands = scene.equals("beach")
					? new int[][]{{0, 0}, {6, 0}, {-6, 0}, {0, 6}, {0, -6}, {8, 4}, {-8, 4}, {10, 0}, {4, 8}, {-6, 8}}
					: new int[][]{{0, 0}, {40, 0}, {-40, 0}, {0, 40}, {0, -40}, {56, 20}, {-44, 28}, {24, 44}, {-56, -24}};
			float room = -1;
			for (int[] c : cands) {
				BlockPos p = at.offset(c[0], 0, c[1]);
				if (scene.equals("beach") && !mc.level.getBiome(p).is(Biomes.BEACH)) {
					continue;
				}
				float score = openScore(mc, p);
				if (score > room) {
					room = score;
					bestDx = c[0];
					bestDz = c[1];
				}
			}
			BlockPos target = at.offset(bestDx, 0, bestDz);
			if (scene.equals("day")) {
				// first hop: get the server to stream the chunk, high in the sky
				mc.player.connection.sendCommand("tp " + target.getX() + " 200 " + target.getZ());
				framePhase = 2;
				waitPhase = 90;
				return;
			}
			int y = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,											 target.getX(), target.getZ()) + 1;
			mc.player.connection.sendCommand("tp " + target.getX() + " " + y + " " + target.getZ());
			framePhase = 3;
			waitPhase = 40;
			return;
		}
		if (framePhase == 2) {
			// terrain has streamed in; drop onto the surface
			BlockPos at = mc.player.blockPosition();
			int y = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,											 at.getX(), at.getZ()) + 1;
			mc.player.connection.sendCommand("tp " + at.getX() + " " + y + " " + at.getZ());
			framePhase = 3;
			waitPhase = 40;
			return;
		}
		// phase 3: face the widest open horizon and clean the chat
		BlockPos at = mc.player.blockPosition();
		int[] yaws = {0, 45, 90, 135, 180, 225, 270, 315};
		int best = 0;
		float bestLook = -1;
		for (int k = 0; k < 8; k++) {
			double rad = Math.toRadians(yaws[k]);
			int dxx = (int) (-Math.sin(rad));
			int dzz = (int) Math.cos(rad);
			int open = 0;
			for (int ds = 1; ds <= 16; ds++) {
				var st = mc.level.getBlockState(at.offset(dxx * ds, 0, dzz * ds));
				if (st.isAir() || st.getBlock() == net.minecraft.world.level.block.Blocks.WATER) {
					open++;
				} else {
					break;
				}
			}
			if (open > bestLook) {
				bestLook = open;
				best = yaws[k];
			}
		}
		mc.player.setYRot(best);
		mc.player.setYHeadRot(best);
		mc.player.setXRot(-2);
		if (mc.gui != null && mc.gui.hud.getChat() != null) {
			mc.gui.hud.getChat().clearMessages(true);
		}
		framePhase = 4;
		waitPhase = 30;
	}
	/** Counts free air at head height around the landing spot: bigger = clearer view. */
	private static float openScore(Minecraft mc, BlockPos at) {
		int y = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				at.getX(), at.getZ()) + 1;
		float score = 0;
		for (int dx = -2; dx <= 2; dx += 2) {
			for (int dz = -2; dz <= 2; dz += 2) {
				for (int dy = 0; dy <= 2; dy++) {
					BlockPos p = at.offset(dx, y - at.getY() + dy, dz);
					if (mc.level.getBlockState(p).isAir()) {
						score += 1;
					}
				}
			}
		}
		return score;
	}

	/** Square spiral positions in blocks, radius 32..416. */
	private static List<BlockPos> spiral() {
		List<BlockPos> out = new ArrayList<>();
		int step = 32;
		int x = 0, z = 0;
		out.add(new BlockPos(x, 0, z));
		for (int ring = 1; ring <= 13; ring++) {
			x += step;
			z += step;
			for (int i = 0; i < 2 * ring; i++) {
				x -= step;
				out.add(new BlockPos(x, 0, z));
			}
			for (int i = 0; i < 2 * ring; i++) {
				z -= step;
				out.add(new BlockPos(x, 0, z));
			}
			for (int i = 0; i < 2 * ring; i++) {
				x += step;
				out.add(new BlockPos(x, 0, z));
			}
			for (int i = 0; i < 2 * ring; i++) {
				z += step;
				out.add(new BlockPos(x, 0, z));
			}
		}
		return out;
	}
}
