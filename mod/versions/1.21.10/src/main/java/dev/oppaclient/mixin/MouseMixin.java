package dev.oppaclient.mixin;

import dev.oppaclient.Zoom;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
abstract class MouseMixin {
	@Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
	private void oppaclient$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		if (Zoom.scroll(vertical)) ci.cancel();
	}
}
