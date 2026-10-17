package dev.oppaclient.mixin;

import dev.oppaclient.Freelook;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
abstract class CameraMixin {
	@ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
	private void oppaclient$freelook(Args args) {
		if (Freelook.active()) {
			args.set(0, Freelook.yaw());
			args.set(1, Freelook.pitch());
		}
	}
}
