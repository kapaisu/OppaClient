package dev.oppaclient;

import com.mojang.blaze3d.platform.InputConstants;
import dev.oppaclient.menu.LayoutScreen;
import dev.oppaclient.menu.MenuScreen;
import dev.oppaclient.platform.Platform;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.List;

public final class Keys {
	public static final KeyMapping MENU = bind("menu", InputConstants.KEY_RSHIFT);
	public static final KeyMapping ZOOM = bind("zoom", InputConstants.KEY_C);
	public static final KeyMapping FREELOOK = bind("freelook", InputConstants.KEY_LALT);
	public static final KeyMapping LAYOUT = bind("layout", InputConstants.UNKNOWN.getValue());
	public static final KeyMapping HUD = bind("hud", InputConstants.UNKNOWN.getValue());
	public static final KeyMapping FULLBRIGHT = bind("fullbright", InputConstants.UNKNOWN.getValue());

	public record Labeled(String label, KeyMapping key) {}

	public static final List<Labeled> ALL = List.of(
			new Labeled("Open menu", MENU),
			new Labeled("Zoom", ZOOM),
			new Labeled("Free look", FREELOOK),
			new Labeled("Edit layout", LAYOUT),
			new Labeled("Hide HUD", HUD),
			new Labeled("Fullbright", FULLBRIGHT));

	private Keys() {}

	private static KeyMapping bind(String name, int key) {
		return Platform.key("key.oppaclient." + name, key);
	}

	static void register() {
		for (Labeled l : ALL) KeyMappingHelper.registerKeyMapping(l.key());
	}

	static void tick(Minecraft mc) {
		Config cfg = Config.get();
		while (MENU.consumeClick()) {
			if (Platform.screen() == null) Platform.show(new MenuScreen());
		}
		while (LAYOUT.consumeClick()) {
			if (Platform.screen() == null) Platform.show(new LayoutScreen(null));
		}
		while (HUD.consumeClick()) {
			cfg.hudHidden = !cfg.hudHidden;
			cfg.save();
		}
		while (FULLBRIGHT.consumeClick()) {
			cfg.fullbright = !cfg.fullbright;
			cfg.save();
		}
		Zoom.tick(mc, ZOOM);
		Freelook.tick(mc, FREELOOK);
	}
}
