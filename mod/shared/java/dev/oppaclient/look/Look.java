package dev.oppaclient.look;

import java.util.Locale;

public record Look(String style, int primary, int secondary, float lift, float glow) {
	public static final Look NONE = new Look("", 0, 0);
	public static final float MAX_LIFT = 4f;
	public static final float GLOW = 0.5f;

	public Look(String style, int primary, int secondary) {
		this(style, primary, secondary, 0, GLOW);
	}

	public boolean none() {
		return style.isEmpty();
	}

	public static Look parse(String s, Catalog.Item[] items) {
		if (s == null || s.isEmpty()) return NONE;
		String[] parts = s.split(":");
		Catalog.Item item = Catalog.find(items, parts[0]);
		if (item == null) return NONE;
		int a = parts.length > 1 ? hex(parts[1], item.primary()) : item.primary();
		int b = parts.length > 2 ? hex(parts[2], item.secondary()) : item.secondary();
		float lift = parts.length > 3 ? number(parts[3], 0) / 2f : 0;
		float glow = parts.length > 4 ? number(parts[4], 5) / 10f : GLOW;
		return new Look(item.id(), a, b, Math.max(-MAX_LIFT, Math.min(MAX_LIFT, lift)), Math.max(0, Math.min(1, glow)));
	}

	private static int number(String s, int fallback) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static int hex(String s, int fallback) {
		try {
			return Integer.parseInt(s, 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	public String encode() {
		if (none()) return "";
		String base = String.format(Locale.ROOT, "%s:%06x:%06x", style, primary & 0xFFFFFF, secondary & 0xFFFFFF);
		int halves = Math.round(lift * 2), tenths = Math.round(glow * 10);
		if (tenths != Math.round(GLOW * 10)) return base + ":" + halves + ":" + tenths;
		return halves == 0 ? base : base + ":" + halves;
	}

	public Look primary(int c) {
		return new Look(style, c, secondary, lift, glow);
	}

	public Look secondary(int c) {
		return new Look(style, primary, c, lift, glow);
	}

	public Look lift(float l) {
		return new Look(style, primary, secondary, Math.max(-MAX_LIFT, Math.min(MAX_LIFT, l)), glow);
	}

	public Look glow(float g) {
		return new Look(style, primary, secondary, lift, Math.max(0, Math.min(1, g)));
	}
}
