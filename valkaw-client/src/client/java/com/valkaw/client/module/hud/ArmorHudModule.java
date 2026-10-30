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
	private static final int PAD = 4;

	private Layout layout = Layout.VERTICAL;
	private boolean split;
	private final ItemStack[] armor = new ItemStack[SLOTS.length];

	public ArmorHudModule() {
		super("armor", "Armor HUD", 120, 4);
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
		// column so every number starts at the same x.
		int pairW = ICON;
		if (!held.isEmpty()) {
			pairW = Math.max(pairW, pairWidth(font, held));
		}
		for (ItemStack stack : armor) {
			if (!stack.isEmpty()) {
				pairW = Math.max(pairW, pairWidth(font, stack));
			}
		}

		int pieces = count + (held.isEmpty() ? 0 : 1);
		int bodyW = layout == Layout.HORIZONTAL ? pieces * (pairW + GAP) - GAP : pairW;
		int bodyH = layout == Layout.HORIZONTAL ? ROW_H : pieces * (ROW_H + GAP) - GAP;

		if (split) {
			int prefixW = Hud.prefix(graphics, font, x, y, "ARMOR");
			w = Math.max(prefixW, bodyW);
			h = 13 + bodyH;
			drawItems(graphics, font, held, armor, x, y + 13, pairW);
		} else {
			int headerW = Hud.width(font, Hud.label("ARMOR")) + PAD;
			w = Math.max(headerW, bodyW + PAD);
			h = 15 + bodyH;
			graphics.fill(x, y, x + w, y + h, Hud.BG);
			graphics.fill(x, y, x + 1, y + h, Hud.color());
			Hud.text(graphics, font, Hud.label("ARMOR"), x + 4, y + 2);
			graphics.fill(x + 4, y + 12, x + 4 + Hud.width(font, Hud.label("ARMOR")), y + 13, Hud.color());
			drawItems(graphics, font, held, armor, x + 4, y + 15, pairW);
		}
	}

	private static int pairWidth(Font font, ItemStack stack) {
		if (!stack.isDamageableItem()) {
			return ICON;
		}
		return ICON + 3 + font.width(Integer.toString(stack.getMaxDamage() - stack.getDamageValue()));
	}

	private void drawItems(GuiGraphics graphics, Font font, ItemStack held, ItemStack[] armor, int startX, int startY,
			int pairW) {
		int index = 0;
		if (!held.isEmpty()) {
			piece(graphics, font, held, startX, startY, pairW);
			index++;
		}
		for (ItemStack stack : armor) {
			if (stack.isEmpty()) {
				continue;
			}
			int px = layout == Layout.HORIZONTAL ? startX + index * (pairW + GAP) : startX;
			int py = layout == Layout.HORIZONTAL ? startY : startY + index * (ROW_H + GAP);
			piece(graphics, font, stack, px, py, pairW);
			index++;
		}
	}

	private static void piece(GuiGraphics graphics, Font font, ItemStack stack, int px, int py, int pairW) {
		graphics.renderItem(stack, px + 1, py + 1);
		if (!stack.isDamageableItem()) {
			return;
		}
		int left = stack.getMaxDamage() - stack.getDamageValue();
		int max = stack.getMaxDamage();
		float ratio = max <= 0 ? 1.0F : (float) left / max;
		int color = ratio > 0.6F ? 0xFF63F5B0 : ratio > 0.25F ? 0xFFFFD166 : 0xFFFF7A9A;
		graphics.drawString(font, Integer.toString(left), px + ICON + 2, py + 5, color, false);
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
