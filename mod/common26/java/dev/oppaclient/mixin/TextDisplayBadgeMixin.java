package dev.oppaclient.mixin;

import dev.oppaclient.Badges;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Display.TextDisplay.class)
abstract class TextDisplayBadgeMixin {
	@Shadow
	private Display.TextDisplay.CachedInfo clientDisplayCache;

	@Unique
	private boolean oppaclient$badged;

	@Inject(method = "cacheDisplay", at = @At("HEAD"))
	private void oppaclient$refresh(Display.TextDisplay.LineSplitter splitter, CallbackInfoReturnable<Display.TextDisplay.CachedInfo> cir) {
		boolean want = Badges.rides((Entity) (Object) this);
		if (want != oppaclient$badged) {
			oppaclient$badged = want;
			clientDisplayCache = null;
		}
	}

	@ModifyArg(method = "cacheDisplay", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Display$TextDisplay$LineSplitter;split(Lnet/minecraft/network/chat/Component;I)Lnet/minecraft/world/entity/Display$TextDisplay$CachedInfo;"))
	private Component oppaclient$badge(Component text) {
		return oppaclient$badged ? Badges.decorate(text) : text;
	}
}
