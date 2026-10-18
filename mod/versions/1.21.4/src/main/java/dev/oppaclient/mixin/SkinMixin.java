package dev.oppaclient.mixin;

import dev.oppaclient.Cosmetics;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
abstract class SkinMixin {
	@Unique
	private SkinTextures oppa$from, oppa$to;

	@Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
	private void oppa$cape(CallbackInfoReturnable<SkinTextures> cir) {
		Identifier cape = Cosmetics.cape((AbstractClientPlayerEntity) (Object) this);
		if (cape == null) return;
		SkinTextures skin = cir.getReturnValue();
		if (skin != oppa$from || oppa$to == null || !cape.equals(oppa$to.capeTexture())) {
			oppa$from = skin;
			oppa$to = new SkinTextures(skin.texture(), skin.textureUrl(), cape, cape, skin.model(), skin.secure());
		}
		cir.setReturnValue(oppa$to);
	}
}
