package dev.oppaclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;

public final class Freelook {
	private static boolean active;
	private static boolean toggledOn;
	private static float yaw, pitch;
	private static Perspective before;

	private Freelook() {}

	public static boolean active() {
		return active;
	}

	public static float yaw() {
		return yaw;
	}

	public static float pitch() {
		return pitch;
	}

	static void tick(MinecraftClient mc, KeyBinding key) {
		Config cfg = Config.get();
		boolean want;
		if (cfg.freelookToggle) {
			while (key.wasPressed()) toggledOn = !toggledOn;
			want = toggledOn;
		} else {
			want = key.isPressed();
		}
		want &= cfg.freelook && mc.player != null && mc.currentScreen == null;
		if (want == active) return;
		active = want;
		if (active) {
			yaw = mc.player.getYaw();
			pitch = mc.player.getPitch();
			before = mc.options.getPerspective();
			if (before.isFirstPerson()) mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		} else {
			toggledOn = false;
			if (before != null) mc.options.setPerspective(before);
		}
	}

	public static void turn(double dx, double dy) {
		yaw += (float) dx * 0.15f;
		pitch = Math.max(-90f, Math.min(90f, pitch + (float) dy * 0.15f));
	}
}
