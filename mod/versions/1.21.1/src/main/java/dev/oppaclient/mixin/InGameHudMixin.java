package dev.oppaclient.mixin;

import dev.oppaclient.hud.Crosshair;
import dev.oppaclient.hud.Hud;
import dev.oppaclient.platform.GfxImpl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
abstract class InGameHudMixin {
	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void oppaclient$crosshair(DrawContext ctx, RenderTickCounter tick, CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (Hud.drawsCrosshair(mc)) {
			Crosshair.draw(new GfxImpl(ctx), mc);
			ci.cancel();
		}
	}

	@Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
	private void oppaclient$effects(DrawContext ctx, RenderTickCounter tick, CallbackInfo ci) {
		if (Hud.drawsEffects()) ci.cancel();
	}
}
