package dev.oppaclient.mixin;

import dev.oppaclient.Config;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
abstract class WorldWeatherMixin {
	@Inject(method = {"getRainLevel", "getThunderLevel"}, at = @At("RETURN"), cancellable = true)
	private void oppaclient$clear(float delta, CallbackInfoReturnable<Float> cir) {
		if (Config.get().clearWeather && (Object) this instanceof ClientLevel) cir.setReturnValue(0f);
	}
}
