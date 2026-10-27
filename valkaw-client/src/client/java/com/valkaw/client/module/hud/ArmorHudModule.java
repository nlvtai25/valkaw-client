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
	private static final int BOX_W = 18;
	private static final int BOX_H = 27;
	private static final int GAP = 2;

	private Layout layout = Layout.HORIZONTAL;
	private boolean split;
	private final ItemStack[] armor = new ItemStack[SLOTS.length];

	public ArmorHudModule() {
		super("armor", "Armor HUD", 120, 4);
		setEnabled(true);
	}

	@Override
	protected int previewWidth() {
		return 80;
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

		int headerW = Hud.width(font, Hud.label("ARMOR")) + 8;
		int rowW = count == 0 ? 0 : count * BOX_W + (count - 1) * GAP;
		int contentW = Math.max(rowW, held.isEmpty() ? 0 : BOX_W);
		int armorH = count == 0 ? 0 : layout == Layout.HORIZONTAL ? BOX_H : count * BOX_H + (count - 1) * GAP;
		int mainH = held.isEmpty() ? 0 : BOX_H + GAP;
		int contentH = mainH + armorH;
		if (split) {
			int prefixW = Hud.prefix(graphics, font, x, y, "ARMOR");
			w = Math.max(prefixW, contentW);
			h = 13 + contentH;
			drawItems(graphics, font, held, armor, x, y + 13, true);
		} else {
			w = Math.max(headerW, contentW + 8);
			h = 15 + contentH;
			graphics.fill(x, y, x + w, y + h, Hud.BG);
			graphics.fill(x, y, x + 1, y + h, Hud.color());
			Hud.text(graphics, font, Hud.label("ARMOR"), x + 4, y + 2);
			graphics.fill(x + 4, y + 12, x + 4 + Hud.width(font, Hud.label("ARMOR")), y + 13, Hud.color());
			drawItems(graphics, font, held, armor, x + 4, y + 15, false);
		}
	}

	private void drawItems(GuiGraphics graphics, Font font, ItemStack held, ItemStack[] armor, int startX, int startY,
			boolean boxes) {
		int cursorY = startY;
		if (!held.isEmpty()) {
			piece(graphics, font, held, startX, cursorY, boxes);
			cursorY += BOX_H + GAP;
		}
		int index = 0;
		for (ItemStack stack : armor) {
			if (stack.isEmpty()) {
				continue;
			}
			int px = layout == Layout.HORIZONTAL ? startX + index * (BOX_W + GAP) : startX;
			int py = layout == Layout.HORIZONTAL ? cursorY : cursorY + index * (BOX_H + GAP);
			piece(graphics, font, stack, px, py, boxes);
			index++;
		}
	}

	private static void piece(GuiGraphics graphics, Font font, ItemStack stack, int px, int py, boolean box) {
		if (box) {
			graphics.fill(px, py, px + BOX_W, py + BOX_H, Hud.BG);
			graphics.fill(px, py, px + 1, py + BOX_H, Hud.color());
		}
		graphics.renderItem(stack, px + 1, py + 1);
		if (stack.isDamageableItem()) {
			int left = stack.getMaxDamage() - stack.getDamageValue();
			int max = stack.getMaxDamage();
			float ratio = max <= 0 ? 1.0F : (float) left / max;
			String text = Integer.toString(left);
			int color = ratio > 0.6F ? 0xFF63F5B0 : ratio > 0.25F ? 0xFFFFD166 : 0xFFFF7A9A;
			graphics.drawString(font, text, px + (BOX_W - font.width(text)) / 2, py + BOX_H - 10, color, false);
		}
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
