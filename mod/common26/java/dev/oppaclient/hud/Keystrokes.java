package dev.oppaclient.hud;

import dev.oppaclient.Config;
import dev.oppaclient.Paint;
import dev.oppaclient.Type;
import dev.oppaclient.platform.Gfx;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

final class Keystrokes extends Element {
	private static final int KEY = 18, GAP = 2, WIDE = 28, BAR = 7;
	private final Map<KeyMapping, Paint.Smooth> fade = new HashMap<>();
	private final Map<String, String> labels = new HashMap<>();

	Keystrokes() {
		super("keys", "Keystrokes", false, 0, 1, 6, -6);
	}

	@Override
	public List<Option> options() {
		return List.of(
				new Toggle("mouse", "Mouse buttons", true),
				new Toggle("cps", "Clicks on mouse buttons", true),
				new Toggle("space", "Space bar", true));
	}

	@Override
	public int width(Minecraft mc, boolean preview) {
		return KEY * 3 + GAP * 2;
	}

	@Override
	public int height(Minecraft mc, boolean preview) {
		int h = KEY * 2 + GAP;
		if (on("mouse")) h += GAP + KEY;
		if (on("space")) h += GAP + BAR;
		return h;
	}

	@Override
	public void draw(Gfx ctx, Minecraft mc, boolean preview) {
		var o = mc.options;
		key(ctx, o.keyUp, null, KEY + GAP, 0, KEY, KEY);
		key(ctx, o.keyLeft, null, 0, KEY + GAP, KEY, KEY);
		key(ctx, o.keyDown, null, KEY + GAP, KEY + GAP, KEY, KEY);
		key(ctx, o.keyRight, null, (KEY + GAP) * 2, KEY + GAP, KEY, KEY);
		int row = (KEY + GAP) * 2;
		if (on("mouse")) {
			boolean cps = on("cps");
			key(ctx, o.keyAttack, cps ? Integer.toString(Clicks.left()) : "L", 0, row, WIDE, KEY);
			key(ctx, o.keyUse, cps ? Integer.toString(Clicks.right()) : "R", WIDE + GAP, row, WIDE, KEY);
			row += KEY + GAP;
		}
		if (on("space")) key(ctx, o.keyJump, "", 0, row, width(mc, preview), BAR);
	}

	private void key(Gfx ctx, KeyMapping bind, String override, int x, int y, int w, int h) {
		float p = fade.computeIfAbsent(bind, k -> new Paint.Smooth(0, 28)).to(bind.isDown() ? 1 : 0).get();
		int fill = p >= 0.999f ? Paint.ink(0.92f) : p <= 0.001f ? Paint.shade(0.38f) : blend(p);
		Paint.round(ctx, x, y, w, h, 4, fill);
		if (h < KEY) return;
		String label = override != null ? override : labels.computeIfAbsent(bind.saveString(), k -> shortName(bind));
		int color = p > 0.5f ? Paint.shade(0.9f) : Paint.ink(0.9f);
		Type.STRONG.draw(ctx, label, x + (w - Type.STRONG.width(label)) / 2, y + (h - 9) / 2 + 1, color, Config.get().shadow && p < 0.5f);
	}

	private static int blend(float p) {
		float a = 0.38f + (0.92f - 0.38f) * p;
		int c = Math.round(0xF2 * p);
		return Paint.argb(a, c << 16 | c << 8 | c);
	}

	private static String shortName(KeyMapping bind) {
		String s = bind.getTranslatedKeyMessage().getString();
		return s.length() <= 3 ? s.toUpperCase() : s.substring(0, 1).toUpperCase();
	}
}
