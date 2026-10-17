package dev.oppaclient.mixin;

import dev.oppaclient.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
abstract class NametagMixin {
	@Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
	private void oppaclient$ownName(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (Config.get().ownNametag && entity == mc.player && !mc.options.getPerspective().isFirstPerson() && !mc.options.hudHidden) {
			cir.setReturnValue(true);
		}
	}
}
