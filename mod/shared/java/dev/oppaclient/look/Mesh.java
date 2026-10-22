package dev.oppaclient.look;

import java.util.Arrays;

public final class Mesh {
	public static final byte SOLID = 0, CLEAR = 1, GLOW = 2, BRIGHT = 3, HALO = 4;

	private float[] pos = new float[12 * 256];
	private float[] normal = new float[3 * 256];
	private int[] color = new int[4 * 256];
	private byte[] kind = new byte[256];
	private int quads;

	private final float[][] stack = new float[16][16];
	private int depth;
	private float[] m = stack[0];
	private byte pen = SOLID;

	public Mesh() {
		identity(m);
	}

	private static void identity(float[] a) {
		Arrays.fill(a, 0);
		a[0] = a[5] = a[10] = a[15] = 1;
	}

	public void clear() {
		quads = 0;
		depth = 0;
		m = stack[0];
		identity(m);
		pen = SOLID;
	}

	public int quads() {
		return quads;
	}

	public float[] positions() {
		return pos;
	}

	public float[] normals() {
		return normal;
	}

	public int[] colors() {
		return color;
	}

	public byte[] kinds() {
		return kind;
	}

	public Mesh pen(byte k) {
		pen = k;
		return this;
	}

	public byte pen() {
		return pen;
	}

	public void push() {
		float[] next = stack[++depth];
		System.arraycopy(m, 0, next, 0, 16);
		m = next;
	}

	public void pop() {
		m = stack[--depth];
	}

	private void mul(float a0, float a1, float a2, float a4, float a5, float a6, float a8, float a9, float a10, float tx, float ty, float tz) {
		float m0 = m[0], m1 = m[1], m2 = m[2], m4 = m[4], m5 = m[5], m6 = m[6], m8 = m[8], m9 = m[9], m10 = m[10];
		m[12] += m0 * tx + m4 * ty + m8 * tz;
		m[13] += m1 * tx + m5 * ty + m9 * tz;
		m[14] += m2 * tx + m6 * ty + m10 * tz;
		m[0] = m0 * a0 + m4 * a1 + m8 * a2;
		m[1] = m1 * a0 + m5 * a1 + m9 * a2;
		m[2] = m2 * a0 + m6 * a1 + m10 * a2;
		m[4] = m0 * a4 + m4 * a5 + m8 * a6;
		m[5] = m1 * a4 + m5 * a5 + m9 * a6;
		m[6] = m2 * a4 + m6 * a5 + m10 * a6;
		m[8] = m0 * a8 + m4 * a9 + m8 * a10;
		m[9] = m1 * a8 + m5 * a9 + m9 * a10;
		m[10] = m2 * a8 + m6 * a9 + m10 * a10;
	}

	public void translate(float x, float y, float z) {
		mul(1, 0, 0, 0, 1, 0, 0, 0, 1, x, y, z);
	}

	public void scale(float x, float y, float z) {
		mul(x, 0, 0, 0, y, 0, 0, 0, z, 0, 0, 0);
	}

	public void rotateX(float rad) {
		float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
		mul(1, 0, 0, 0, c, s, 0, -s, c, 0, 0, 0);
	}

	public void rotateY(float rad) {
		float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
		mul(c, 0, -s, 0, 1, 0, s, 0, c, 0, 0, 0);
	}

	public void rotateZ(float rad) {
		float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
		mul(c, s, 0, -s, c, 0, 0, 0, 1, 0, 0, 0);
	}

	private void grow() {
		int n = kind.length * 2;
		pos = Arrays.copyOf(pos, n * 12);
		normal = Arrays.copyOf(normal, n * 3);
		color = Arrays.copyOf(color, n * 4);
		kind = Arrays.copyOf(kind, n);
	}

	private void put(int at, float x, float y, float z) {
		pos[at] = m[0] * x + m[4] * y + m[8] * z + m[12];
		pos[at + 1] = m[1] * x + m[5] * y + m[9] * z + m[13];
		pos[at + 2] = m[2] * x + m[6] * y + m[10] * z + m[14];
	}

	public void quad(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
			int c0, int c1, int c2, int c3) {
		if (quads == kind.length) grow();
		int p = quads * 12;
		put(p, x0, y0, z0);
		put(p + 3, x1, y1, z1);
		put(p + 6, x2, y2, z2);
		put(p + 9, x3, y3, z3);
		float ax = pos[p + 6] - pos[p], ay = pos[p + 7] - pos[p + 1], az = pos[p + 8] - pos[p + 2];
		float bx = pos[p + 9] - pos[p + 3], by = pos[p + 10] - pos[p + 4], bz = pos[p + 11] - pos[p + 5];
		float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len < 1e-6f) len = 1;
		normal[quads * 3] = nx / len;
		normal[quads * 3 + 1] = ny / len;
		normal[quads * 3 + 2] = nz / len;
		color[quads * 4] = c0;
		color[quads * 4 + 1] = c1;
		color[quads * 4 + 2] = c2;
		color[quads * 4 + 3] = c3;
		kind[quads] = pen;
		quads++;
	}

	public void quad(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, int c) {
		quad(x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, c, c, c, c);
	}

	public void tri(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, int c0, int c1, int c2) {
		quad(x0, y0, z0, x1, y1, z1, x2, y2, z2, x2, y2, z2, c0, c1, c2, c2);
	}

	public void halo(float x, float y, float z, float r, int c) {
		if ((c >>> 24) < 3) return;
		byte was = pen;
		pen = HALO;
		quad(x, y, z, x, y, z, x, y, z, x, y, z, c);
		pen = was;
		normal[(quads - 1) * 3] = r * (float) Math.sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2]);
	}

	public void box(float x0, float y0, float z0, float x1, float y1, float z1, int c) {
		box(x0, y0, z0, x1, y1, z1, c, c, c);
	}

	public void box(float x0, float y0, float z0, float x1, float y1, float z1, int top, int side, int bottom) {
		quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, top);
		quad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, bottom);
		quad(x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, side);
		quad(x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, side);
		quad(x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, side);
		quad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, side);
	}

	public void disc(float y, float r, int segments, int c, boolean up) {
		for (int i = 0; i < segments; i += 2) {
			double a = Math.PI * 2 * i / segments, b = Math.PI * 2 * (i + 1) / segments, d = Math.PI * 2 * (i + 2) / segments;
			float ax = (float) Math.cos(a) * r, az = (float) Math.sin(a) * r;
			float bx = (float) Math.cos(b) * r, bz = (float) Math.sin(b) * r;
			float dx = (float) Math.cos(d) * r, dz = (float) Math.sin(d) * r;
			if (up) quad(0, y, 0, ax, y, az, bx, y, bz, dx, y, dz, c);
			else quad(0, y, 0, dx, y, dz, bx, y, bz, ax, y, az, c);
		}
	}

	public void gem(float x, float y, float z, float r, int c, int edge) {
		float[][] p = {{r, 0, 0}, {0, 0, r}, {-r, 0, 0}, {0, 0, -r}};
		for (int i = 0; i < 4; i++) {
			float[] a = p[i], b = p[(i + 1) % 4];
			tri(x + a[0], y, z + a[2], x, y - r * 1.3f, z, x + b[0], y, z + b[2], edge, c, edge);
			tri(x + b[0], y, z + b[2], x, y + r * 1.3f, z, x + a[0], y, z + a[2], edge, c, edge);
		}
	}

	public static int mix(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
		int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
		return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
	}

	public static int shade(int c, float f) {
		int r = Math.min(255, Math.round((c >> 16 & 255) * f)), g = Math.min(255, Math.round((c >> 8 & 255) * f)), b = Math.min(255, Math.round((c & 255) * f));
		return c & 0xFF000000 | r << 16 | g << 8 | b;
	}

	public static int alpha(int c, float a) {
		return Math.round(Math.max(0, Math.min(1, a)) * 255) << 24 | c & 0xFFFFFF;
	}

	public static int opaque(int rgb) {
		return 0xFF000000 | rgb;
	}
}
