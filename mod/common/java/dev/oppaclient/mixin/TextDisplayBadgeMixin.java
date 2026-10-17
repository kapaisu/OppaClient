package dev.oppaclient.mixin;

import dev.oppaclient.Badges;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DisplayEntity.TextDisplayEntity.class)
abstract class TextDisplayBadgeMixin {
	@Shadow
	private DisplayEntity.TextDisplayEntity.TextLines textLines;

	@Unique
	private boolean oppaclient$badged;

	@Inject(method = "splitLines", at = @At("HEAD"))
	private void oppaclient$refresh(DisplayEntity.TextDisplayEntity.LineSplitter splitter, CallbackInfoReturnable<DisplayEntity.TextDisplayEntity.TextLines> cir) {
		boolean want = Badges.rides((Entity) (Object) this);
		if (want != oppaclient$badged) {
			oppaclient$badged = want;
			textLines = null;
		}
	}

	@ModifyArg(method = "splitLines", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity$LineSplitter;split(Lnet/minecraft/text/Text;I)Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity$TextLines;"))
	private Text oppaclient$badge(Text text) {
		return oppaclient$badged ? Badges.decorate(text) : text;
	}
}
