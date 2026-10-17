package dev.oppaclient.mixin;

import dev.oppaclient.Badges;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
abstract class BadgeMixin {
	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
	private void oppaclient$badge(CallbackInfoReturnable<Text> cir) {
		if ((Object) this instanceof AbstractClientPlayerEntity player && Badges.has(player)) {
			cir.setReturnValue(Badges.decorate(cir.getReturnValue()));
		}
	}
}
