package dev.oppaclient;

import dev.oppaclient.look.Images;
import dev.oppaclient.look.Looks;
import dev.oppaclient.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

public final class Cosmetics {
	public static final Identifier WHITE = OppaClient.id("textures/white.png");
	public static final float HELMET = 1.08f;
	private static final Map<String, Identifier> textures = new HashMap<>();
	private static Images.Image steve;

	private Cosmetics() {}

	public static Looks.Worn worn(Player player) {
		Minecraft mc = Minecraft.getInstance();
		boolean self = player == mc.player || player.getUUID().equals(mc.getUser().getProfileId());
		if (!self && !Config.get().cosmetics) return Looks.Worn.NONE;
		return Looks.of(player.getUUID(), self);
	}

	public static boolean helmet(Player player) {
		return !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
	}

	public static float pace(Player player) {
		if (player.isFallFlying() || player.getAbilities().flying) return 1;
		double v = player.getDeltaMovement().horizontalDistance();
		return (float) Math.min(0.6, v * 2.5) + (player.onGround() ? 0 : 0.3f);
	}

	public static Images.Image defaultSkin() {
		if (steve == null) {
			try (var in = Minecraft.getInstance().getResourceManager().getResource(Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png")).orElseThrow().open()) {
				steve = Images.decode(in.readAllBytes());
			} catch (Exception e) {
				steve = new Images.Image(64, 64, new int[64 * 64]);
			}
		}
		return steve;
	}

	public static Identifier cape(AbstractClientPlayer player) {
		String url = worn(player).cape();
		if (url == null) return null;
		Identifier id = textures.get(url);
		if (id != null) return id;
		Images.Image img = Looks.cape(url);
		if (img == null || !Minecraft.getInstance().isSameThread()) return null;
		id = OppaClient.id("cape/" + Integer.toHexString(url.hashCode()));
		Platform.upload(id, img.width(), img.height(), img.argb());
		textures.put(url, id);
		return id;
	}
}
