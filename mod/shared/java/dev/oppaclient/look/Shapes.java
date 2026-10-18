package dev.oppaclient.look;

final class Shapes {
	private Shapes() {}

	static float cos(double a) {
		return (float) Math.cos(a);
	}

	static float sin(double a) {
		return (float) Math.sin(a);
	}

	static float squircle(double a, float exponent) {
		if (exponent <= 2.01f) return 1;
		double c = Math.abs(Math.cos(a)), s = Math.abs(Math.sin(a));
		return (float) (1 / Math.pow(Math.pow(c, exponent) + Math.pow(s, exponent), 1 / exponent));
	}

	static void loft(Mesh m, float[] ys, float[] rs, float[] dxs, float[] dzs, int[] colors, int segments, float exponent, float stripes) {
		for (int l = 0; l + 1 < ys.length; l++) {
			for (int i = 0; i < segments; i++) {
				double a = Math.PI * 2 * i / segments, b = Math.PI * 2 * (i + 1) / segments;
				float ka = squircle(a, exponent), kb = squircle(b, exponent);
				float ax0 = cos(a) * ka * rs[l] + dxs[l], az0 = sin(a) * ka * rs[l] + dzs[l];
				float bx0 = cos(b) * kb * rs[l] + dxs[l], bz0 = sin(b) * kb * rs[l] + dzs[l];
				float ax1 = cos(a) * ka * rs[l + 1] + dxs[l + 1], az1 = sin(a) * ka * rs[l + 1] + dzs[l + 1];
				float bx1 = cos(b) * kb * rs[l + 1] + dxs[l + 1], bz1 = sin(b) * kb * rs[l + 1] + dzs[l + 1];
				float f = stripes > 0 && (i & 1) == 0 ? 1 - stripes : 1;
				int c0 = Mesh.shade(colors[l], f), c1 = Mesh.shade(colors[l + 1], f);
				m.quad(ax0, ys[l], az0, ax1, ys[l + 1], az1, bx1, ys[l + 1], bz1, bx0, ys[l], bz0, c0, c1, c1, c0);
			}
		}
	}

	static void sphere(Mesh m, float cx, float cy, float cz, float r, int c, int rings, int segments) {
		for (int j = 0; j < rings; j++) {
			double p0 = Math.PI * j / rings - Math.PI / 2, p1 = Math.PI * (j + 1) / rings - Math.PI / 2;
			float y0 = sin(p0) * r, y1 = sin(p1) * r, r0 = cos(p0) * r, r1 = cos(p1) * r;
			for (int i = 0; i < segments; i++) {
				double a = Math.PI * 2 * i / segments, b = Math.PI * 2 * (i + 1) / segments;
				m.quad(cx + cos(a) * r0, cy + y0, cz + sin(a) * r0, cx + cos(a) * r1, cy + y1, cz + sin(a) * r1,
						cx + cos(b) * r1, cy + y1, cz + sin(b) * r1, cx + cos(b) * r0, cy + y0, cz + sin(b) * r0, c);
			}
		}
	}
}
