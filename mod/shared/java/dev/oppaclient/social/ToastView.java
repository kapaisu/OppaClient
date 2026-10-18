package dev.oppaclient.social;

import dev.oppaclient.Paint;
import dev.oppaclient.Type;
import dev.oppaclient.platform.Gfx;

import java.util.List;

public final class ToastView {
	private ToastView() {}

	public static void draw(Gfx g, int screenWidth, int top) {
		List<Toasts.Toast> list = Toasts.current();
		if (list.isEmpty()) return;
		int y = top;
		for (int i = list.size() - 1; i >= 0; i--) {
			Toasts.Toast t = list.get(i);
			float a = Toasts.fade(t);
			if (a <= 0) continue;
			int pad = 9;
			int w = Math.max(Type.STRONG.width(t.title()), t.detail().isEmpty() ? 0 : Type.SMALL.width(t.detail())) + pad * 2;
			int h = t.detail().isEmpty() ? 21 : 32;
			int x = screenWidth - w - 8 + Math.round((1 - a) * 14);
			Paint.round(g, x, y, w, h, 8, Paint.shade(0.82f * a));
			Type.STRONG.draw(g, t.title(), x + pad, y + 6, Paint.ink(0.95f * a));
			if (!t.detail().isEmpty()) Type.SMALL.draw(g, t.detail(), x + pad, y + 18, Paint.ink(0.6f * a));
			y += h + 6;
		}
	}
}
