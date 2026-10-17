package dev.oppaclient.mixin;

import dev.oppaclient.Badges;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class StandBadgeMixin {
	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
	private void oppaclient$badge(CallbackInfoReturnable<Component> cir) {
		if (Badges.marks((Entity) (Object) this)) cir.setReturnValue(Badges.decorate(cir.getReturnValue()));
	}
}
