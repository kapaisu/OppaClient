package dev.oppaclient.mixin;

import dev.oppaclient.Badges;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class StandBadgeMixin {
	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
	private void oppaclient$badge(CallbackInfoReturnable<Text> cir) {
		if (Badges.marks((Entity) (Object) this)) cir.setReturnValue(Badges.decorate(cir.getReturnValue()));
	}
}
