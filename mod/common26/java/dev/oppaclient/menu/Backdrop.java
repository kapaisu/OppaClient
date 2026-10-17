package dev.oppaclient.menu;

import dev.oppaclient.OppaClient;
import dev.oppaclient.Pixels;
import dev.oppaclient.platform.Gfx;
import dev.oppaclient.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

final class Backdrop {
	private static final Identifier SOURCE = OppaClient.id("textures/gui/background.png");
	private static Pixels source;
	private static final Identifier SCALED = OppaClient.id("backdrop");
	private static boolean built;
	private static int builtW, builtH;

	private Backdrop() {}

	static void draw(Gfx g) {
		Minecraft mc = Minecraft.getInstance();
		int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
		if (w <= 0 || h <= 0 || !ensure(w, h)) return;
		float s = Platform.guiScale();
		g.push();
		g.scale(1 / s, 1 / s);
		g.blit(SCALED, 0, 0, w, h, 0, 0, w, h, 0xFFFFFFFF);
		g.pop();
	}

	private static boolean ensure(int w, int h) {
		if (built && builtW == w && builtH == h) return true;
		if (source == null) {
			source = Pixels.load(SOURCE);
			if (source == null) return false;
		}
		double scale = Math.max(w / (double) source.width(), h / (double) source.height());
		double ox = (source.width() * scale - w) / 2, oy = (source.height() * scale - h) / 2;
		int sw = source.width(), sh = source.height();
		int[] out = new int[w * h];
		for (int y = 0; y < h; y++) {
			double sy = Math.max(0, Math.min(sh - 1.001, (y + oy + 0.5) / scale - 0.5));
			int y0 = (int) sy;
			double fy = sy - y0;
			for (int x = 0; x < w; x++) {
				double sx = Math.max(0, Math.min(sw - 1.001, (x + ox + 0.5) / scale - 0.5));
				int x0 = (int) sx;
				double fx = sx - x0;
				int a = source.at(x0, y0), b = source.at(x0 + 1, y0);
				int c = source.at(x0, y0 + 1), d = source.at(x0 + 1, y0 + 1);
				int argb = 0xFF000000;
				for (int shift = 0; shift <= 16; shift += 8) {
					double top = ((a >> shift) & 0xFF) * (1 - fx) + ((b >> shift) & 0xFF) * fx;
					double bottom = ((c >> shift) & 0xFF) * (1 - fx) + ((d >> shift) & 0xFF) * fx;
					argb |= ((int) Math.round(top * (1 - fy) + bottom * fy) & 0xFF) << shift;
				}
				out[y * w + x] = argb;
			}
		}
		Platform.upload(SCALED, w, h, out);
		built = true;
		builtW = w;
		builtH = h;
		return true;
	}
}
