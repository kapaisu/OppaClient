package dev.oppaclient.hud;

import dev.oppaclient.platform.Platform;

import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;

final class Clicks {
	private static final ArrayDeque<Long> left = new ArrayDeque<>();
	private static final ArrayDeque<Long> right = new ArrayDeque<>();
	private static boolean leftDown, rightDown;

	private Clicks() {}

	static void poll(Minecraft mc) {
		long now = System.currentTimeMillis();
		if (Platform.screen() == null && mc.isWindowActive()) {
			boolean l = mc.mouseHandler.isLeftPressed();
			boolean r = mc.mouseHandler.isRightPressed();
			if (l && !leftDown) left.addLast(now);
			if (r && !rightDown) right.addLast(now);
			leftDown = l;
			rightDown = r;
		}
		while (!left.isEmpty() && now - left.peekFirst() > 1000) left.removeFirst();
		while (!right.isEmpty() && now - right.peekFirst() > 1000) right.removeFirst();
	}

	static int left() {
		return left.size();
	}

	static int right() {
		return right.size();
	}
}
