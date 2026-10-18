package dev.oppaclient.hud;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Combat {
	private static double reach;
	private static long reachAt, lastHit;
	private static int combo, lastHurt;

	private Combat() {}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			Minecraft mc = Minecraft.getInstance();
			if (world.isClientSide() && player == mc.player && entity instanceof LivingEntity) {
				Vec3 eye = player.getEyePosition();
				AABB box = entity.getBoundingBox();
				double dx = eye.x - Math.max(box.minX, Math.min(box.maxX, eye.x));
				double dy = eye.y - Math.max(box.minY, Math.min(box.maxY, eye.y));
				double dz = eye.z - Math.max(box.minZ, Math.min(box.maxZ, eye.z));
				long now = System.currentTimeMillis();
				reach = Math.sqrt(dx * dx + dy * dy + dz * dz);
				reachAt = now;
				combo = now - lastHit > 2500 ? 1 : combo + 1;
				lastHit = now;
			}
			return InteractionResult.PASS;
		});
	}

	public static void tick(Minecraft mc) {
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
