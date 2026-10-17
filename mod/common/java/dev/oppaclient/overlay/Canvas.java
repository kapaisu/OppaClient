package dev.oppaclient.overlay;

import java.util.Arrays;

public final class Canvas {
	private final boolean additive;
	private float[] xy = new float[512];
	private int[] colors = new int[256];
	private int count;
	private float alpha = 1f;

	private Canvas(boolean additive) {
		this.additive = additive;
	}

	public static Canvas flat() {
		return new Canvas(false);
	}

	public static Canvas light() {
		return new Canvas(true);
	}

	public boolean additive() {
		return additive;
	}

	public int count() {
		return count;
	}

	public float[] xy() {
		return xy;
	}

	public int[] colors() {
		return colors;
	}

	public void clear() {
		count = 0;
	}

	public static Canvas of(boolean additive) {
		return additive ? light() : flat();
	}

	public Canvas alpha(float alpha) {
		this.alpha = alpha;
		return this;
	}

	public boolean isEmpty() {
		return count == 0;
	}

	public void vertex(float x, float y, int argb) {
		if (count * 2 + 2 > xy.length) {
			xy = Arrays.copyOf(xy, xy.length * 2);
			colors = Arrays.copyOf(colors, colors.length * 2);
		}
		xy[count * 2] = x;
		xy[count * 2 + 1] = y;
		colors[count] = alpha >= 1f ? argb : scaleAlpha(argb, alpha);
		count++;
	}

	public void quad(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
		vertex(x0, y0, c0);
		vertex(x1, y1, c1);
		vertex(x2, y2, c2);
		vertex(x3, y3, c3);
	}

	public void triangle(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2) {
		quad(x0, y0, c0, x1, y1, c1, x2, y2, c2, x2, y2, c2);
	}

	public void rect(float x0, float y0, float x1, float y1, int argb) {
		quad(x0, y0, argb, x0, y1, argb, x1, y1, argb, x1, y0, argb);
	}

	public void gradient(float x0, float y0, float x1, float y1, int topLeft, int topRight, int bottomLeft, int bottomRight) {
		quad(x0, y0, topLeft, x0, y1, bottomLeft, x1, y1, bottomRight, x1, y0, topRight);
	}

	public void roundRect(float x0, float y0, float x1, float y1, float radius, int argb) {
		float r = Math.min(radius, Math.min(x1 - x0, y1 - y0) * 0.5f);
		if (r <= 0.25f) {
			rect(x0, y0, x1, y1, argb);
			return;
		}
		rect(x0 + r, y0, x1 - r, y1, argb);
		rect(x0, y0 + r, x0 + r, y1 - r, argb);
		rect(x1 - r, y0 + r, x1, y1 - r, argb);
		corner(x0 + r, y0 + r, r, 180f, argb);
		corner(x1 - r, y0 + r, r, 270f, argb);
		corner(x1 - r, y1 - r, r, 0f, argb);
		corner(x0 + r, y1 - r, r, 90f, argb);
	}

	private void corner(float cx, float cy, float r, float startDegrees, int argb) {
		int segments = Math.max(3, (int) Math.ceil(r * 1.5f));
		double start = Math.toRadians(startDegrees);
		double step = Math.PI / 2 / segments;
		for (int i = 0; i < segments; i++) {
			double a0 = start + step * i;
			double a1 = a0 + step;
			triangle(cx, cy, argb,
					cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r, argb,
					cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, argb);
		}
	}

	public void roundOutline(float x0, float y0, float x1, float y1, float radius, float thickness, int argb) {
		float r = Math.min(radius, Math.min(x1 - x0, y1 - y0) * 0.5f);
		float[] outer = ring(x0, y0, x1, y1, r);
		float[] inner = ring(x0 + thickness, y0 + thickness, x1 - thickness, y1 - thickness, Math.max(0f, r - thickness));
		int n = outer.length / 2;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			quad(outer[i * 2], outer[i * 2 + 1], argb, outer[j * 2], outer[j * 2 + 1], argb,
					inner[j * 2], inner[j * 2 + 1], argb, inner[i * 2], inner[i * 2 + 1], argb);
		}
	}

	public static float[] ring(float x0, float y0, float x1, float y1, float r) {
		int perCorner = 8;
		float[] out = new float[perCorner * 4 * 2];
		float[][] centers = {{x0 + r, y0 + r}, {x1 - r, y0 + r}, {x1 - r, y1 - r}, {x0 + r, y1 - r}};
		int k = 0;
		for (int c = 0; c < 4; c++) {
			double start = Math.toRadians(180 + 90 * c);
			for (int i = 0; i < perCorner; i++) {
				double a = start + Math.PI / 2 * i / (perCorner - 1);
				out[k++] = centers[c][0] + (float) Math.cos(a) * r;
				out[k++] = centers[c][1] + (float) Math.sin(a) * r;
			}
		}
		return out;
	}

	private static int scaleAlpha(int argb, float factor) {
		int a = Math.round(((argb >>> 24) & 0xFF) * factor);
		return (Math.clamp(a, 0, 255) << 24) | (argb & 0xFFFFFF);
	}
}
