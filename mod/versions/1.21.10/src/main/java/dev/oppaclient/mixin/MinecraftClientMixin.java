package dev.oppaclient.mixin;

import dev.oppaclient.menu.MainMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MinecraftClient.class)
abstract class MinecraftClientMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen oppaclient$mainMenu(Screen screen) {
		return screen != null && screen.getClass() == TitleScreen.class ? new MainMenu() : screen;
	}
}
