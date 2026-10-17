package dev.oppaclient;

import dev.oppaclient.platform.Gfx;
import dev.oppaclient.platform.Platform;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class Logo {
	private static final Identifier SOURCE = OppaClient.id("textures/gui/logo.png");
	private static final Map<Integer, Identifier> SIZES = new HashMap<>();
	private static Pixels source;
	private static boolean tried;

	private Logo() {}

	public static float aspect() {
		Pixels src = source();
		return src == null ? 3.34f : src.width() / (float) src.height();
	}

	private static Pixels source() {
		if (!tried) {
			tried = true;
			source = Pixels.load(SOURCE);
		}
		return source;
	}

	public static void draw(Gfx g, float x, float y, float height, float alpha) {
		Pixels src = source();
		if (src == null || alpha <= 0.01f) return;
		float s = Platform.guiScale();
		int h = Math.max(1, Math.round(height * s));
		int w = Math.round(h * aspect());
		Identifier tex = SIZES.computeIfAbsent(h, k -> resample(src, w, h));
		g.push();
		g.scale(1 / s, 1 / s);
		g.blit(tex, Math.round(x * s), Math.round(y * s), w, h, 0, 0, w, h, Paint.argb(alpha, 0xFFFFFF));
		g.pop();
	}

	private static Identifier resample(Pixels src, int w, int h) {
		int[] out = new int[w * h];
		double sx = src.width() / (double) w, sy = src.height() / (double) h;
		for (int y = 0; y < h; y++) {
			int y0 = (int) (y * sy), y1 = Math.max(y0 + 1, (int) Math.ceil((y + 1) * sy));
			for (int x = 0; x < w; x++) {
				int x0 = (int) (x * sx), x1 = Math.max(x0 + 1, (int) Math.ceil((x + 1) * sx));
				double a = 0, r = 0, g = 0, b = 0;
				int n = 0;
				for (int yy = y0; yy < Math.min(y1, src.height()); yy++) {
					for (int xx = x0; xx < Math.min(x1, src.width()); xx++) {
						int c = src.at(xx, yy);
						double ca = (c >>> 24) / 255.0;
						a += ca;
						r += ((c >> 16) & 0xFF) * ca;
						g += ((c >> 8) & 0xFF) * ca;
						b += (c & 0xFF) * ca;
						n++;
					}
				}
				if (a > 0) {
					out[y * w + x] = (int) Math.round(a / n * 255) << 24
							| (int) Math.round(r / a) << 16
							| (int) Math.round(g / a) << 8
							| (int) Math.round(b / a);
				}
			}
		}
		Identifier id = OppaClient.id("logo/" + h);
		Platform.upload(id, w, h, out);
		return id;
	}
}
