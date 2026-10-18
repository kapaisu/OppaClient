package dev.oppaclient.look;

import java.util.Locale;

public record Look(String style, int primary, int secondary, float lift) {
	public static final Look NONE = new Look("", 0, 0);
	public static final float MAX_LIFT = 4f;

	public Look(String style, int primary, int secondary) {
		this(style, primary, secondary, 0);
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
		float lift = 0;
		if (parts.length > 3) {
			try {
				lift = Math.max(-MAX_LIFT, Math.min(MAX_LIFT, Integer.parseInt(parts[3]) / 2f));
			} catch (NumberFormatException ignored) {
			}
		}
		return new Look(item.id(), a, b, lift);
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
		int halves = Math.round(lift * 2);
		return halves == 0 ? base : base + ":" + halves;
	}

	public Look primary(int c) {
		return new Look(style, c, secondary, lift);
	}

	public Look secondary(int c) {
		return new Look(style, primary, c, lift);
	}

	public Look lift(float l) {
		return new Look(style, primary, secondary, Math.max(-MAX_LIFT, Math.min(MAX_LIFT, l)));
	}
}
