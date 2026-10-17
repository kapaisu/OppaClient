package dev.oppaclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

public final class Zoom {
	private static final Paint.Smooth level = new Paint.Smooth(1, 16);
	private static float scrolled = 1f;
	private static boolean held;
	private static boolean cinematicBefore;

	private Zoom() {}

	public static void tick(MinecraftClient mc, KeyBinding key) {
		Config cfg = Config.get();
		boolean now = cfg.zoom && key.isPressed() && mc.currentScreen == null;
		if (now != held) {
			held = now;
			scrolled = 1f;
			if (cfg.zoomCinematic) {
				if (now) {
					cinematicBefore = mc.options.smoothCameraEnabled;
					mc.options.smoothCameraEnabled = true;
				} else {
					mc.options.smoothCameraEnabled = cinematicBefore;
				}
			}
		}
	}

	public static float fov(float fov) {
		Config cfg = Config.get();
		float target = held ? Math.max(1f, cfg.zoomFactor * scrolled) : 1f;
		level.to(target);
		float f = cfg.zoomSmooth ? level.get() : target;
		if (!cfg.zoomSmooth) level.snap(target);
		return f <= 1.0001f ? fov : fov / f;
	}

	public static boolean scroll(double amount) {
		if (!held || !Config.get().zoomScroll || amount == 0) return false;
		scrolled = (float) Math.max(0.25, Math.min(8, scrolled * (amount > 0 ? 1.15 : 1 / 1.15)));
		return true;
	}
}
