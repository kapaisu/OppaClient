package dev.oppaclient;

import dev.oppaclient.perf.Limits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;

public final class Culling {
	private Culling() {}

	public static boolean particle(ParticleOptions effect, double x, double y, double z) {
		LocalPlayer player = Minecraft.getInstance().player;
		ParticleType<?> type = effect.getType();
		boolean hit = type == ParticleTypes.CRIT || type == ParticleTypes.ENCHANTED_HIT || type == ParticleTypes.DAMAGE_INDICATOR || type == ParticleTypes.SWEEP_ATTACK;
		return Limits.particle(hit, player == null ? 0 : player.distanceToSqr(x, y, z));
	}

	public static boolean hide(Entity entity, double x, double y, double z) {
		int kind = entity instanceof ItemEntity || entity instanceof ExperienceOrb ? Limits.DROP
				: entity instanceof ItemFrame || entity instanceof ArmorStand ? Limits.DECOR : 0;
		return kind != 0 && !entity.isCurrentlyGlowing() && Limits.hide(kind, entity.distanceToSqr(x, y, z));
	}
}
