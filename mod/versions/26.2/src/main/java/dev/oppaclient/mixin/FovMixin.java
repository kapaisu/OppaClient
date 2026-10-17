package dev.oppaclient.mixin;

import dev.oppaclient.Zoom;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
abstract class FovMixin {
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void oppaclient$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(Zoom.fov(cir.getReturnValueF()));
	}
}
