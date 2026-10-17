package dev.oppaclient.mixin;

import dev.oppaclient.Config;
import dev.oppaclient.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
abstract class NametagMixin {
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
	private void oppaclient$ownName(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
		Minecraft mc = Minecraft.getInstance();
		if (Config.get().ownNametag && entity == mc.player && !mc.options.getCameraType().isFirstPerson() && !Platform.hudHidden()) {
			cir.setReturnValue(true);
		}
	}
}
