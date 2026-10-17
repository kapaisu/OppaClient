package dev.oppaclient.mixin;

import dev.oppaclient.Badges;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
abstract class BadgeMixin {
	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
	private void oppaclient$badge(CallbackInfoReturnable<Component> cir) {
		if ((Object) this instanceof AbstractClientPlayer player && Badges.has(player)) {
			cir.setReturnValue(Badges.decorate(cir.getReturnValue()));
		}
	}
}
