package dev.oppaclient;

import dev.oppaclient.platform.Gfx;
import dev.oppaclient.platform.Platform;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;

public final class Icons {
	private static final Map<String, Identifier> CACHE = new HashMap<>();
	private static int next;

	private Icons() {}

	public static void draw(Gfx g, String key, Pixels src, float x, float y, float size, float alpha) {
		if (src == null) return;
		float s = Platform.guiScale();
		int px = Math.max(1, Math.round(size * s));
		Identifier tex = CACHE.computeIfAbsent(key + "@" + px, k -> {
			Identifier id = OppaClient.id("icon/" + next++);
			Platform.upload(id, px, px, rounded(resample(src, px), px));
			return id;
		});
		g.push();
		g.scale(1 / s, 1 / s);
		g.blit(tex, Math.round(x * s), Math.round(y * s), px, px, 0, 0, px, px, Paint.argb(alpha, 0xFFFFFF));
		g.pop();
	}

	public static void forget(String key) {
		CACHE.keySet().removeIf(k -> k.startsWith(key + "@"));
	}

	private static int[] resample(Pixels src, int size) {
		int[] out = new int[size * size];
		double sx = src.width() / (double) size, sy = src.height() / (double) size;
		for (int y = 0; y < size; y++) {
			int y0 = (int) (y * sy), y1 = Math.max(y0 + 1, (int) Math.ceil((y + 1) * sy));
			for (int x = 0; x < size; x++) {
				int x0 = (int) (x * sx), x1 = Math.max(x0 + 1, (int) Math.ceil((x + 1) * sx));
				double a = 0, r = 0, gr = 0, b = 0;
				int n = 0;
				for (int yy = y0; yy < Math.min(y1, src.height()); yy++) {
					for (int xx = x0; xx < Math.min(x1, src.width()); xx++) {
						int c = src.at(xx, yy);
						double ca = (c >>> 24) / 255.0;
						a += ca;
						r += ((c >> 16) & 0xFF) * ca;
						gr += ((c >> 8) & 0xFF) * ca;
						b += (c & 0xFF) * ca;
						n++;
					}
				}
				if (a > 0) {
					out[y * size + x] = (int) Math.round(a / n * 255) << 24 | (int) Math.round(r / a) << 16
							| (int) Math.round(gr / a) << 8 | (int) Math.round(b / a);
				}
			}
		}
		return out;
	}

	private static int[] rounded(int[] px, int size) {
		double r = size * 0.2;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				double dx = Math.max(0, Math.max(r - (x + 0.5), (x + 0.5) - (size - r)));
				double dy = Math.max(0, Math.max(r - (y + 0.5), (y + 0.5) - (size - r)));
				double cover = Math.max(0, Math.min(1, r + 0.5 - Math.hypot(dx, dy)));
				if (dx == 0 && dy == 0) continue;
				int c = px[y * size + x];
				int a = (int) Math.round((c >>> 24) * cover);
				px[y * size + x] = a << 24 | (c & 0xFFFFFF);
			}
		}
		return px;
	}
}
