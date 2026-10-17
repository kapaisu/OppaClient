package dev.oppaclient;

import dev.oppaclient.platform.Platform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

public final class Presence {
	private static String last = "";

	private Presence() {}

	public static void tick(MinecraftClient mc) {
		String now;
		if (mc.world == null) {
			now = "menu";
		} else if (mc.isInSingleplayer()) {
			now = "singleplayer";
		} else {
			ServerInfo server = mc.getCurrentServerEntry();
			now = server == null ? "multiplayer" : "server " + server.address.trim();
		}
		if (!now.equals(last)) {
			last = now;
			OppaClient.LOG.info("oppa-presence {} {}", Platform.gameVersion(), now);
		}
	}
}
