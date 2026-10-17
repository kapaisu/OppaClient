package dev.oppaclient.mixin;

import dev.oppaclient.menu.MainMenu;
import dev.oppaclient.menu.ServersScreen;
import dev.oppaclient.menu.WorldsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MinecraftClient.class)
abstract class MinecraftClientMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen oppaclient$mainMenu(Screen screen) {
		MinecraftClient self = (MinecraftClient) (Object) this;
		if (screen == null) return self.world == null ? new MainMenu() : null;
		if (screen.getClass() == TitleScreen.class) return new MainMenu();
		if (screen.getClass() == SelectWorldScreen.class) return new WorldsScreen(new MainMenu());
		if (screen.getClass() == MultiplayerScreen.class) return new ServersScreen(new MainMenu());
		return screen;
	}
}
