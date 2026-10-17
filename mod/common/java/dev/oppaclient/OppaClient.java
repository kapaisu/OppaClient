package dev.oppaclient;

import dev.oppaclient.overlay.OutlineRenderer;
import dev.oppaclient.overlay.OverlayConfig;
import dev.oppaclient.platform.Platform;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OppaClient implements ClientModInitializer {
	public static final String ID = "oppaclient";
	public static final Logger LOG = LoggerFactory.getLogger("Oppa");

	public static final OutlineRenderer OUTLINE = new OutlineRenderer();

	public static Identifier id(String path) {
		return Identifier.of(ID, path);
	}

	@Override
	public void onInitializeClient() {
		Config.get();
		OverlayConfig.load();
		Keys.register();
		ClientTickEvents.END_CLIENT_TICK.register(Keys::tick);
		Platform.init();
	}
}
