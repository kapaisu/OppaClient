package dev.oppaclient.look;

import java.util.HashMap;
import java.util.Map;

public final class Wings {
	private static final int LIGHT = 1, BASE = 2, DARK = 3, MID = 4, ACCENT = 5, BONE = 6, CLEAR = 7, SHINE = 8, HOT = 9, SHADE = 10;
	private static final Map<String, Sprite> sprites = new HashMap<>();
	private static final int[] colors = new int[11];
	private static final byte[] pens = new byte[11];

	private record Shape(float cell, float depth, int root, int wrist, float rest, float open, float beat, float tilt) {}

	private Wings() {}

	private static Shape shape(String style) {
		return switch (style) {
			case "angel", "raven" -> new Shape(0.62f, 0.7f, 6, 13, 0.6f, 0.22f, 1f, 0.12f);
			case "phoenix" -> new Shape(0.62f, 0.7f, 6, 13, 0.58f, 0.2f, 1.1f, 0.12f);
			case "demon", "bat" -> new Shape(0.6f, 0.5f, 5, 12, 0.62f, 0.25f, 1f, 0.1f);
			case "dragon" -> new Shape(0.66f, 0.55f, 5, 12, 0.62f, 0.25f, 0.85f, 0.1f);
			case "butterfly" -> new Shape(0.7f, 0.4f, 12, 30, 0.55f, 0.3f, 2.2f, 0.05f);
			case "fairy" -> new Shape(0.66f, 0.35f, 14, 30, 0.5f, 0.28f, 2.6f, 0.05f);
			case "crystal" -> new Shape(0.8f, 0.9f, 10, 30, 0.55f, 0.3f, 0.7f, 0.08f);
			case "mech" -> new Shape(0.6f, 0.9f, 4, 12, 0.6f, 0.22f, 0.8f, 0.12f);
			default -> null;
		};
	}

	public static void build(Mesh m, Look look, float time, float phase, float pace) {
		if (look.none()) return;
		String style = look.style();
		Shape shape = shape(style);
		if (shape == null) return;
		Sprite sprite = sprites.computeIfAbsent(style, Wings::sprite);
		palette(style, Mesh.opaque(look.primary()), Mesh.opaque(look.secondary()));

		float beat = phase * shape.beat;
		float wave = (float) Math.sin(beat), lag = (float) Math.sin(beat - 0.9f);
		float amp = 0.05f + 0.3f * pace;
		float sweep = shape.rest + (shape.open - shape.rest) * pace + amp * 0.5f * wave;
		float up = 0.08f + 0.12f * pace + amp * wave;
		float fold = (0.28f - 0.22f * pace) + amp * 0.7f * lag;
		float breathe = 0.02f * (float) Math.sin(time * 1.3f);

		float cell = shape.cell;
		float oy = -shape.root * cell;
		for (int side = -1; side <= 1; side += 2) {
			m.push();
			m.translate(side * 1.2f, 2.4f, 2.3f);
			m.rotateX(-shape.tilt);
			m.rotateZ(-side * (up + breathe));
			m.rotateY(-side * sweep);
			sprite.draw(m, colors, pens, cell, shape.depth, 0, shape.wrist, 0, oy, side);
			float hinge = shape.wrist * cell;
			m.translate(side * hinge, 0, 0);
			m.rotateY(-side * fold);
			m.rotateZ(-side * fold * 0.25f);
			m.translate(-side * hinge, 0, 0);
			sprite.draw(m, colors, pens, cell, shape.depth, shape.wrist, sprite.w, 0, oy, side);
			m.pop();
		}
	}

	private static void palette(String style, int a, int b) {
		for (int i = 0; i < pens.length; i++) pens[i] = Mesh.SOLID;
		colors[LIGHT] = Mesh.mix(a, 0xFFFFFFFF, 0.14f);
		colors[BASE] = a;
		colors[DARK] = Mesh.shade(a, 0.72f);
		colors[MID] = Mesh.mix(a, b, 0.5f);
		colors[ACCENT] = b;
		colors[BONE] = Mesh.shade(b, 1.1f);
		colors[CLEAR] = Mesh.alpha(a, 0.55f);
		colors[SHINE] = Mesh.mix(b, 0xFFFFFFFF, 0.2f);
		colors[HOT] = Mesh.mix(a, b, 0.35f);
		colors[SHADE] = Mesh.shade(a, 0.84f);
		pens[CLEAR] = Mesh.CLEAR;
		pens[SHINE] = Mesh.BRIGHT;
		pens[HOT] = Mesh.BRIGHT;
		switch (style) {
			case "angel", "raven" -> colors[BONE] = Mesh.mix(a, 0xFFFFFFFF, 0.24f);
			case "demon", "dragon", "bat" -> {
				colors[LIGHT] = Mesh.mix(a, 0xFFFFFFFF, 0.08f);
				colors[BONE] = b;
				colors[ACCENT] = Mesh.mix(b, 0xFFE8E2D0, 0.55f);
			}
			case "butterfly" -> {
				colors[LIGHT] = Mesh.mix(a, 0xFFFFFFFF, 0.3f);
				colors[MID] = Mesh.mix(a, 0xFFFFFFFF, 0.85f);
			}
			case "fairy" -> {
				colors[SHINE] = Mesh.mix(b, 0xFFFFFFFF, 0.3f);
				colors[CLEAR] = Mesh.alpha(Mesh.mix(a, b, 0.2f), 0.5f);
				colors[LIGHT] = Mesh.alpha(Mesh.mix(a, 0xFFFFFFFF, 0.45f), 0.62f);
				pens[LIGHT] = Mesh.CLEAR;
			}
			case "crystal" -> {
				colors[LIGHT] = Mesh.mix(a, 0xFFFFFFFF, 0.35f);
				colors[SHINE] = Mesh.mix(b, a, 0.25f);
			}
			case "mech" -> colors[BONE] = Mesh.shade(a, 0.55f);
			default -> {}
		}
	}

	private static Sprite sprite(String style) {
		return switch (style) {
			case "angel" -> feathered(false, false);
			case "raven" -> feathered(true, false);
			case "phoenix" -> feathered(false, true);
			case "demon" -> membrane(4, 1f, false);
			case "dragon" -> membrane(4, 1.12f, true);
			case "bat" -> membrane(3, 0.82f, false);
			case "butterfly" -> butterfly();
			case "fairy" -> fairy();
			case "crystal" -> crystal();
			case "mech" -> mech();
			default -> new Sprite(1, 1);
		};
	}

	private static float armX(float t) {
		return 29f * t;
	}

	private static float armY(float t) {
		return 6f - 5.2f * (float) Math.sin(Math.PI * Math.min(1, t * 1.25f)) + 3.2f * t * t;
	}

	private static Sprite feathered(boolean ragged, boolean flame) {
		Sprite s = new Sprite(32, 30);
		int prim = 8;
		for (int i = prim - 1; i >= 0; i--) {
			float k = i / (prim - 1f);
			float t = 0.5f + 0.5f * k;
			float angle = (float) Math.toRadians(96 - 62 * Math.pow(k, 1.2));
			float len = 15 + 5 * (float) Math.sin(Math.PI * (0.25 + 0.6 * k)) - (ragged && i % 2 == 1 ? 2.5f : 0) + (flame ? 2.5f * k : 0);
			feather(s, armX(t), armY(t) + 1, angle, len, 1.9f, flame, i % 2 == 0 ? BASE : SHADE);
		}
		int sec = 7;
		for (int i = sec - 1; i >= 0; i--) {
			float k = i / (sec - 1f);
			float t = 0.03f + 0.47f * k;
			float angle = (float) Math.toRadians(100 - 6 * k);
			float len = 15.5f - 2.5f * k - (ragged && i % 3 == 1 ? 2f : 0) + (flame ? 1.5f : 0);
			feather(s, armX(t), armY(t) + 1, angle, len, 2.1f, flame, i % 2 == 0 ? SHADE : BASE);
		}
		for (int i = 0; i < 9; i++) {
			float t = 0.02f + 0.86f * i / 8f;
			float ex = armX(t) + 1.2f, ey = armY(t) + 4.6f + (i % 2) * 0.8f;
			s.capsule(armX(t), armY(t) + 0.5f, ex, ey, 2.0f, 1.5f, (a, d) -> a > 0.75f ? MID : LIGHT);
		}
		for (int i = 0; i < 24; i++) {
			float t0 = i / 24f, t1 = (i + 1) / 24f;
			s.line(armX(t0), armY(t0), armX(t1), armY(t1), 1.05f, BONE);
		}
		return s;
	}

	private static void feather(Sprite s, float bx, float by, float angle, float len, float r, boolean flame, int body) {
		float ex = bx + (float) Math.cos(angle) * len, ey = by + (float) Math.sin(angle) * len;
		s.capsule(bx, by, ex, ey, r, r * 0.55f, (t, d) -> {
			if (flame) return t > 0.82f ? SHINE : t > 0.55f ? HOT : body;
			return t > 0.86f ? ACCENT : t > 0.68f ? MID : body;
		});
	}

	private static Sprite membrane(int fingers, float scale, boolean dragon) {
		Sprite s = new Sprite(34, 32);
		float k = scale;
		float rx = 0, ry = 5, ex = 7 * k, ey = 1.5f * k + 1, wx = 13 * k, wy = 0;
		float[][] tips = fingers == 4
				? new float[][] {{30 * k, 2 * k}, {28 * k, 11 * k}, {23 * k, 20 * k}, {15 * k, 25 * k}}
				: new float[][] {{27 * k, 6 * k}, {22 * k, 16 * k}, {14 * k, 23 * k}};
		float bx = 2, by = 21 * k;
		float[][] edge = new float[tips.length + 1][];
		System.arraycopy(tips, 0, edge, 0, tips.length);
		edge[tips.length] = new float[] {bx, by};
		for (int f = 0; f + 1 < edge.length; f++) {
			float[] a = edge[f], b = edge[f + 1];
			float sag = f + 2 == edge.length ? 0.22f : 0.3f;
			float cx = (a[0] + b[0]) / 2, cy = (a[1] + b[1]) / 2;
			float nx = wx - cx, ny = wy - cy, nl = (float) Math.hypot(nx, ny);
			float chord = (float) Math.hypot(b[0] - a[0], b[1] - a[1]);
			s.triangle(wx, wy, a[0], a[1], b[0], b[1], (wrist, far) -> {
				float reach = 1 - wrist;
				float along = reach < 1e-4f ? 0.5f : far / reach;
				float cut = sag * chord * (float) Math.sin(Math.PI * along) / nl;
				if (reach > 1 - cut) return 0;
				return reach > 0.8f - cut ? DARK : reach > 0.42f ? BASE : LIGHT;
			});
		}
		s.triangle(rx, ry, wx, wy, bx, by, (u, w) -> LIGHT);
		s.triangle(rx, ry, ex, ey, wx, wy, (u, w) -> LIGHT);
		s.line(rx, ry, ex, ey, 1.2f, BONE);
		s.line(ex, ey, wx, wy, 1.0f, BONE);
		for (float[] t : tips) s.capsule(wx, wy, t[0], t[1], 0.85f, 0.45f, (a, d) -> BONE);
		if (dragon) {
			s.capsule(wx, wy, wx - 1.5f, wy - 3.5f, 0.9f, 0.3f, (a, d) -> ACCENT);
			for (int i = 0; i < 4; i++) {
				float t = 0.15f + 0.22f * i;
				float x = rx + (ex - rx) * t * 2, y = ry + (ey - ry) * t * 2;
				if (t > 0.5f) {
					x = ex + (wx - ex) * (t - 0.5f) * 2;
					y = ey + (wy - ey) * (t - 0.5f) * 2;
				}
				s.capsule(x, y - 0.5f, x + 0.4f, y - 2.4f, 0.7f, 0.2f, (a, d) -> ACCENT);
			}
		}
		return s;
	}

	private static Sprite butterfly() {
		Sprite s = new Sprite(26, 30);
		s.ellipse(9.5f, 7f, 9.5f, 6.5f, -0.65f, (d, a) -> d > 0.86f ? ACCENT : d > 0.55f ? LIGHT : BASE);
		s.ellipse(7f, 19f, 6.2f, 4.4f, 0.75f, (d, a) -> d > 0.82f ? ACCENT : d > 0.5f ? LIGHT : BASE);
		float[][] spots = {{14.5f, 3.6f, 1.3f}, {16.6f, 6.6f, 1f}, {11.5f, 2.6f, 0.9f}, {9.5f, 21.5f, 1f}};
		for (float[] p : spots) s.ellipse(p[0], p[1], p[2], p[2], 0, (d, a) -> MID);
		s.line(0.5f, 12, 13, 4.5f, 0.45f, DARK);
		s.line(0.5f, 12, 10, 10, 0.45f, DARK);
		s.line(0.5f, 12, 9, 20.5f, 0.45f, DARK);
		s.line(0, 11, 1, 13.5f, 0.8f, ACCENT);
		return s;
	}

	private static Sprite fairy() {
		Sprite s = new Sprite(26, 32);
		float[][] lobes = {{-0.72f, 21, 5.6f}, {0.62f, 13, 3.8f}};
		for (float[] l : lobes) {
			float c = (float) Math.cos(l[0]), sn = (float) Math.sin(l[0]);
			s.ellipse(0.5f + c * l[1] / 2, 12 + sn * l[1] / 2, l[1] / 2, l[2], l[0], (d, a) -> CLEAR);
		}
		s.outline(CLEAR, SHINE);
		for (float[] l : lobes) {
			float c = (float) Math.cos(l[0]), sn = (float) Math.sin(l[0]);
			s.line(0.5f, 12, 0.5f + c * l[1] * 0.78f, 12 + sn * l[1] * 0.78f, 0.3f, LIGHT);
			s.line(0.5f + c * l[1] * 0.35f, 12 + sn * l[1] * 0.35f, 0.5f + c * l[1] * 0.62f + sn * l[2] * 0.5f, 12 + sn * l[1] * 0.62f - c * l[2] * 0.5f, 0.3f, LIGHT);
		}
		return s;
	}

	private static Sprite crystal() {
		Sprite s = new Sprite(30, 30);
		float[][] shards = {{-1.05f, 13, 2.2f}, {-0.6f, 18, 2.7f}, {-0.15f, 16, 2.5f}, {0.32f, 12.5f, 2.2f}, {0.78f, 9, 1.8f}};
		for (float[] p : shards) {
			float c = (float) Math.cos(p[0]), sn = (float) Math.sin(p[0]);
			float bx = 1, by = 10, mx = bx + c * p[1] * 0.32f, my = by + sn * p[1] * 0.32f, tx = bx + c * p[1], ty = by + sn * p[1];
			float nx = -sn * p[2], ny = c * p[2];
			s.triangle(tx, ty, mx + nx, my + ny, mx - nx, my - ny, (tip, right) -> 1 - tip - right > right ? LIGHT : BASE);
			s.triangle(bx, by, mx + nx, my + ny, mx - nx, my - ny, (root, right) -> 1 - root - right > right ? LIGHT : BASE);
			s.line(bx + c * 2.5f, by + sn * 2.5f, bx + c * p[1] * 0.78f, by + sn * p[1] * 0.78f, 0.38f, SHINE);
		}
		s.ellipse(1.5f, 10, 2.4f, 2.4f, 0, (d, a) -> d < 0.6f ? SHINE : ACCENT);
		return s;
	}

	private static Sprite mech() {
		Sprite s = new Sprite(32, 30);
		int plates = 8;
		for (int i = plates - 1; i >= 0; i--) {
			float k = i / (plates - 1f);
			float t = 0.04f + 0.92f * k;
			float angle = (float) Math.toRadians(98 - 64 * Math.pow(k, 1.3));
			float len = 12 + 5 * (float) Math.sin(Math.PI * (0.3 + 0.6 * k));
			float bx = armX(t), by = armY(t) + 1;
			float ex = bx + (float) Math.cos(angle) * len, ey = by + (float) Math.sin(angle) * len;
			s.capsule(bx, by, ex, ey, 1.6f, 1.6f, (a, d) -> a > 0.9f ? MID : d > 0.75f ? DARK : a < 0.12f ? ACCENT : BASE);
		}
		for (int i = 0; i < 24; i++) {
			float t0 = i / 24f, t1 = (i + 1) / 24f;
			s.line(armX(t0), armY(t0), armX(t1), armY(t1), 1.25f, BONE);
		}
		for (int i = 0; i < 4; i++) {
			float t = 0.05f + 0.3f * i;
			s.ellipse(armX(t), armY(t), 1.3f, 1.3f, 0, (d, a) -> ACCENT);
		}
		return s;
	}
}
