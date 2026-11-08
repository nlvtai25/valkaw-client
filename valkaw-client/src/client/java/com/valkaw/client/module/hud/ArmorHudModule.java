package com.valkaw.client.module.hud;

import com.google.gson.JsonObject;
import com.valkaw.client.hud.Hud;
import com.valkaw.client.module.HudModule;
import com.valkaw.client.module.Layout;
import com.valkaw.client.module.Setting;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** One Armor HUD by default; Split separates the pieces inside the same module. */
public class ArmorHudModule extends HudModule {
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
			EquipmentSlot.FEET};
	private static final int ICON = 18;
	private static final int ROW_H = 18;
	private static final int GAP = 2;
	private static final int PAD = 1;

	private Layout layout = Layout.VERTICAL;
	private boolean split;
	private final ItemStack[] armor = new ItemStack[SLOTS.length];
	/** Durability strings computed once per frame and shared by measure + draw. */
	private final String[] dur = new String[SLOTS.length + 1];

	public ArmorHudModule() {
		super("armor", "Armor HUD", 120, 3);
		setEnabled(true);
	}

	@Override
	protected int previewWidth() {
		return 60;
	}

	@Override
	protected int previewHeight() {
		return 56;
	}

	@Override
	public void render(GuiGraphics graphics, Font font) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			w = 0;
			h = 0;
			return;
		}
		ItemStack held = player.getMainHandItem();
		ItemStack[] armor = this.armor;
		int count = 0;
		for (int i = 0; i < SLOTS.length; i++) {
			armor[i] = player.getItemBySlot(SLOTS[i]);
			if (!armor[i].isEmpty()) {
				count++;
			}
		}
		if (count == 0 && held.isEmpty()) {
			w = 0;
			h = 0;
			return;
		}

		// Item on the left, durability on the right; the widest pair sets the
		// column so every number starts at the same x. The durability string is
		// built once here and reused by the draw pass.
		int pairW = ICON;
		dur[0] = durString(held);
		if (!held.isEmpty() && dur[0] != null) {
			pairW = Math.max(pairW, ICON + 3 + font.width(dur[0]));
		}
		for (int i = 0; i < SLOTS.length; i++) {
			if (!armor[i].isEmpty()) {
				dur[i + 1] = durString(armor[i]);
				if (dur[i + 1] != null) {
					pairW = Math.max(pairW, ICON + 3 + font.width(dur[i + 1]));
				}
			}
		}

		int pieces = count + (held.isEmpty() ? 0 : 1);
		int bodyW = layout == Layout.HORIZONTAL ? pieces * (pairW + GAP) - GAP : pairW;
		int bodyH = layout == Layout.HORIZONTAL ? ROW_H : pieces * (ROW_H + GAP) - GAP;

		int bx = Hud.plateX(x);
		if (split) {
			int prefixW = Hud.prefix(graphics, font, x, y, "Armor");
			w = Math.max(prefixW, bodyW);
			h = 13 + bodyH;
			drawItems(graphics, font, held, armor, dur, bx, y + 13, pairW);
		} else {
			int headerW = Hud.width(font, Hud.label("Armor")) + PAD;
			w = Math.max(headerW, bodyW + PAD);
			h = 15 + bodyH;
			graphics.fill(bx, y, bx + w, y + h, Hud.BG);
			Hud.text(graphics, font, Hud.label("Armor"), bx + 1, y + 2);
			graphics.fill(bx + 1, y + 12, bx + 1 + Hud.width(font, Hud.label("Armor")), y + 13, Hud.color());
			drawItems(graphics, font, held, armor, dur, bx + 1, y + 15, pairW);
		}
	}

	private static String durString(ItemStack stack) {
		return stack.isDamageableItem() ? Integer.toString(stack.getMaxDamage() - stack.getDamageValue()) : null;
	}

	private void drawItems(GuiGraphics graphics, Font font, ItemStack held, ItemStack[] armor, String[] dur, int startX,
			int startY, int pairW) {
		int index = 0;
		if (!held.isEmpty()) {
			piece(graphics, font, held, dur[0], startX, startY, pairW);
			index++;
		}
		for (int i = 0; i < armor.length; i++) {
			ItemStack stack = armor[i];
			if (stack.isEmpty()) {
				continue;
			}
			int px = layout == Layout.HORIZONTAL ? startX + index * (pairW + GAP) : startX;
			int py = layout == Layout.HORIZONTAL ? startY : startY + index * (ROW_H + GAP);
			piece(graphics, font, stack, dur[i + 1], px, py, pairW);
			index++;
		}
	}

	private static void piece(GuiGraphics graphics, Font font, ItemStack stack, String dur, int px, int py,
			int pairW) {
		graphics.renderItem(stack, px + 1, py + 1);
		if (dur == null) {
			return;
		}
		int max = stack.getMaxDamage();
		int left = max - stack.getDamageValue();
		float ratio = max <= 0 ? 1.0F : (float) left / max;
		int color = ratio > 0.6F ? 0xFF63F5B0 : ratio > 0.25F ? 0xFFFFD166 : 0xFFFF7A9A;
		graphics.drawString(font, dur, px + ICON + 2, py + 5, color, false);
	}

	@Override
	public List<Setting> settings() {
		return List.of(
				Setting.choice("LAYOUT", () -> layout.name(), () -> layout = layout.next()),
				Setting.choice("SPLIT", () -> split ? "ON" : "OFF", () -> split = !split));
	}

	@Override
	public String option() {
		return split ? "SPLIT" : layout.name();
	}

	@Override
	public void cycleOption() {
		layout = layout.next();
	}

	@Override
	public void writeExtra(JsonObject json) {
		super.writeExtra(json);
		json.addProperty("layout", layout.name());
		json.addProperty("split", split);
	}

	@Override
	public void readExtra(JsonObject json) {
		super.readExtra(json);
		if (json.has("layout")) {
			layout = Layout.parse(json.get("layout").getAsString());
		}
		if (json.has("split")) {
			split = json.get("split").getAsBoolean();
		}
	}
}
