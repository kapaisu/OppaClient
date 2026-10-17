package dev.oppaclient.menu;

import dev.oppaclient.Paint;
import dev.oppaclient.platform.Gfx;

final class Switch {
	static final int W = 20, H = 11;
	private final Paint.Smooth knob;

	Switch(boolean on) {
		knob = new Paint.Smooth(on ? 1 : 0, 22);
	}

	void draw(Gfx ctx, int x, int y, boolean on, float alpha) {
		float k = knob.to(on ? 1 : 0).get();
		Paint.round(ctx, x, y, W, H, H / 2f, Paint.ink(alpha * (0.16f + 0.76f * k)));
		float d = H - 4;
		float kx = x + 2 + (W - 4 - d) * k;
		int knobColor = k > 0.5f ? Paint.shade(alpha * 0.85f) : Paint.ink(alpha * 0.8f);
		Paint.round(ctx, kx, y + 2, d, d, d / 2, knobColor);
	}
}
