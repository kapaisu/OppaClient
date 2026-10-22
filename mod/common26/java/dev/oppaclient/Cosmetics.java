package dev.oppaclient;

import dev.oppaclient.look.CapeArt;
import dev.oppaclient.look.Images;
import dev.oppaclient.look.Looks;
import dev.oppaclient.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

public final class Cosmetics {
	public static final Identifier WHITE = OppaClient.id("textures/white.png");
	public static final Identifier GLOW = OppaClient.id("textures/glow.png");
	public static final float HELMET = 1.08f;
	private static final double RANGE = 64;
	private static final Map<String, Identifier> textures = new HashMap<>();
	private static Images.Image steve;

	private Cosmetics() {}

	public static Looks.Worn worn(Player player) {
		Minecraft mc = Minecraft.getInstance();
		boolean self = player == mc.player || player.getUUID().equals(mc.getUser().getProfileId());
		if (!self && (!Config.get().cosmetics || mc.player != null && player.distanceToSqr(mc.player) > RANGE * RANGE)) return Looks.Worn.NONE;
		return Looks.of(player.getUUID(), self);
	}

	public static boolean helmet(Player player) {
		return !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
	}

	public static float air(Player player) {
		if (player.isFallFlying() || player.getAbilities().flying) return 1;
		return player.onGround() ? 0 : 0.3f;
	}

	public static boolean elytra(Player player) {
		return player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
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

	private static Images.Image elytra;
	private static boolean elytraTried;

	private static Images.Image elytraShape() {
		if (elytraTried) return elytra;
		elytraTried = true;
		for (String path : new String[] {"textures/entity/equipment/wings/elytra.png", "textures/entity/elytra.png"}) {
			var res = Minecraft.getInstance().getResourceManager().getResource(Identifier.withDefaultNamespace(path));
			if (res.isEmpty()) continue;
			try (var in = res.get().open()) {
				elytra = Images.decode(in.readAllBytes());
				return elytra;
			} catch (Exception ignored) {
			}
		}
		return null;
	}

	public static Identifier cape(AbstractClientPlayer player) {
		String url = worn(player).cape();
		if (url == null) return null;
		Identifier id = textures.get(url);
		if (id != null) return id;
		Images.Image img = Looks.cape(url);
		if (img != null) img = CapeArt.fitElytra(img, elytraShape());
		if (img == null || !Minecraft.getInstance().isSameThread()) return null;
		id = OppaClient.id("cape/" + Integer.toHexString(url.hashCode()));
		Platform.upload(id, img.width(), img.height(), img.argb());
		textures.put(url, id);
		return id;
	}
}
