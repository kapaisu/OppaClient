package dev.oppaclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.oppaclient.Config;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
abstract class FireOverlayMixin {
	@Inject(method = "submitFire", at = @At("HEAD"))
	private static void oppaclient$lower(PoseStack matrices, SubmitNodeCollector submits, TextureAtlasSprite sprite, CallbackInfo ci) {
		matrices.pushPose();
		matrices.translate(0, -0.35 * Config.get().lowFire, 0);
	}

	@Inject(method = "submitFire", at = @At("RETURN"))
	private static void oppaclient$restore(PoseStack matrices, SubmitNodeCollector submits, TextureAtlasSprite sprite, CallbackInfo ci) {
		matrices.popPose();
	}
}
