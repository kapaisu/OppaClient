package dev.oppaclient.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPlayerInteractionManager.class)
public interface InteractionManagerAccessor {
	@Accessor("currentBreakingProgress")
	float oppaclient$breakingProgress();

	@Accessor("currentBreakingPos")
	BlockPos oppaclient$breakingPos();
}
