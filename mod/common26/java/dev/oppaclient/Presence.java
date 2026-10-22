package dev.oppaclient;

import dev.oppaclient.platform.Platform;
import dev.oppaclient.social.Hosting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

public final class Presence {
	private static String last = "";
	private static String pending = System.getenv("OPPA_JOIN");

	private Presence() {}

	public static void tick(Minecraft mc) {
		if (Hosting.active() && (mc.level == null || !mc.hasSingleplayerServer())) Hosting.stop();
		if (pending != null && mc.level == null && Platform.screen() != null) {
			String join = pending;
			pending = null;
			Platform.join(join);
		}
		String now;
		if (mc.level == null) {
			now = "menu";
		} else if (mc.hasSingleplayerServer()) {
			now = Hosting.ready() && Hosting.address() != null ? "hosting " + Hosting.address() : "singleplayer";
		} else {
			ServerData server = mc.getCurrentServer();
			now = server == null ? "multiplayer" : "server " + Hosting.label(server.ip.trim());
		}
		if (!now.equals(last)) {
			last = now;
			OppaClient.LOG.info("oppa-presence {} {}", Platform.gameVersion(), now);
		}
	}
}
