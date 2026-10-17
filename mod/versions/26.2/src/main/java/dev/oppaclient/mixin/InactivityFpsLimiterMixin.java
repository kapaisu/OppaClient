package dev.oppaclient.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.oppaclient.Config;
import dev.oppaclient.platform.Platform;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FramerateLimitTracker.class)
abstract class InactivityFpsLimiterMixin {
	@Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
	private void oppaclient$cap(CallbackInfoReturnable<Integer> cir) {
		Minecraft mc = Minecraft.getInstance();
		Config cfg = Config.get();
		int limit = cir.getReturnValueI();
		if (cfg.backgroundFps > 0 && !mc.isWindowActive()) limit = Math.min(limit, cfg.backgroundFps);
		if (cfg.menuFps > 0 && mc.level != null && Platform.screen() != null) limit = Math.min(limit, cfg.menuFps);
		cir.setReturnValue(limit);
	}
}
