package dev.oppaclient.look;

public final class Wings {
	private Wings() {}

	public static void build(Mesh m, Look look, float time, float pace) {
		if (look.none()) return;
		int a = Mesh.opaque(look.primary()), b = Mesh.opaque(look.secondary());
		String s = look.style();
		boolean flutter = s.equals("butterfly") || s.equals("fairy");
		float speed = flutter ? 2.6f + pace * 4f : 1.4f + pace * 5.5f;
		float wave = (float) Math.sin(time * speed);
		float spread = flutter ? 0.35f + 0.55f * (wave * 0.5f + 0.5f) : 0.42f + 0.22f * wave + pace * 0.1f;
		float lift = flutter ? 0.05f : 0.16f + 0.1f * wave;
		for (int side = -1; side <= 1; side += 2) {
			m.push();
			m.translate(side * 1.4f, 2.6f, 2.4f);
			m.scale(side, 1, 1);
			m.rotateY(-spread);
			m.rotateZ(-lift);
			switch (s) {
				case "angel" -> feathered(m, a, b, false, 1f);
				case "raven" -> feathered(m, a, b, true, 0.95f);
				case "phoenix" -> phoenix(m, a, b, time);
				case "demon" -> membrane(m, a, b, 4, 1.05f, false);
				case "dragon" -> membrane(m, a, b, 4, 1.3f, true);
				case "bat" -> membrane(m, a, b, 3, 0.92f, false);
				case "butterfly" -> butterfly(m, a, b);
				case "fairy" -> fairy(m, a, b);
				case "crystal" -> crystal(m, a, b, time);
				case "mech" -> mech(m, a, b);
				default -> {}
			}
			m.pop();
		}
	}

	private static void feather(Mesh m, float bx, float by, float z, float angle, float length, float width, int base, int tip) {
		float dx = Shapes.cos(angle), dy = Shapes.sin(angle);
		float px = -dy, py = dx;
		float w0 = width * 0.42f, w1 = width * 0.5f, w2 = width * 0.36f;
		float ax = bx + dx * length * 0.55f, ay = by + dy * length * 0.55f;
		float cx = bx + dx * length * 0.88f, cy = by + dy * length * 0.88f;
		float tx = bx + dx * length, ty = by + dy * length;
		int mid = Mesh.mix(base, tip, 0.45f), late = Mesh.mix(base, tip, 0.85f);
		m.quad(bx - px * w0, -(by - py * w0), z, bx + px * w0, -(by + py * w0), z, ax + px * w1, -(ay + py * w1), z, ax - px * w1, -(ay - py * w1), z, base, base, mid, mid);
		m.quad(ax - px * w1, -(ay - py * w1), z, ax + px * w1, -(ay + py * w1), z, cx + px * w2, -(cy + py * w2), z, cx - px * w2, -(cy - py * w2), z, mid, mid, late, late);
		m.tri(cx - px * w2, -(cy - py * w2), z, cx + px * w2, -(cy + py * w2), z, tx, -ty, z, late, late, tip);
	}

	private static float[] arm(float t) {
		float x = 15.5f * t;
		float y = 5.2f * (float) Math.sin(Math.PI * Math.min(1, t * 1.15f)) + 1.0f * t;
		return new float[] {x, y};
	}

	private static void feathered(Mesh m, int c, int tipColor, boolean ragged, float scale) {
		m.push();
		m.scale(scale, scale, 1);
		int under = Mesh.shade(c, 0.86f);
		int prim = 7;
		for (int i = 0; i < prim; i++) {
			float t = 0.55f + 0.45f * i / (prim - 1f);
			float[] p = arm(t);
			float angle = (float) Math.toRadians(-95 + 75 * (i / (prim - 1f)));
			float len = 8.5f + 3.5f * (float) Math.sin(Math.PI * (0.35 + 0.6 * i / (prim - 1f)));
			if (ragged && i % 2 == 1) len -= 1.4f;
			feather(m, p[0], p[1], -0.05f * i, angle, len, 3.4f, under, tipColor);
		}
		int sec = 8;
		for (int i = 0; i < sec; i++) {
			float t = 0.04f + 0.52f * i / (sec - 1f);
			float[] p = arm(t);
			float angle = (float) Math.toRadians(-100 + 8 * (i / (sec - 1f)));
			float len = 9.5f - 1.6f * i / (sec - 1f) + (ragged && i % 3 == 1 ? -1.2f : 0);
			feather(m, p[0], p[1], 0.2f - 0.02f * i, angle, len, 3.1f, c, Mesh.mix(c, tipColor, 0.7f));
		}
		int cov = 9;
		for (int i = 0; i < cov; i++) {
			float t = 0.02f + 0.8f * i / (cov - 1f);
			float[] p = arm(t);
			float angle = (float) Math.toRadians(-105 + 30 * (i / (cov - 1f)));
			feather(m, p[0], p[1] + 0.3f, 0.45f, angle, 3.8f + (i % 2) * 0.6f, 2.9f, Mesh.shade(c, 1.06f), c);
		}
		for (int i = 0; i < 12; i++) {
			float t0 = i / 12f, t1 = (i + 1) / 12f;
			float[] p0 = arm(t0), p1 = arm(t1);
			m.quad(p0[0], -p0[1] - 0.55f, 0.5f, p1[0], -p1[1] - 0.55f, 0.5f, p1[0], -p1[1] + 0.6f, 0.5f, p0[0], -p0[1] + 0.6f, 0.5f, Mesh.shade(c, 1.08f));
		}
		m.pop();
	}

	private static void phoenix(Mesh m, int flame, int tip, float time) {
		m.push();
		m.scale(1.05f, 1.05f, 1);
		int deep = Mesh.mix(flame, 0xFF5A0A0A, 0.35f);
		int n = 11;
		for (int i = 0; i < n; i++) {
			float t = 0.05f + 0.95f * i / (n - 1f);
			float[] p = arm(t);
			float flick = 0.06f * (float) Math.sin(time * 6 + i * 1.7f);
			float angle = (float) Math.toRadians(-100 + 85 * Math.pow(i / (n - 1f), 1.3)) + flick;
			float len = 9 + 6f * (float) Math.sin(Math.PI * (0.25 + 0.7 * i / (n - 1f)));
			m.pen(Mesh.SOLID);
			feather(m, p[0], p[1], 0.05f * i, angle, len * 0.82f, 2.4f, deep, flame);
			m.pen(Mesh.GLOW);
			feather(m, p[0], p[1], 0.05f * i + 0.12f, angle, len, 1.5f, Mesh.alpha(flame, 0.85f), Mesh.alpha(tip, 0.9f));
		}
		m.pen(Mesh.SOLID);
		for (int i = 0; i < 8; i++) {
			float t = 0.03f + 0.7f * i / 7f;
			float[] p = arm(t);
			feather(m, p[0], p[1] + 0.3f, 0.7f, (float) Math.toRadians(-100 + 25 * i / 7f), 3.8f, 2.4f, flame, Mesh.mix(flame, tip, 0.6f));
		}
		m.pop();
	}

	private static void limb(Mesh m, float x0, float y0, float x1, float y1, float r0, float r1, int c) {
		float dx = x1 - x0, dy = y1 - y0;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 1e-4f) return;
		float px = -dy / len, py = dx / len;
		int dark = Mesh.shade(c, 0.8f);
		float[][] p = {
				{x0 + px * r0, y0 + py * r0, r0}, {x1 + px * r1, y1 + py * r1, r1},
				{x1 - px * r1, y1 - py * r1, r1}, {x0 - px * r0, y0 - py * r0, r0}};
		m.quad(p[0][0], -p[0][1], r0, p[1][0], -p[1][1], r1, p[2][0], -p[2][1], r1, p[3][0], -p[3][1], r0, c);
		m.quad(p[3][0], -p[3][1], -r0, p[2][0], -p[2][1], -r1, p[1][0], -p[1][1], -r1, p[0][0], -p[0][1], -r0, dark);
		m.quad(p[0][0], -p[0][1], -r0, p[1][0], -p[1][1], -r1, p[1][0], -p[1][1], r1, p[0][0], -p[0][1], r0, c);
		m.quad(p[3][0], -p[3][1], r0, p[2][0], -p[2][1], r1, p[2][0], -p[2][1], -r1, p[3][0], -p[3][1], -r0, dark);
	}

	private static void membrane(Mesh m, int skin, int bone, int fingers, float scale, boolean dragon) {
		m.push();
		m.scale(scale, scale, 1);
		float[] root = {0, 0}, elbow = {5.5f, 4.2f}, wrist = {10.5f, 6.8f};
		float[][] tips = fingers == 4
				? new float[][] {{19.5f, 7.6f}, {18.5f, 0.5f}, {15.5f, -6.5f}, {10.5f, -10.5f}}
				: new float[][] {{17.5f, 5.0f}, {15.0f, -3.5f}, {10.0f, -9.0f}};
		float[] body = {0.6f, -9.5f};
		int light = Mesh.mix(skin, 0xFFFFFFFF, 0.12f), dark = Mesh.shade(skin, 0.75f);
		float[][] edge = new float[tips.length + 1][];
		System.arraycopy(tips, 0, edge, 0, tips.length);
		edge[tips.length] = body;
		for (int f = 0; f + 1 < edge.length; f++) {
			float[] a = edge[f], b = edge[f + 1];
			float mx = (a[0] + b[0]) / 2, my = (a[1] + b[1]) / 2;
			float sag = f + 2 == edge.length ? 0.32f : 0.24f;
			int steps = 6;
			for (int i = 0; i < steps; i++) {
				float t0 = (float) i / steps, t1 = (float) (i + 1) / steps;
				float s0 = sag * (float) Math.sin(Math.PI * t0), s1 = sag * (float) Math.sin(Math.PI * t1);
				float ex0 = a[0] + (b[0] - a[0]) * t0 + (wrist[0] - mx) * s0, ey0 = a[1] + (b[1] - a[1]) * t0 + (wrist[1] - my) * s0;
				float ex1 = a[0] + (b[0] - a[0]) * t1 + (wrist[0] - mx) * s1, ey1 = a[1] + (b[1] - a[1]) * t1 + (wrist[1] - my) * s1;
				m.tri(wrist[0], -wrist[1], 0, ex0, -ey0, 0, ex1, -ey1, 0, light, i == 0 && f > 0 ? light : dark, i + 1 == steps && f + 2 < edge.length ? light : dark);
			}
		}
		m.quad(root[0], -root[1], 0, elbow[0], -elbow[1], 0, wrist[0], -wrist[1], 0, body[0], -body[1], 0, light, light, light, dark);
		limb(m, root[0], root[1], elbow[0], elbow[1], 0.75f, 0.6f, bone);
		limb(m, elbow[0], elbow[1], wrist[0], wrist[1], 0.6f, 0.5f, bone);
		for (float[] t : tips) limb(m, wrist[0], wrist[1], t[0], t[1], 0.42f, 0.12f, bone);
		if (dragon) {
			int claw = Mesh.mix(bone, 0xFFE8E2D0, 0.5f);
			limb(m, wrist[0], wrist[1], wrist[0] + 1.2f, wrist[1] + 2.6f, 0.4f, 0.05f, claw);
			for (int i = 0; i < 4; i++) {
				float t = 0.2f + 0.2f * i;
				float x = elbow[0] * (1 - t) + wrist[0] * t, y = elbow[1] * (1 - t) + wrist[1] * t;
				limb(m, x, y, x + 0.3f, y + 1.3f, 0.28f, 0.02f, claw);
			}
		}
		m.pop();
	}

	private static void lobe(Mesh m, float from, float to, float reach, float roundness, int fill, int edge, float z) {
		int steps = 14;
		float inner = 0.8f;
		for (int i = 0; i < steps; i++) {
			double a0 = Math.toRadians(from + (to - from) * i / steps), a1 = Math.toRadians(from + (to - from) * (i + 1) / steps);
			float k0 = reach * (float) Math.pow(Math.sin(Math.PI * i / steps), roundness);
			float k1 = reach * (float) Math.pow(Math.sin(Math.PI * (i + 1) / steps), roundness);
			float x0 = Shapes.cos(a0) * k0, y0 = Shapes.sin(a0) * k0, x1 = Shapes.cos(a1) * k1, y1 = Shapes.sin(a1) * k1;
			m.tri(0, 0, z, x0 * inner, -y0 * inner, z, x1 * inner, -y1 * inner, z, Mesh.shade(fill, 0.8f), fill, fill);
			m.quad(x0 * inner, -y0 * inner, z, x0, -y0, z, x1, -y1, z, x1 * inner, -y1 * inner, z, edge);
		}
	}

	private static void butterfly(Mesh m, int fill, int edge) {
		lobe(m, -10, 95, 14, 0.55f, fill, edge, 0);
		lobe(m, -80, 5, 9.5f, 0.7f, Mesh.mix(fill, 0xFFFFFFFF, 0.15f), edge, 0.05f);
		int spot = Mesh.mix(fill, 0xFFFFFFFF, 0.75f);
		float[][] spots = {{9.5f, 7.5f, 0.9f}, {11.2f, 4.2f, 0.7f}, {7.2f, 9.6f, 0.6f}, {6.2f, -4.2f, 0.6f}};
		for (float[] s : spots) {
			m.push();
			m.translate(s[0], -s[1], 0.12f);
			m.rotateX((float) Math.PI / 2);
			m.disc(0, s[2], 8, spot, false);
			m.disc(0, s[2], 8, spot, true);
			m.pop();
		}
		limb(m, 0, 0, 0.5f, -0.5f, 0.35f, 0.3f, edge);
	}

	private static void fairy(Mesh m, int wing, int vein) {
		m.pen(Mesh.CLEAR);
		int fill = Mesh.alpha(wing, 0.62f), edge = Mesh.alpha(Mesh.mix(wing, 0xFFFFFFFF, 0.4f), 0.9f);
		float[][] leaves = {{58, 21, 3.0f}, {30, 16, 2.4f}, {-24, 12.5f, 2.0f}, {-48, 9.5f, 1.7f}};
		for (int i = 0; i < leaves.length; i++) {
			float[] l = leaves[i];
			m.push();
			m.rotateZ((float) Math.toRadians(-l[0]));
			leaf(m, l[1], l[2], fill, edge, 0.04f * i);
			m.pen(Mesh.GLOW);
			m.quad(0, -0.12f, 0.06f * i + 0.01f, l[1] * 0.9f, -0.04f, 0.06f * i + 0.01f, l[1] * 0.9f, 0.04f, 0.06f * i + 0.01f, 0, 0.12f, 0.06f * i + 0.01f, Mesh.alpha(vein, 0.7f));
			m.pen(Mesh.CLEAR);
			m.pop();
		}
		m.pen(Mesh.SOLID);
	}

	private static void leaf(Mesh m, float length, float width, int fill, int edge, float z) {
		int steps = 10;
		for (int i = 0; i < steps; i++) {
			float t0 = (float) i / steps, t1 = (float) (i + 1) / steps;
			float w0 = width * (float) Math.sin(Math.PI * Math.pow(t0, 0.8)), w1 = width * (float) Math.sin(Math.PI * Math.pow(t1, 0.8));
			m.quad(length * t0, -w0, z, length * t1, -w1, z, length * t1, w1, z, length * t0, w0, z, fill, fill, fill, fill);
			m.quad(length * t0, -w0 - 0.25f, z, length * t1, -w1 - 0.25f, z, length * t1, -w1, z, length * t0, -w0, z, edge);
			m.quad(length * t0, w0, z, length * t1, w1, z, length * t1, w1 + 0.25f, z, length * t0, w0 + 0.25f, z, edge);
		}
	}

	private static void shard(Mesh m, float angle, float length, float width, int base, int tip) {
		m.push();
		m.rotateZ(-angle);
		float w = width / 2, d = width * 0.35f;
		float[][] ring = {{0, -w}, {d, 0}, {0, w}, {-d, 0}};
		float neck = length * 0.18f;
		for (int i = 0; i < 4; i++) {
			float[] a = ring[i], b = ring[(i + 1) % 4];
			int shadeA = Mesh.shade(base, 0.85f + 0.08f * i);
			m.tri(0, 0, 0, neck, a[1], a[0], neck, b[1], b[0], Mesh.shade(base, 0.7f), shadeA, shadeA);
			m.tri(neck, b[1], b[0], neck, a[1], a[0], length, 0, 0, shadeA, shadeA, tip);
		}
		m.pop();
	}

	private static void crystal(Mesh m, int c, int glow, float time) {
		float[][] shards = {{-0.5f, 9, 2.0f}, {-0.2f, 13, 2.4f}, {0.1f, 17, 2.7f}, {0.38f, 20.5f, 2.9f}, {0.66f, 19, 2.8f}, {0.94f, 15.5f, 2.5f}, {1.2f, 11.5f, 2.1f}, {1.45f, 8, 1.7f}};
		float pulse = 0.75f + 0.25f * (float) Math.sin(time * 2.2f);
		for (float[] s : shards) {
			m.pen(Mesh.SOLID);
			shard(m, s[0], s[1], s[2], c, Mesh.mix(c, glow, 0.6f));
			m.pen(Mesh.GLOW);
			m.push();
			m.rotateZ(-s[0]);
			m.quad(1.5f, -0.18f, 0, s[1] * 0.92f, -0.05f, 0, s[1] * 0.92f, 0.05f, 0, 1.5f, 0.18f, 0, Mesh.alpha(glow, pulse));
			m.pop();
		}
		m.pen(Mesh.SOLID);
		m.gem(0, 0, 0, 1.1f, Mesh.mix(c, glow, 0.5f), glow);
	}

	private static void mech(Mesh m, int plate, int joint) {
		int dark = Mesh.shade(plate, 0.72f), light = Mesh.shade(plate, 1.12f);
		float[] root = {0, 0}, wrist = {9.5f, 6};
		limb(m, root[0], root[1], wrist[0], wrist[1], 0.7f, 0.6f, dark);
		limb(m, wrist[0], wrist[1], 16, 4, 0.55f, 0.35f, dark);
		int plates = 8;
		for (int i = 0; i < plates; i++) {
			float t = i / (plates - 1f);
			float bx, by;
			if (t < 0.55f) {
				float u = t / 0.55f;
				bx = root[0] + (wrist[0] - root[0]) * u;
				by = root[1] + (wrist[1] - root[1]) * u;
			} else {
				float u = (t - 0.55f) / 0.45f;
				bx = wrist[0] + (16 - wrist[0]) * u;
				by = wrist[1] + (4 - wrist[1]) * u;
			}
			float angle = (float) Math.toRadians(-98 + 70 * Math.pow(t, 1.4));
			float len = 7 + 5 * (float) Math.sin(Math.PI * (0.3 + 0.6 * t));
			m.push();
			m.translate(bx, -by, -0.1f * i);
			m.rotateZ(-angle);
			m.box(0.4f, -1.05f, -0.22f, len, 1.05f, 0.22f, light, plate, dark);
			m.box(len - 1.4f, -1.15f, -0.3f, len - 0.6f, 1.15f, 0.3f, joint);
			m.box(1.0f, -0.25f, 0.22f, 1.6f, 0.25f, 0.38f, joint);
			m.pop();
		}
		m.push();
		m.rotateX((float) Math.PI / 2);
		Shapes.loft(m, new float[] {-0.6f, 0.6f}, new float[] {1.5f, 1.5f}, new float[2], new float[2], new int[] {joint, Mesh.shade(joint, 0.8f)}, 10, 2, 0.15f);
		m.disc(-0.6f, 1.5f, 10, joint, true);
		m.disc(0.6f, 1.5f, 10, joint, false);
		m.pop();
		m.push();
		m.translate(wrist[0], -wrist[1], 0);
		m.rotateX((float) Math.PI / 2);
		Shapes.loft(m, new float[] {-0.5f, 0.5f}, new float[] {1.1f, 1.1f}, new float[2], new float[2], new int[] {joint, Mesh.shade(joint, 0.8f)}, 10, 2, 0.15f);
		m.disc(-0.5f, 1.1f, 10, joint, true);
		m.disc(0.5f, 1.1f, 10, joint, false);
		m.pop();
	}
}
