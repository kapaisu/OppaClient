package dev.oppaclient;

import dev.oppaclient.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

public final class Presence {
	private static String last = "";

	private Presence() {}

	public static void tick(Minecraft mc) {
		String now;
		if (mc.level == null) {
			now = "menu";
		} else if (mc.hasSingleplayerServer()) {
			now = "singleplayer";
		} else {
			ServerData server = mc.getCurrentServer();
			now = server == null ? "multiplayer" : "server " + server.ip.trim();
		}
		if (!now.equals(last)) {
			last = now;
			OppaClient.LOG.info("oppa-presence {} {}", Platform.gameVersion(), now);
		}
	}
}
