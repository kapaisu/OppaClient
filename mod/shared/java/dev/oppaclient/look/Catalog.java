package dev.oppaclient.look;

public final class Catalog {
	public record Item(String id, String name, int primary, int secondary, String primaryLabel, String secondaryLabel) {}

	public static final Item[] HATS = {
			new Item("tophat", "Top hat", 0x1C1C20, 0x8E2A35, "Hat", "Band"),
			new Item("crown", "Crown", 0xF2C14E, 0xD7263D, "Gold", "Gems"),
			new Item("beanie", "Beanie", 0x3D5A80, 0xE0FBFC, "Knit", "Cuff"),
			new Item("horns", "Horns", 0x2B1B17, 0xFF4A2E, "Horn", "Glow"),
			new Item("wizard", "Wizard hat", 0x3A2E6E, 0xF4D35E, "Hat", "Stars"),
	};

	public static final Item[] WINGS = {
			new Item("angel", "Angel", 0xF7F7FA, 0xC9D6EA, "Feathers", "Tips"),
			new Item("raven", "Raven", 0x18181D, 0x3B3363, "Feathers", "Sheen"),
			new Item("phoenix", "Phoenix", 0xFF5A1F, 0xFFD24A, "Flame", "Tips"),
			new Item("demon", "Demon", 0x5A0F16, 0x1A0B0D, "Skin", "Bones"),
			new Item("dragon", "Dragon", 0x2D6A4F, 0x173A2B, "Skin", "Bones"),
			new Item("bat", "Bat", 0x2A2A2E, 0x121214, "Skin", "Bones"),
			new Item("butterfly", "Butterfly", 0x3A86FF, 0x0B0B12, "Wings", "Edges"),
			new Item("fairy", "Fairy", 0xBDE8FF, 0xF6F1FF, "Wings", "Veins"),
			new Item("crystal", "Crystal", 0x7FDBFF, 0xC77DFF, "Crystal", "Glow"),
			new Item("mech", "Mechanical", 0x9AA1A9, 0xC9862E, "Plates", "Joints"),
	};

	public static final Item[] AURAS = {
			new Item("orbit", "Orbit", 0x9BE7FF, 0xFFFFFF, "Glow", "Core"),
			new Item("halo", "Halo", 0xFFE08A, 0xFFFFFF, "Glow", "Core"),
			new Item("embers", "Embers", 0xFF7A2F, 0xFFD166, "Glow", "Sparks"),
			new Item("rings", "Rings", 0xB794F6, 0xFFFFFF, "Glow", "Core"),
			new Item("spiral", "Spiral", 0x6EE7B7, 0xFFFFFF, "Glow", "Core"),
			new Item("drift", "Drift", 0x8FD3FF, 0xF2F4FF, "Motes", "Ring"),
			new Item("prism", "Prism", 0xFF6B9A, 0x7AF0FF, "Ring", "Ring fade"),
	};

	private Catalog() {}

	public static Item find(Item[] items, String id) {
		for (Item i : items) if (i.id().equals(id)) return i;
		return null;
	}

	public static int index(Item[] items, String id) {
		for (int i = 0; i < items.length; i++) if (items[i].id().equals(id)) return i;
		return -1;
	}
}
