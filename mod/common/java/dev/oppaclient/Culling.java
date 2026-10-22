package dev.oppaclient;

import dev.oppaclient.perf.Limits;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;

public final class Culling {
	private Culling() {}

	public static boolean particle(ParticleEffect effect, double x, double y, double z) {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		ParticleType<?> type = effect.getType();
		boolean hit = type == ParticleTypes.CRIT || type == ParticleTypes.ENCHANTED_HIT || type == ParticleTypes.DAMAGE_INDICATOR || type == ParticleTypes.SWEEP_ATTACK;
		return Limits.particle(hit, player == null ? 0 : player.squaredDistanceTo(x, y, z));
	}

	public static boolean hide(Entity entity, double x, double y, double z) {
		int kind = entity instanceof ItemEntity || entity instanceof ExperienceOrbEntity ? Limits.DROP
				: entity instanceof ItemFrameEntity || entity instanceof ArmorStandEntity ? Limits.DECOR : 0;
		return kind != 0 && !entity.isGlowing() && Limits.hide(kind, entity.squaredDistanceTo(x, y, z));
	}
}
