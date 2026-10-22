package dev.oppaclient.look;

public final class CapeArt {
	public static final int SCALE = 8;

	private CapeArt() {}

	public static Images.Image compose(Images.Image src, int cx, int cy, int cw, int ch) {
		int s = SCALE, w = 64 * s, h = 32 * s;
		int[] px = new int[w * h];
		Images.Image front = Images.resample(src, cx, cy, cw, ch, 10 * s, 16 * s);
		int base = opaqueAverage(front);
		int edge = Mesh.shade(base, 0.62f) | 0xFF000000;
		int inside = Mesh.shade(base, 0.45f) | 0xFF000000;
		fill(px, w, 0, 0, 22 * s, 17 * s, edge);
		paste(px, w, front, s, s, false);
		fill(px, w, 12 * s, s, 10 * s, 16 * s, inside);
		Images.Image wing = Images.resample(src, cx, cy, cw, ch, 10 * s, 20 * s);
		fill(px, w, 22 * s, 0, 24 * s, 22 * s, edge);
		paste(px, w, wing, 24 * s, 2 * s, false);
		paste(px, w, wing, 36 * s, 2 * s, true);
		return new Images.Image(w, h, px);
	}

	public static Images.Image fitElytra(Images.Image cape, Images.Image elytra) {
		if (elytra == null) return cape;
		int w = cape.width(), h = cape.height(), ew = elytra.width(), eh = elytra.height();
		int[] px = cape.argb().clone();
		for (int y = 0; y < h; y++) {
			int v = y * 32 / h;
			if (v >= 22) break;
			for (int x = 0; x < w; x++) {
				int u = x * 64 / w;
				if (u < 22 || u >= 46) continue;
				int mask = elytra.at(x * ew / w, y * eh / h) >>> 24;
				int c = px[y * w + x];
				px[y * w + x] = Math.min(c >>> 24, mask) << 24 | c & 0xFFFFFF;
			}
		}
		return new Images.Image(w, h, px);
	}

	private static int opaqueAverage(Images.Image img) {
		long r = 0, g = 0, b = 0, n = 0;
		for (int c : img.argb()) {
			if ((c >>> 24) < 128) continue;
			r += c >> 16 & 255;
			g += c >> 8 & 255;
			b += c & 255;
			n++;
		}
		if (n == 0) return 0xFF202024;
		return 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
	}

	private static void fill(int[] px, int stride, int x, int y, int w, int h, int c) {
		for (int j = y; j < y + h; j++) for (int i = x; i < x + w; i++) px[j * stride + i] = c;
	}

	private static void paste(int[] px, int stride, Images.Image img, int x, int y, boolean mirror) {
		for (int j = 0; j < img.height(); j++) {
			for (int i = 0; i < img.width(); i++) {
				int c = img.argb()[j * img.width() + (mirror ? img.width() - 1 - i : i)];
				int under = px[(y + j) * stride + x + i];
				px[(y + j) * stride + x + i] = over(c, under);
			}
		}
	}

	private static int over(int top, int under) {
		int a = top >>> 24;
		if (a == 255) return top;
		if (a == 0) return under;
		return Mesh.mix(under, top | 0xFF000000, a / 255f) | 0xFF000000;
	}
}
