package dev.oppaclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.oppaclient.Config;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(LightmapRenderStateExtractor.class)
abstract class LightmapMixin {
	@ModifyExpressionValue(method = "extract",
			slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;gamma()Lnet/minecraft/client/OptionInstance;")),
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0))
	private Object oppaclient$fullbright(Object gamma) {
		return Config.get().fullbright ? (Object) 16.0 : gamma;
	}
}
