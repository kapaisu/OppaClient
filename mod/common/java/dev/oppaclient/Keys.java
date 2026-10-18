package dev.oppaclient;

import dev.oppaclient.menu.LayoutScreen;
import dev.oppaclient.menu.MenuScreen;
import dev.oppaclient.platform.Platform;
import dev.oppaclient.social.Shots;
import dev.oppaclient.social.Toasts;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class Keys {
	public static final KeyBinding MENU = bind("menu", GLFW.GLFW_KEY_RIGHT_SHIFT);
	public static final KeyBinding ZOOM = bind("zoom", GLFW.GLFW_KEY_C);
	public static final KeyBinding FREELOOK = bind("freelook", GLFW.GLFW_KEY_LEFT_ALT);
	public static final KeyBinding LAYOUT = bind("layout", InputUtil.UNKNOWN_KEY.getCode());
	public static final KeyBinding HUD = bind("hud", InputUtil.UNKNOWN_KEY.getCode());
	public static final KeyBinding FULLBRIGHT = bind("fullbright", InputUtil.UNKNOWN_KEY.getCode());
	public static final KeyBinding SHARE = bind("share", GLFW.GLFW_KEY_K);

	public record Labeled(String label, KeyBinding key) {}

	public static final List<Labeled> ALL = List.of(
			new Labeled("Open menu", MENU),
			new Labeled("Zoom", ZOOM),
			new Labeled("Free look", FREELOOK),
			new Labeled("Edit layout", LAYOUT),
			new Labeled("Hide HUD", HUD),
			new Labeled("Fullbright", FULLBRIGHT),
			new Labeled("Share screenshot", SHARE));

	private Keys() {}

	private static KeyBinding bind(String name, int key) {
		return Platform.key("key.oppaclient." + name, key);
	}

	static void register() {
		for (Labeled l : ALL) KeyBindingHelper.registerKeyBinding(l.key());
	}

	static void tick(MinecraftClient mc) {
		Config cfg = Config.get();
		while (MENU.wasPressed()) {
			if (mc.currentScreen == null) mc.setScreen(new MenuScreen(Toasts.has(Toasts.Kind.INVITE) || Toasts.has(Toasts.Kind.REQUEST) ? MenuScreen.FRIENDS : -1));
		}
		while (SHARE.wasPressed()) {
			Shots.share(Shots.latest(), url -> mc.execute(() -> mc.keyboard.setClipboard(url)));
		}
		while (LAYOUT.wasPressed()) {
			if (mc.currentScreen == null) mc.setScreen(new LayoutScreen(null));
		}
		while (HUD.wasPressed()) {
			cfg.hudHidden = !cfg.hudHidden;
			cfg.save();
		}
		while (FULLBRIGHT.wasPressed()) {
			cfg.fullbright = !cfg.fullbright;
			cfg.save();
		}
		Zoom.tick(mc, ZOOM);
		Freelook.tick(mc, FREELOOK);
	}
}
