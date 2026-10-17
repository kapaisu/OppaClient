package dev.oppaclient.mixin;

import dev.oppaclient.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.ClientClockManager$ClientClockInstance")
abstract class ClientWorldPropertiesMixin {
	@Inject(method = "totalTicks", at = @At("RETURN"), cancellable = true)
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
