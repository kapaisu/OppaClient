package dev.oppaclient.look;

public final class Auras {
	private Auras() {}

	public static void build(Mesh m, Look look, float time) {
		if (look.none()) return;
		int glow = Mesh.opaque(look.primary()), core = Mesh.opaque(look.secondary());
		switch (look.style()) {
			case "orbit" -> orbit(m, glow, core, time);
			case "halo" -> halo(m, glow, core, time);
			case "embers" -> embers(m, glow, core, time);
			case "rings" -> rings(m, glow, core, time);
			case "spiral" -> spiral(m, glow, core, time);
			case "drift" -> drift(m, glow, core, core, time, false);
			case "prism" -> drift(m, 0, glow, core, time, true);
			default -> {}
		}
		m.pen(Mesh.SOLID);
	}

	private static void orb(Mesh m, float x, float y, float z, float r, int glow, int core) {
		m.pen(Mesh.BRIGHT);
		Shapes.sphere(m, x, y, z, r, Mesh.mix(core, glow, 0.25f), 4, 6);
		m.pen(Mesh.GLOW);
		Shapes.sphere(m, x, y, z, r * 2.1f, Mesh.alpha(glow, 0.3f), 4, 6);
	}

	private static void orbit(Mesh m, int glow, int core, float t) {
		for (int i = 0; i < 3; i++) {
			float phase = t * 1.6f + i * (float) (Math.PI * 2 / 3);
			for (int k = 0; k < 8; k++) {
				float a = phase - k * 0.13f;
				float x = Shapes.cos(a) * 11f, z = Shapes.sin(a) * 11f;
				float y = 6 + Shapes.sin(a * 2 + i * 2.1f) * 4;
				float fade = 1 - k / 8f;
				if (k == 0) {
					orb(m, x, y, z, 1.1f, glow, core);
				} else {
					m.pen(Mesh.GLOW);
					m.gem(x, y, z, 0.75f * fade, Mesh.alpha(glow, 0.7f * fade), Mesh.alpha(glow, 0.2f * fade));
				}
			}
		}
	}

	private static void band(Mesh m, float y, float r, float width, float height, int segments, int inner, int outer, float spin) {
		for (int i = 0; i < segments; i++) {
			double a = Math.PI * 2 * i / segments + spin, b = Math.PI * 2 * (i + 1) / segments + spin;
			float ca = Shapes.cos(a), sa = Shapes.sin(a), cb = Shapes.cos(b), sb = Shapes.sin(b);
			float ri = r - width / 2, ro = r + width / 2;
			m.quad(ca * ri, y, sa * ri, ca * r, y, sa * r, cb * r, y, sb * r, cb * ri, y, sb * ri, outer, inner, inner, outer);
			m.quad(ca * r, y, sa * r, ca * ro, y, sa * ro, cb * ro, y, sb * ro, cb * r, y, sb * r, inner, outer, outer, inner);
			if (height > 0) {
				m.quad(ca * r, y - height / 2, sa * r, ca * r, y, sa * r, cb * r, y, sb * r, cb * r, y - height / 2, sb * r, outer, inner, inner, outer);
				m.quad(ca * r, y, sa * r, ca * r, y + height / 2, sa * r, cb * r, y + height / 2, sb * r, cb * r, y, sb * r, inner, outer, outer, inner);
			}
		}
	}

	private static void hoop(Mesh m, float r, float thick, int segments, int c) {
		for (int i = 0; i < segments; i++) {
			double a = Math.PI * 2 * i / segments, b = Math.PI * 2 * (i + 1) / segments;
			float ca = Shapes.cos(a), sa = Shapes.sin(a), cb = Shapes.cos(b), sb = Shapes.sin(b);
			float ri = r - thick / 2, ro = r + thick / 2, h = thick / 2;
			m.quad(ca * ri, -h, sa * ri, ca * ro, -h, sa * ro, cb * ro, -h, sb * ro, cb * ri, -h, sb * ri, c);
			m.quad(ca * ri, h, sa * ri, cb * ri, h, sb * ri, cb * ro, h, sb * ro, ca * ro, h, sa * ro, c);
			m.quad(ca * ro, -h, sa * ro, ca * ro, h, sa * ro, cb * ro, h, sb * ro, cb * ro, -h, sb * ro, Mesh.shade(c, 0.9f));
			m.quad(ca * ri, h, sa * ri, ca * ri, -h, sa * ri, cb * ri, -h, sb * ri, cb * ri, h, sb * ri, Mesh.shade(c, 0.9f));
		}
	}

	private static void halo(Mesh m, int glow, int core, float t) {
		float bob = Shapes.sin(t * 1.8f) * 0.45f;
		m.push();
		m.translate(0, -12.8f + bob, 0);
		m.rotateX(0.22f);
		m.pen(Mesh.BRIGHT);
		hoop(m, 4.6f, 0.9f, 32, Mesh.mix(core, glow, 0.35f));
		m.pen(Mesh.GLOW);
		band(m, 0, 4.6f, 3.2f, 2.2f, 32, Mesh.alpha(glow, 0.55f), Mesh.alpha(glow, 0), 0);
		m.pop();
	}

	private static float hash(int i) {
		int h = i * 0x27D4EB2D;
		h ^= h >>> 15;
		h *= 0x165667B1;
		h ^= h >>> 13;
		return (h & 0xFFFF) / 65535f;
	}

	private static void embers(Mesh m, int glow, int spark, float t) {
		int count = 22;
		for (int i = 0; i < count; i++) {
			float speed = 0.22f + 0.2f * hash(i * 3);
			float life = (t * speed + hash(i)) % 1f;
			float ang = hash(i * 7) * (float) Math.PI * 2 + t * 0.4f;
			float rad = 6 + 5 * hash(i * 11);
			float x = Shapes.cos(ang) * rad + Shapes.sin(t * 2 + i) * 0.8f, z = Shapes.sin(ang) * rad;
			float y = 24 - life * 32;
			float fade = (float) Math.sin(Math.PI * life);
			int c = Mesh.mix(glow, spark, hash(i * 5));
			float size = 0.35f + 0.45f * fade;
			m.pen(Mesh.BRIGHT);
			m.gem(x, y, z, size, c, Mesh.mix(c, spark, 0.5f));
			m.pen(Mesh.GLOW);
			m.gem(x, y, z, size * 2.2f, Mesh.alpha(glow, 0.35f * fade), Mesh.alpha(glow, 0));
		}
		m.pen(Mesh.GLOW);
		band(m, 23.8f, 9f, 6f, 0, 28, Mesh.alpha(glow, 0.35f), Mesh.alpha(glow, 0), t * 0.2f);
	}

	private static void rings(Mesh m, int glow, int core, float t) {
		float[][] set = {{20.5f, 8.5f, 0.35f, 1.1f}, {8.5f, 9.8f, -0.45f, -0.8f}, {-3.0f, 7.2f, 0.3f, 1.5f}};
		for (float[] r : set) {
			m.push();
			m.translate(0, r[0] + Shapes.sin(t * 1.3f + r[0]) * 0.6f, 0);
			m.rotateX(0.25f + r[2] * Shapes.sin(t * 0.6f + r[1]));
			m.rotateZ(r[2] * 0.6f * Shapes.cos(t * 0.5f + r[1]));
			m.pen(Mesh.BRIGHT);
			hoop(m, r[1], 0.55f, 40, Mesh.mix(core, glow, 0.4f));
			m.pen(Mesh.GLOW);
			band(m, 0, r[1], 2.4f, 1.6f, 40, Mesh.alpha(glow, 0.5f), Mesh.alpha(glow, 0), t * r[3]);
			m.pop();
		}
	}

	private static void spiral(Mesh m, int glow, int core, float t) {
		int dots = 28;
		for (int strand = 0; strand < 2; strand++) {
			for (int i = 0; i < dots; i++) {
				float u = i / (float) dots;
				float a = u * (float) Math.PI * 5 + t * 2.2f + strand * (float) Math.PI;
				float y = 24 - u * 34;
				float r = 8f + 1.2f * Shapes.sin(u * 9 + t);
				float fade = (float) Math.sin(Math.PI * u);
				float x = Shapes.cos(a) * r, z = Shapes.sin(a) * r;
				m.pen(Mesh.BRIGHT);
				m.gem(x, y, z, 0.3f + 0.45f * fade, Mesh.mix(glow, core, fade * 0.6f), glow);
				m.pen(Mesh.GLOW);
				m.gem(x, y, z, 0.9f + 0.9f * fade, Mesh.alpha(glow, 0.35f * fade), Mesh.alpha(glow, 0));
			}
		}
	}

	private static int hue(float h) {
		h = h - (float) Math.floor(h);
		float r = Math.abs(h * 6 - 3) - 1, g = 2 - Math.abs(h * 6 - 2), b = 2 - Math.abs(h * 6 - 4);
		return 0xFF000000 | Math.round(Math.max(0, Math.min(1, r)) * 255) << 16 | Math.round(Math.max(0, Math.min(1, g)) * 255) << 8 | Math.round(Math.max(0, Math.min(1, b)) * 255);
	}

	private static void drift(Mesh m, int mote, int ring, int ringFade, float t, boolean spectrum) {
		int count = 18;
		for (int i = 0; i < count; i++) {
			float speed = 0.16f + 0.14f * hash(i * 13);
			float life = (t * speed + hash(i * 17)) % 1f;
			float ang = hash(i * 19) * (float) Math.PI * 2 + Shapes.sin(t * 0.5f + i) * 0.4f;
			float rad = 5.5f + 4.5f * hash(i * 23);
			float y = 24 - life * 34;
			float fade = (float) Math.sin(Math.PI * life);
			int c = spectrum ? hue(hash(i * 29) + t * 0.05f) : mote;
			float size = 0.28f + 0.3f * fade;
			float x = Shapes.cos(ang) * rad, z = Shapes.sin(ang) * rad;
			m.pen(Mesh.BRIGHT);
			m.gem(x, y, z, size, c, Mesh.mix(c, 0xFFFFFFFF, 0.4f));
			m.pen(Mesh.GLOW);
			m.gem(x, y, z, size * 2.6f, Mesh.alpha(c, 0.3f * fade), Mesh.alpha(c, 0));
		}
		float ride = 22.5f - 15f * (0.5f - 0.5f * Shapes.cos(t * 0.9f));
		int dashes = 10, seg = 4;
		float spin = t * 1.4f, r = 9.5f, gap = 0.35f;
		for (int d = 0; d < dashes; d++) {
			float start = (float) (Math.PI * 2 * d / dashes) + spin;
			float span = (float) (Math.PI * 2 / dashes) * (1 - gap);
			for (int k = 0; k < seg; k++) {
				float a = start + span * k / seg, b = start + span * (k + 1) / seg;
				float u = (float) (d * seg + k) / (dashes * seg);
				int c = spectrum ? hue(u + t * 0.08f) : Mesh.mix(ring, ringFade, 0.5f - 0.5f * Shapes.cos(u * (float) Math.PI * 2));
				float ca = Shapes.cos(a), sa = Shapes.sin(a), cb = Shapes.cos(b), sb = Shapes.sin(b);
				m.pen(Mesh.BRIGHT);
				m.quad(ca * (r - 0.3f), ride, sa * (r - 0.3f), ca * (r + 0.3f), ride, sa * (r + 0.3f), cb * (r + 0.3f), ride, sb * (r + 0.3f), cb * (r - 0.3f), ride, sb * (r - 0.3f), c);
				m.quad(ca * r, ride - 0.35f, sa * r, ca * r, ride + 0.35f, sa * r, cb * r, ride + 0.35f, sb * r, cb * r, ride - 0.35f, sb * r, c);
				m.pen(Mesh.GLOW);
				m.quad(ca * (r - 1.6f), ride, sa * (r - 1.6f), ca * r, ride, sa * r, cb * r, ride, sb * r, cb * (r - 1.6f), ride, sb * (r - 1.6f), Mesh.alpha(c, 0), Mesh.alpha(c, 0.5f), Mesh.alpha(c, 0.5f), Mesh.alpha(c, 0));
				m.quad(ca * r, ride, sa * r, ca * (r + 1.6f), ride, sa * (r + 1.6f), cb * (r + 1.6f), ride, sb * (r + 1.6f), cb * r, ride, sb * r, Mesh.alpha(c, 0.5f), Mesh.alpha(c, 0), Mesh.alpha(c, 0), Mesh.alpha(c, 0.5f));
			}
		}
	}
}
