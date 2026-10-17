package dev.oppaclient.mixin;

import dev.oppaclient.Config;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.Properties.class)
abstract class ClientWorldPropertiesMixin {
	@Inject(method = "getTimeOfDay", at = @At("RETURN"), cancellable = true)
	private void oppaclient$time(CallbackInfoReturnable<Long> cir) {
		long fixed = switch (Config.get().time) {
			case "Day" -> 6000L;
			case "Sunset" -> 12500L;
			case "Night" -> 18000L;
			default -> -1L;
		};
		if (fixed >= 0) {
			long real = cir.getReturnValueJ();
			cir.setReturnValue(Math.floorDiv(real, 24000L) * 24000L + fixed);
		}
	}
}
