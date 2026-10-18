package dev.oppaclient.mixin;

import dev.oppaclient.Cosmetics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
abstract class SkinMixin {
	@Unique
	private PlayerSkin oppa$from, oppa$to;

	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void oppa$cape(CallbackInfoReturnable<PlayerSkin> cir) {
		Identifier cape = Cosmetics.cape((AbstractClientPlayer) (Object) this);
		if (cape == null) return;
		PlayerSkin skin = cir.getReturnValue();
		if (skin != oppa$from || oppa$to == null || oppa$to.cape() == null || !cape.equals(oppa$to.cape().texturePath())) {
			ClientAsset.ResourceTexture asset = new ClientAsset.ResourceTexture(cape, cape);
			oppa$from = skin;
			oppa$to = new PlayerSkin(skin.body(), asset, asset, skin.model(), skin.secure());
		}
		cir.setReturnValue(oppa$to);
	}
}
