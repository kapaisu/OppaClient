package dev.oppaclient;

import dev.oppaclient.overlay.OutlineRenderer;
import dev.oppaclient.overlay.OverlayConfig;
import dev.oppaclient.look.Wardrobe;
import dev.oppaclient.menu.MenuScreen;
import dev.oppaclient.platform.Platform;
import dev.oppaclient.social.Api;
import dev.oppaclient.social.Shots;
import dev.oppaclient.social.Social;
import net.minecraft.client.Minecraft;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OppaClient implements ClientModInitializer {
	public static final String ID = "oppaclient";
	public static final Logger LOG = LoggerFactory.getLogger("Oppa");

	public static final OutlineRenderer OUTLINE = new OutlineRenderer();

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(ID, path);
	}

	private static void online(Minecraft mc) {
		Social.tick(Platform.screen() instanceof MenuScreen menu && menu.onFriends() ? 5_000 : 20_000);
		Wardrobe.tick();
		dev.oppaclient.social.Chat.start();
		Shots.watch(mc.gameDirectory.toPath().resolve("screenshots"), Keys.SHARE.isUnbound() ? "the share key" : Keys.SHARE.getTranslatedKeyMessage().getString());
	}

	@Override
	public void onInitializeClient() {
		Api.agent(Platform.gameVersion());
		Config.get();
		OverlayConfig.load();
		Keys.register();
		ClientTickEvents.END_CLIENT_TICK.register(Keys::tick);
		ClientTickEvents.END_CLIENT_TICK.register(Badges::tick);
		ClientTickEvents.END_CLIENT_TICK.register(Presence::tick);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> dev.oppaclient.perf.Limits.tick());
		ClientTickEvents.END_CLIENT_TICK.register(OppaClient::online);
		ClientTickEvents.END_CLIENT_TICK.register(dev.oppaclient.hud.Combat::tick);
		dev.oppaclient.hud.Combat.register();
		CosmeticsLayer.register();
		Platform.init();
	}
}
