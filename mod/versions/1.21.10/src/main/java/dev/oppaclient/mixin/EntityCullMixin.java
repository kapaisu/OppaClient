package dev.oppaclient.mixin;

import dev.oppaclient.Culling;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
abstract class EntityCullMixin {
	@Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
	private void oppaclient$cull(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && Culling.hide(entity, x, y, z)) cir.setReturnValue(false);
	}
}
