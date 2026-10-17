package dev.oppaclient;

import dev.oppaclient.platform.Gfx;
import dev.oppaclient.platform.Platform;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class Paint {
	private Paint() {}

	public static int ink(float alpha) {
		return argb(alpha, 0xF2F2F2);
	}

	public static int shade(float alpha) {
		return argb(alpha, 0x000000);
	}

	public static int argb(float alpha, int rgb) {
		int a = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	private static float density;

	public static float density() {
		return density > 0 ? density : Platform.guiScale();
	}

	public static void density(float pixelsPerUnit) {
		density = pixelsPerUnit;
	}

	private static final Map<Integer, Identifier> DISCS = new HashMap<>();

	private static Identifier disc(int r) {
		return DISCS.computeIfAbsent(r, k -> {
			int d = r * 2;
			int[] px = new int[d * d];
			for (int y = 0; y < d; y++) {
				for (int x = 0; x < d; x++) {
					int hit = 0;
					for (int sy = 0; sy < 4; sy++) {
						for (int sx = 0; sx < 4; sx++) {
							double fx = x + (sx + 0.5) / 4 - r, fy = y + (sy + 0.5) / 4 - r;
							if (fx * fx + fy * fy <= r * (double) r) hit++;
						}
					}
					px[y * d + x] = (hit * 255 / 16) << 24 | 0xFFFFFF;
				}
			}
			Identifier id = OppaClient.id("disc/" + r);
			Platform.upload(id, d, d, px);
			return id;
		});
	}

	public static void round(Gfx g, float x, float y, float w, float h, float r, int color) {
		if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
		float s = density();
		int X = Math.round(x * s), Y = Math.round(y * s), W = Math.round(w * s), H = Math.round(h * s);
		int R = Math.min(Math.round(r * s), Math.min(W, H) / 2);
		g.push();
		g.scale(1 / s, 1 / s);
		if (R <= 0) {
			g.fill(X, Y, X + W, Y + H, color);
		} else {
			Identifier tex = disc(R);
			int d = R * 2;
			g.blit(tex, X, Y, R, R, 0, 0, d, d, color);
			g.blit(tex, X + W - R, Y, R, R, R, 0, d, d, color);
			g.blit(tex, X, Y + H - R, R, R, 0, R, d, d, color);
			g.blit(tex, X + W - R, Y + H - R, R, R, R, R, d, d, color);
			g.fill(X + R, Y, X + W - R, Y + R, color);
			g.fill(X, Y + R, X + W, Y + H - R, color);
			g.fill(X + R, Y + H - R, X + W - R, Y + H, color);
		}
		g.pop();
	}

	public static void hairline(Gfx g, float x0, float y0, float x1, float y1, int color) {
		float s = density();
		g.push();
		g.scale(1 / s, 1 / s);
		int ax = Math.round(x0 * s), ay = Math.round(y0 * s), bx = Math.round(x1 * s), by = Math.round(y1 * s);
		g.fill(ax, ay, Math.max(bx, ax + 1), Math.max(by, ay + 1), color);
		g.pop();
	}

	public static final class Smooth {
		private float value;
		private float target;
		private final float speed;
		private long last = System.nanoTime();

		public Smooth(float start, float speed) {
			this.value = start;
			this.target = start;
			this.speed = speed;
		}

		public Smooth to(float t) {
			target = t;
			return this;
		}

		public void snap(float v) {
			value = target = v;
		}

		public float get() {
			long now = System.nanoTime();
			float dt = Math.min(0.1f, (now - last) / 1e9f);
			last = now;
			value += (target - value) * (1 - (float) Math.exp(-speed * dt));
			if (Math.abs(target - value) < 0.001f) value = target;
			return value;
		}

		public float target() {
			return target;
		}
	}
}
