package dev.oppaclient.hud;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

final class Clicks {
	private static final ArrayDeque<Long> left = new ArrayDeque<>();
	private static final ArrayDeque<Long> right = new ArrayDeque<>();
	private static boolean leftDown, rightDown;

	private Clicks() {}

	static void poll(MinecraftClient mc) {
		long now = System.currentTimeMillis();
		if (mc.currentScreen == null && mc.isWindowFocused()) {
			long handle = mc.getWindow().getHandle();
			boolean l = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
			boolean r = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
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
