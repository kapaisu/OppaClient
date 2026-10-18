package dev.oppaclient;

import dev.oppaclient.look.Images;
import dev.oppaclient.look.Looks;
import dev.oppaclient.platform.Platform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class Cosmetics {
	public static final Identifier WHITE = OppaClient.id("textures/white.png");
	private static final Map<String, Identifier> textures = new HashMap<>();

	private Cosmetics() {}

	public static Looks.Worn worn(PlayerEntity player) {
		MinecraftClient mc = MinecraftClient.getInstance();
		boolean self = player == mc.player || player.getUuid().equals(mc.getSession().getUuidOrNull());
		if (!self && !Config.get().cosmetics) return Looks.Worn.NONE;
		return Looks.of(player.getUuid(), self);
	}

	public static final float HELMET = 1.08f;

	public static boolean helmet(PlayerEntity player) {
		return !player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).isEmpty();
	}

	public static float pace(PlayerEntity player) {
		if (Platform.gliding(player) || player.getAbilities().flying) return 1;
		double v = player.getVelocity().horizontalLength();
		return (float) Math.min(0.6, v * 2.5) + (player.isOnGround() ? 0 : 0.3f);
	}

	private static Images.Image steve;

	public static Images.Image defaultSkin() {
		if (steve == null) {
			try (var in = MinecraftClient.getInstance().getResourceManager().getResource(Identifier.ofVanilla("textures/entity/player/wide/steve.png")).orElseThrow().getInputStream()) {
				steve = Images.decode(in.readAllBytes());
			} catch (Exception e) {
				steve = new Images.Image(64, 64, new int[64 * 64]);
			}
		}
		return steve;
	}

	public static Identifier cape(AbstractClientPlayerEntity player) {
		String url = worn(player).cape();
		if (url == null) return null;
		Identifier id = textures.get(url);
		if (id != null) return id;
		Images.Image img = Looks.cape(url);
		if (img == null || !MinecraftClient.getInstance().isOnThread()) return null;
		id = OppaClient.id("cape/" + Integer.toHexString(url.hashCode()));
		Platform.upload(id, img.width(), img.height(), img.argb());
		textures.put(url, id);
		return id;
	}
}
