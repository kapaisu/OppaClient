package dev.oppaclient.mixin;

import dev.oppaclient.Config;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
abstract class WorldWeatherMixin {
	@Inject(method = {"getRainGradient", "getThunderGradient"}, at = @At("RETURN"), cancellable = true)
	private void oppaclient$clear(float delta, CallbackInfoReturnable<Float> cir) {
		if (Config.get().clearWeather && (Object) this instanceof ClientWorld) cir.setReturnValue(0f);
	}
}
