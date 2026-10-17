package dev.oppaclient;

import dev.oppaclient.platform.Gfx;
import dev.oppaclient.platform.Platform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public enum Type {
	UI("ui", 9, 9),
	SMALL("small", 8, 9),
	STRONG("strong", 9, 9),
	TITLE("title", 12, 20),
	HERO("hero", 24, 40);

	private final String id;

	public final int cap;

	public final int line;
	private final Style[] fonts = new Style[6];
	@SuppressWarnings("unchecked")
	private final Map<String, Text>[] texts = new Map[7];
	@SuppressWarnings("unchecked")
	private final Map<String, Integer>[] widths = new Map[7];

	Type(String id, int cap, int line) {
		this.id = id;
		this.cap = cap;
		this.line = line;
		for (int s = 1; s <= 6; s++) {
			fonts[s - 1] = Platform.font(Identifier.of(OppaClient.ID, id + "_" + s));
		}
	}

	private boolean vanilla() {
		return !Config.get().oppaFont && (this == UI || this == SMALL || this == STRONG);
	}

	private int slot() {
		return vanilla() ? 0 : Math.max(1, Math.min(6, Math.round(Paint.density())));
	}

	private static <V> Map<String, V> table(Map<String, V>[] tables, int slot) {
		Map<String, V> m = tables[slot];
		if (m == null) {
			m = new HashMap<>();
			tables[slot] = m;
		} else if (m.size() > 512) {
			m.clear();
		}
		return m;
	}

	public Text of(String s) {
		int slot = slot();
		return table(texts, slot).computeIfAbsent(s, k -> {
			if (slot == 0) return this == STRONG ? Text.literal(k).styled(st -> st.withBold(true)) : Text.literal(k);
			return Text.literal(k).setStyle(fonts[slot - 1]);
		});
	}

	public int width(String s) {
		return table(widths, slot()).computeIfAbsent(s, k -> MinecraftClient.getInstance().textRenderer.getWidth(of(k)));
	}

	public void draw(Gfx ctx, String s, int x, int y, int argb) {
		draw(ctx, s, x, y, argb, false);
	}

	public void draw(Gfx ctx, String s, int x, int y, int argb, boolean shadow) {
		if ((argb >>> 24) < 5) return;
		ctx.text(of(s), x, y, argb, shadow);
	}

	public void drawRight(Gfx ctx, String s, int right, int y, int argb) {
		draw(ctx, s, right - width(s), y, argb);
	}

	public void drawCentered(Gfx ctx, String s, int cx, int y, int argb) {
		draw(ctx, s, cx - width(s) / 2, y, argb);
	}
}
