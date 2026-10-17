package dev.oppaclient.mixin;

import dev.oppaclient.Config;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
abstract class FramerateMixin {
	@Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
	private void oppaclient$cap(CallbackInfoReturnable<Integer> cir) {
		MinecraftClient mc = (MinecraftClient) (Object) this;
		Config cfg = Config.get();
		int limit = cir.getReturnValueI();
		if (cfg.backgroundFps > 0 && !mc.isWindowFocused()) limit = Math.min(limit, cfg.backgroundFps);
		if (cfg.menuFps > 0 && mc.world != null && mc.currentScreen != null) limit = Math.min(limit, cfg.menuFps);
		cir.setReturnValue(limit);
	}
}
