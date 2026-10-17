package dev.oppaclient.mixin;

import dev.oppaclient.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
abstract class FireOverlayMixin {
	@Inject(method = "renderFireOverlay", at = @At("HEAD"))
	private static void oppaclient$lower(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
		matrices.push();
		matrices.translate(0, -0.35 * Config.get().lowFire, 0);
	}

	@Inject(method = "renderFireOverlay", at = @At("RETURN"))
	private static void oppaclient$restore(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
		matrices.pop();
	}
}
