package dev.oppaclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.oppaclient.Config;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(LightmapTextureManager.class)
abstract class LightmapMixin {
	@ModifyExpressionValue(method = "update",
			slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/GameOptions;getGamma()Lnet/minecraft/client/option/SimpleOption;")),
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;", ordinal = 0))
	private Object oppaclient$fullbright(Object gamma) {
		return Config.get().fullbright ? (Object) 16.0 : gamma;
	}
}
