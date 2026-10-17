package dev.oppaclient.hud;

import dev.oppaclient.Config;
import dev.oppaclient.Paint;
import dev.oppaclient.platform.Gfx;
import dev.oppaclient.platform.Platform;
import net.minecraft.client.MinecraftClient;

public final class Crosshair {
	private Crosshair() {}

	public static void draw(Gfx ctx, MinecraftClient mc) {
		Config c = Config.get();
		float s = Platform.guiScale();
		float u = Hud.REFERENCE_SCALE;
		int cx = mc.getWindow().getFramebufferWidth() / 2, cy = mc.getWindow().getFramebufferHeight() / 2;
		int size = Math.round(c.crosshairSize * u), gap = Math.round(c.crosshairGap * u);
		int t = Math.max(1, Math.round(c.crosshairThickness * u));
		int color = 0xFF000000 | c.crosshairColor;
		int outline = Paint.shade(0.55f);
		ctx.push();
		ctx.scale(1 / s, 1 / s);
		switch (c.crosshairStyle) {
			case "Dot" -> {
				int r = Math.max(1, t + 1);
				if (c.crosshairOutline) box(ctx, cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1, outline);
				box(ctx, cx - r, cy - r, cx + r, cy + r, color);
			}
			case "Square" -> {
				int r = gap + size / 2;
				if (c.crosshairOutline) frame(ctx, cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1, t + 2, outline);
				frame(ctx, cx - r, cy - r, cx + r, cy + r, t, color);
			}
			default -> {
				int h = t / 2;
				int[][] arms = {
						{cx - gap - size, cy - h, cx - gap, cy - h + t},
						{cx + gap, cy - h, cx + gap + size, cy - h + t},
						{cx - h, cy - gap - size, cx - h + t, cy - gap},
						{cx - h, cy + gap, cx - h + t, cy + gap + size}};
				if (c.crosshairOutline) {
					for (int[] a : arms) box(ctx, a[0] - 1, a[1] - 1, a[2] + 1, a[3] + 1, outline);
				}
				for (int[] a : arms) box(ctx, a[0], a[1], a[2], a[3], color);
				if (c.crosshairStyle.equals("Plus + dot")) box(ctx, cx - h, cy - h, cx - h + t, cy - h + t, color);
			}
		}
		ctx.pop();
	}

	private static void box(Gfx ctx, int x0, int y0, int x1, int y1, int color) {
		ctx.fill(x0, y0, x1, y1, color);
	}

	private static void frame(Gfx ctx, int x0, int y0, int x1, int y1, int t, int color) {
		box(ctx, x0, y0, x1, y0 + t, color);
		box(ctx, x0, y1 - t, x1, y1, color);
		box(ctx, x0, y0 + t, x0 + t, y1 - t, color);
		box(ctx, x1 - t, y0 + t, x1, y1 - t, color);
	}
}
