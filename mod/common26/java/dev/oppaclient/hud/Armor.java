package dev.oppaclient.hud;

import dev.oppaclient.Type;
import dev.oppaclient.platform.Gfx;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class Armor extends Element {
	private static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
	private static final int ROW = 18;

	Armor() {
		super("armor", "Armor", true, 1, 0.5f, -6, 0);
	}

	@Override
	public List<Option> options() {
		return List.of(new Choice("durability", "Durability", List.of("Remaining", "Percent", "Hidden")), new Toggle("held", "Held item", true));
	}

	private final List<ItemStack> worn = new ArrayList<>();
	private long wornFrame = -1;
	private boolean wornPreview;

	private List<ItemStack> worn(Minecraft mc, boolean preview) {
		if (wornFrame == Hud.frame() && wornPreview == preview) return worn;
		wornFrame = Hud.frame();
		wornPreview = preview;
		List<ItemStack> out = worn;
		out.clear();
		if (mc.player != null) {
			for (EquipmentSlot slot : SLOTS) {
				ItemStack s = mc.player.getItemBySlot(slot);
				if (slot == EquipmentSlot.MAINHAND && !on("held")) continue;
				if (!s.isEmpty() && (slot != EquipmentSlot.MAINHAND || s.isDamageableItem())) out.add(s);
			}
		}
		if (out.isEmpty() && preview) {
			out.add(new ItemStack(Items.DIAMOND_HELMET));
			out.add(new ItemStack(Items.DIAMOND_CHESTPLATE));
		}
		return out;
	}

	@Override
	public boolean idle(Minecraft mc) {
		return worn(mc, false).isEmpty();
	}

	private String durability(ItemStack s) {
		String mode = pick("durability");
		if (mode.equals("Hidden")) return "";
		if (!s.isDamageableItem()) return s.getCount() > 1 ? Integer.toString(s.getCount()) : "";
		int left = s.getMaxDamage() - s.getDamageValue();
		return mode.equals("Percent") ? left * 100 / s.getMaxDamage() + "%" : Integer.toString(left);
	}

	@Override
	public int width(Minecraft mc, boolean preview) {
		int w = 0;
		for (ItemStack s : worn(mc, preview)) w = Math.max(w, Type.STRONG.width(durability(s)));
		return 16 + (w > 0 ? 4 + w : 0);
	}

	@Override
	public int height(Minecraft mc, boolean preview) {
		return Math.max(16, worn(mc, preview).size() * ROW - 2);
	}

	@Override
	public void draw(Gfx ctx, Minecraft mc, boolean preview) {
		int y = 0;
		int w = width(mc, preview);
		boolean iconsLeft = placement().ax < 0.5f;
		for (ItemStack s : worn(mc, preview)) {
			String d = durability(s);
			float left = s.isDamageableItem() ? (s.getMaxDamage() - s.getDamageValue()) / (float) s.getMaxDamage() : 1;
			float alpha = left < 0.15f ? 1f : 0.85f;
			if (iconsLeft) {
				ctx.item(s, 0, y);
				if (!d.isEmpty()) text(ctx, Type.STRONG, d, 20, y + 4, alpha);
			} else {
				ctx.item(s, w - 16, y);
				if (!d.isEmpty()) text(ctx, Type.STRONG, d, w - 20 - Type.STRONG.width(d), y + 4, alpha);
			}
			y += ROW;
		}
	}
}
