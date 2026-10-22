package dev.oppaclient.look;

public final class Hats {
	private Hats() {}

	private static float strength = 1f;

	public static void build(Mesh m, Look look, float time) {
		int a = Mesh.opaque(look.primary()), b = Mesh.opaque(look.secondary());
		strength = look.glow() * 2f;
		m.push();
		m.translate(0, -look.lift(), 0);
		switch (look.style()) {
			case "crown" -> crown(m, a, b, time);
			case "tophat" -> topHat(m, a, b, time);
			case "beanie" -> beanie(m, a, b, time);
			case "horns" -> horns(m, a, b, time);
			case "wizard" -> wizard(m, a, b, time);
			default -> {}
		}
		m.pop();
		m.pen(Mesh.SOLID);
	}

	private static float noise(float x, float y, float z) {
		int h = Float.floatToIntBits(x * 7.31f + y * 3.17f + z * 5.03f) * 0x27D4EB2D;
		h ^= h >>> 15;
		return ((h & 0xFF) / 255f - 0.5f) * 0.12f;
	}

	private static void block(Mesh m, float x0, float y0, float z0, float x1, float y1, float z1, int c) {
		int top = Mesh.shade(c, 1.08f + noise(x0, y0, z0)), side = Mesh.shade(c, 1f + noise(x1, y0, z1)), under = Mesh.shade(c, 0.78f);
		m.box(x0, y0, z0, x1, y1, z1, top, side, under);
	}

	private static void halo(Mesh m, float x, float y, float z, float r, int c, float alpha) {
		float k = alpha * strength;
		if (k <= 0.01f) return;
		m.halo(x, y, z, r * 1.1f, Mesh.alpha(Mesh.mix(c, 0xFFFFFFFF, 0.35f), Math.min(1, k)));
		m.halo(x, y, z, r * 2.6f, Mesh.alpha(c, Math.min(1, k * 0.45f)));
	}

	private static void gem(Mesh m, float x, float y, float z, float s, int c, float pulse) {
		m.pen(Mesh.BRIGHT);
		block(m, x - s, y - s, z - s, x + s, y + s, z + s, Mesh.mix(c, 0xFFFFFFFF, 0.15f));
		m.pen(Mesh.SOLID);
		halo(m, x, y, z, s * 5.5f, c, 0.55f * pulse);
	}

	private static void sparkle(Mesh m, float x, float y, float z, float s, int c, float fade) {
		if (fade <= 0.02f) return;
		m.pen(Mesh.BRIGHT);
		float k = s * fade;
		m.box(x - k, y - k, z - k, x + k, y + k, z + k, Mesh.mix(c, 0xFFFFFFFF, 0.5f));
		m.pen(Mesh.SOLID);
		halo(m, x, y, z, k * 5, c, 0.5f * fade);
	}

	private static float pulse(float t, float offset) {
		return 0.7f + 0.3f * Shapes.sin(t * 2.4f + offset);
	}

	private static void crown(Mesh m, int gold, int gem, float t) {
		int deep = Mesh.shade(gold, 0.72f), bright = Mesh.mix(gold, 0xFFFFFFFF, 0.25f);
		float o = 4.85f, i = 4.1f, y0 = -7.4f, y1 = -9.4f;
		block(m, -o, y1, -o, o, y0, -i, gold);
		block(m, -o, y1, i, o, y0, o, gold);
		block(m, -o, y1, -i, -i, y0, i, gold);
		block(m, i, y1, -i, o, y0, i, gold);
		block(m, -o - 0.15f, y0 - 0.55f, -o - 0.15f, o + 0.15f, y0, -o + 0.5f, deep);
		block(m, -o - 0.15f, y0 - 0.55f, o - 0.5f, o + 0.15f, y0, o + 0.15f, deep);
		block(m, -o - 0.15f, y0 - 0.55f, -o + 0.5f, -o + 0.5f, y0, o - 0.5f, deep);
		block(m, o - 0.5f, y0 - 0.55f, -o + 0.5f, o + 0.15f, y0, o - 0.5f, deep);
		float[] spots = {-o + 0.75f, 0, o - 0.75f};
		int n = 0;
		for (int side = 0; side < 4; side++) {
			for (float s : spots) {
				float x, z;
				switch (side) {
					case 0 -> { x = s; z = -o + 0.375f; }
					case 1 -> { x = s; z = o - 0.375f; }
					case 2 -> { x = -o + 0.375f; z = s; }
					default -> { x = o - 0.375f; z = s; }
				}
				if (side >= 2 && Math.abs(s) > 1) continue;
				boolean tall = s == 0;
				float h = tall ? 2.6f : 1.6f;
				block(m, x - 0.75f, y1 - h * 0.55f, z - 0.375f, x + 0.75f, y1, z + 0.375f, gold);
				block(m, x - 0.4f, y1 - h, z - 0.3f, x + 0.4f, y1 - h * 0.55f, z + 0.3f, bright);
				gem(m, x, y1 - h - 0.35f, z, 0.32f, tall ? gem : bright, pulse(t, n++));
			}
		}
		if (strength > 0.05f) {
			m.pen(Mesh.GLOW);
			int rim = Mesh.alpha(bright, Math.min(1, 0.35f * strength));
			int none = Mesh.alpha(bright, 0);
			for (int side = 0; side < 4; side++) {
				float ax = side < 2 ? -o : side == 2 ? -o : o, az = side == 0 ? -o : side == 1 ? o : -o;
				float bx = side < 2 ? o : side == 2 ? -o : o, bz = side == 0 ? -o : side == 1 ? o : o;
				float nx = side == 2 ? -1 : side == 3 ? 1 : 0, nz = side == 0 ? -1 : side == 1 ? 1 : 0;
				m.quad(ax, y1, az, bx, y1, bz, bx + nx * 0.8f, y1 - 0.9f, bz + nz * 0.8f, ax + nx * 0.8f, y1 - 0.9f, az + nz * 0.8f, rim, rim, none, none);
			}
			m.pen(Mesh.SOLID);
		}
		gem(m, 0, (y0 + y1) / 2, -o - 0.1f, 0.6f, gem, pulse(t, 0.5f));
		gem(m, 0, (y0 + y1) / 2, o + 0.1f, 0.6f, gem, pulse(t, 1.5f));
		gem(m, -o - 0.1f, (y0 + y1) / 2, 0, 0.6f, gem, pulse(t, 2.5f));
		gem(m, o + 0.1f, (y0 + y1) / 2, 0, 0.6f, gem, pulse(t, 3.5f));
		for (int k = 0; k < 3; k++) {
			float a = t * 0.8f + k * 2.094f;
			float life = (t * 0.35f + k * 0.33f) % 1f;
			sparkle(m, Shapes.cos(a) * 6.2f, -11.5f - life * 3, Shapes.sin(a) * 6.2f, 0.22f, bright, (float) Math.sin(Math.PI * life));
		}
	}

	private static void topHat(Mesh m, int hat, int band, float t) {
		int under = Mesh.shade(hat, 0.7f), rim = Mesh.mix(hat, 0xFFFFFFFF, 0.08f);
		block(m, -6, -9.1f, -6, 6, -8.5f, 6, rim);
		block(m, -5.6f, -8.5f, -5.6f, 5.6f, -8.35f, 5.6f, under);
		block(m, -4.2f, -15.6f, -4.2f, 4.2f, -9.1f, 4.2f, hat);
		block(m, -4.45f, -15.85f, -4.45f, 4.45f, -15.4f, 4.45f, Mesh.mix(hat, 0xFFFFFFFF, 0.06f));
		block(m, -4.35f, -10.9f, -4.35f, 4.35f, -9.1f, 4.35f, band);
		float p = pulse(t, 0);
		m.pen(Mesh.BRIGHT);
		int line = Mesh.mix(band, 0xFFFFFFFF, 0.35f);
		block(m, -4.38f, -11.05f, -4.38f, 4.38f, -10.85f, -4.32f, line);
		block(m, -4.38f, -11.05f, 4.32f, 4.38f, -10.85f, 4.38f, line);
		block(m, -4.38f, -11.05f, -4.32f, -4.32f, -10.85f, 4.32f, line);
		block(m, 4.32f, -11.05f, -4.32f, 4.38f, -10.85f, 4.32f, line);
		int buckle = 0xFFF2C14E;
		block(m, -1.1f, -10.75f, -4.6f, 1.1f, -9.25f, -4.38f, buckle);
		m.pen(Mesh.SOLID);
		block(m, -0.55f, -10.3f, -4.65f, 0.55f, -9.7f, -4.55f, band);
		halo(m, 0, -10f, -4.7f, 2.2f, buckle, 0.35f * p);
		float life = (t * 0.4f) % 1f;
		sparkle(m, 3.6f, -15.2f - life * 1.5f, -4.4f, 0.2f, 0xFFFFFFFF, (float) Math.sin(Math.PI * life));
	}

	private static void beanie(Mesh m, int knit, int cuff, float t) {
		float o = 4.8f;
		int rows = 4;
		for (int r = 0; r < rows; r++) {
			float y1 = -6.4f - r * 0.75f, y0 = y1 - 0.75f;
			block(m, -o, y0, -o, o, y1, o, Mesh.shade(knit, r % 2 == 0 ? 1f : 0.88f));
		}
		block(m, -4.5f, -10.15f, -4.5f, 4.5f, -9.4f, 4.5f, Mesh.shade(knit, 1.04f));
		block(m, -3.6f, -10.85f, -3.6f, 3.6f, -10.15f, 3.6f, Mesh.shade(knit, 0.94f));
		block(m, -2.2f, -11.35f, -2.2f, 2.2f, -10.85f, 2.2f, Mesh.shade(knit, 1.02f));
		int cols = 12;
		float co = 5.05f;
		for (int c = 0; c < cols; c++) {
			float x0 = -co + 2 * co * c / cols, x1 = -co + 2 * co * (c + 1) / cols;
			int col = Mesh.shade(cuff, c % 2 == 0 ? 1f : 0.86f);
			block(m, x0, -6.4f, -co, x1, -4.6f, -co + 0.6f, col);
			block(m, x0, -6.4f, co - 0.6f, x1, -4.6f, co, col);
			block(m, -co, -6.4f, x0, -co + 0.6f, -4.6f, x1, col);
			block(m, co - 0.6f, -6.4f, x0, co, -4.6f, x1, col);
		}
		block(m, -1.6f, -13.3f, -1.6f, 1.6f, -11.35f, 1.6f, cuff);
		block(m, -1.25f, -13.6f, -1.25f, 1.25f, -13.3f, 1.25f, Mesh.mix(cuff, 0xFFFFFFFF, 0.3f));
		m.pen(Mesh.BRIGHT);
		int glow = Mesh.mix(cuff, 0xFFFFFFFF, 0.4f);
		float z = -co - 0.08f;
		block(m, -0.3f, -5.95f, z, 0.3f, -5.05f, z + 0.1f, glow);
		block(m, -0.75f, -5.8f, z, 0.75f, -5.2f, z + 0.1f, glow);
		m.pen(Mesh.SOLID);
		halo(m, 0, -5.5f, z - 0.1f, 1.8f, glow, 0.4f * pulse(t, 0));
	}

	private static void horns(Mesh m, int horn, int tip, float t) {
		float[][] seg = {
				{3.0f, -7.6f, -0.6f, 2.1f, 1.9f},
				{3.7f, -9.5f, -0.3f, 1.8f, 1.5f},
				{4.45f, -10.9f, 0.2f, 1.5f, 1.35f},
				{5.2f, -12.15f, 0.9f, 1.25f, 1.2f},
				{5.9f, -13.25f, 1.7f, 1.0f, 1.05f},
				{6.45f, -14.2f, 2.6f, 0.75f, 0.9f}};
		for (int side = -1; side <= 1; side += 2) {
			for (int i = 0; i < seg.length; i++) {
				float[] s = seg[i];
				float u = i / (seg.length - 1f);
				int c = Mesh.mix(horn, tip, u * u);
				float x = s[0] * side, half = s[3] / 2;
				if (i >= seg.length - 2) m.pen(Mesh.BRIGHT);
				block(m, x - half, s[1] - s[4], s[2] - half, x + half, s[1], s[2] + half, c);
				m.pen(Mesh.SOLID);
				if (i < seg.length - 2) {
					block(m, x - half - 0.02f, s[1] - 0.35f, s[2] - half - 0.02f, x + half + 0.02f, s[1] - 0.2f, s[2] + half + 0.02f, Mesh.shade(horn, 0.7f));
				}
			}
			float[] end = seg[seg.length - 1];
			float p = pulse(t, side);
			halo(m, end[0] * side, end[1] - end[4], end[2], 2.4f, tip, 0.5f * p);
			for (int k = 0; k < 3; k++) {
				float life = (t * 0.6f + k * 0.33f + (side > 0 ? 0.15f : 0)) % 1f;
				float drift = Shapes.sin(t * 3 + k * 2 + side) * 0.4f;
				sparkle(m, end[0] * side + drift, end[1] - end[4] - 0.4f - life * 3.2f, end[2] + drift * 0.5f, 0.18f, tip, (float) Math.sin(Math.PI * life));
			}
		}
	}

	private static void wizard(Mesh m, int hat, int star, float t) {
		int under = Mesh.shade(hat, 0.68f);
		block(m, -6.6f, -9.0f, -6.6f, 6.6f, -8.4f, 6.6f, hat);
		block(m, -6.2f, -8.4f, -6.2f, 6.2f, -8.3f, 6.2f, under);
		float[][] steps = {
				{4.4f, 1.6f, 0, 0}, {3.75f, 1.5f, 0.1f, 0.25f}, {3.1f, 1.45f, 0.2f, 0.7f}, {2.45f, 1.35f, 0.35f, 1.3f},
				{1.8f, 1.25f, 0.55f, 2.0f}, {1.2f, 1.1f, 0.85f, 2.85f}, {0.7f, 1.0f, 1.2f, 3.7f}, {0.35f, 0.9f, 1.6f, 4.6f}};
		float y = -9.0f;
		for (int i = 0; i < steps.length; i++) {
			float[] s = steps[i];
			float yTop = y - s[1];
			block(m, s[2] - s[0], yTop, s[3] - s[0], s[2] + s[0], y, s[3] + s[0], Mesh.mix(hat, Mesh.shade(hat, 1.3f), i / (float) steps.length));
			y = yTop;
		}
		block(m, -4.5f, -10.2f, -4.5f, 4.5f, -9.0f, 4.5f, star);
		float[][] stars = {{-2.4f, -11.3f, -3.25f}, {2.0f, -13.0f, -2.2f}, {-0.6f, -15.0f, -1.0f}, {3.4f, -11.2f, 1.2f}, {-3.0f, -12.9f, 1.4f}};
		for (int i = 0; i < stars.length; i++) {
			float[] s = stars[i];
			float tw = 0.55f + 0.45f * Shapes.sin(t * 3.1f + i * 1.7f);
			m.pen(Mesh.BRIGHT);
			int c = Mesh.mix(star, 0xFFFFFFFF, 0.2f);
			block(m, s[0] - 0.45f, s[1] - 0.15f, s[2] - 0.05f, s[0] + 0.45f, s[1] + 0.15f, s[2] + 0.05f, c);
			block(m, s[0] - 0.15f, s[1] - 0.45f, s[2] - 0.05f, s[0] + 0.15f, s[1] + 0.45f, s[2] + 0.05f, c);
			m.pen(Mesh.SOLID);
			halo(m, s[0], s[1], s[2] - 0.1f, 1.5f, star, 0.45f * tw);
		}
		float tipY = y, tipX = steps[steps.length - 1][2], tipZ = steps[steps.length - 1][3];
		for (int k = 0; k < 4; k++) {
			float a = t * 1.3f + k * 1.57f;
			float life = (t * 0.45f + k * 0.25f) % 1f;
			sparkle(m, tipX + Shapes.cos(a) * 2.2f, tipY + 1.5f + life * 3, tipZ + Shapes.sin(a) * 2.2f, 0.2f, star, (float) Math.sin(Math.PI * life));
		}
	}
}
