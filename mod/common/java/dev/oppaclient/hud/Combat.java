package dev.oppaclient.hud;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class Combat {
	private static double reach;
	private static long reachAt, lastHit;
	private static int combo, lastHurt;

	private Combat() {}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (world.isClient() && player == mc.player && entity instanceof LivingEntity) {
				Vec3d eye = player.getEyePos();
				Box box = entity.getBoundingBox();
				double dx = eye.x - Math.max(box.minX, Math.min(box.maxX, eye.x));
				double dy = eye.y - Math.max(box.minY, Math.min(box.maxY, eye.y));
				double dz = eye.z - Math.max(box.minZ, Math.min(box.maxZ, eye.z));
				long now = System.currentTimeMillis();
				reach = Math.sqrt(dx * dx + dy * dy + dz * dz);
				reachAt = now;
				combo = now - lastHit > 2500 ? 1 : combo + 1;
				lastHit = now;
			}
			return ActionResult.PASS;
		});
	}

	public static void tick(MinecraftClient mc) {
		if (mc.player == null) return;
		int hurt = mc.player.hurtTime;
		if (hurt > 0 && lastHurt == 0) combo = 0;
		lastHurt = hurt;
		if (System.currentTimeMillis() - lastHit > 2500) combo = 0;
	}

	static double reach() {
		return reach;
	}

	static boolean recentReach() {
		return System.currentTimeMillis() - reachAt < 3000;
	}

	static int combo() {
		return combo;
	}
}
