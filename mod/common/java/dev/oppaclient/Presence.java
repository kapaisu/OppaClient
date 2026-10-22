package dev.oppaclient;

import dev.oppaclient.platform.Platform;
import dev.oppaclient.social.Hosting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

public final class Presence {
	private static String last = "";
	private static String pending = System.getenv("OPPA_JOIN");

	private Presence() {}

	public static void tick(MinecraftClient mc) {
		if (Hosting.active() && (mc.world == null || !mc.isInSingleplayer())) Hosting.stop();
		if (pending != null && mc.world == null && mc.currentScreen != null && mc.getOverlay() == null) {
			String join = pending;
			pending = null;
			Platform.join(join);
		}
		String now;
		if (mc.world == null) {
			now = "menu";
		} else if (mc.isInSingleplayer()) {
			now = Hosting.ready() && Hosting.address() != null ? "hosting " + Hosting.address() : "singleplayer";
		} else {
			ServerInfo server = mc.getCurrentServerEntry();
			now = server == null ? "multiplayer" : "server " + Hosting.label(server.address.trim());
		}
		if (!now.equals(last)) {
			last = now;
			OppaClient.LOG.info("oppa-presence {} {}", Platform.gameVersion(), now);
		}
	}
}
