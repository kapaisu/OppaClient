package dev.oppaclient;

import dev.oppaclient.platform.Platform;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class Freelook {
	private static boolean active;
	private static boolean toggledOn;
	private static float yaw, pitch;
	private static CameraType before;

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

	static void tick(Minecraft mc, KeyMapping key) {
		Config cfg = Config.get();
		boolean want;
		if (cfg.freelookToggle) {
			while (key.consumeClick()) toggledOn = !toggledOn;
			want = toggledOn;
		} else {
			want = key.isDown();
		}
		want &= cfg.freelook && mc.player != null && Platform.screen() == null;
		if (want == active) return;
		active = want;
		if (active) {
			yaw = mc.player.getYRot();
			pitch = mc.player.getXRot();
			before = mc.options.getCameraType();
			if (before.isFirstPerson()) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		} else {
			toggledOn = false;
			if (before != null) mc.options.setCameraType(before);
		}
	}

	public static void turn(double dx, double dy) {
		yaw += (float) dx * 0.15f;
		pitch = Math.max(-90f, Math.min(90f, pitch + (float) dy * 0.15f));
	}
}
