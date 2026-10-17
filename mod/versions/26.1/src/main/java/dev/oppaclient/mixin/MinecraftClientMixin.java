package dev.oppaclient.mixin;

import dev.oppaclient.menu.MainMenu;
import dev.oppaclient.menu.ServersScreen;
import dev.oppaclient.menu.WorldsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Minecraft.class)
abstract class MinecraftClientMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen oppaclient$mainMenu(Screen screen) {
		Minecraft self = (Minecraft) (Object) this;
		if (screen == null) return self.level == null ? new MainMenu() : null;
		if (screen.getClass() == TitleScreen.class) return new MainMenu();
		if (screen.getClass() == SelectWorldScreen.class) return new WorldsScreen(new MainMenu());
		if (screen.getClass() == JoinMultiplayerScreen.class) return new ServersScreen(new MainMenu());
		return screen;
	}
}
