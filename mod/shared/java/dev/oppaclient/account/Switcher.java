package dev.oppaclient.account;

import dev.oppaclient.look.Wardrobe;
import dev.oppaclient.platform.Login;
import dev.oppaclient.platform.Platform;
import dev.oppaclient.social.Api;
import dev.oppaclient.social.Social;
import dev.oppaclient.social.Toasts;

import java.util.function.Consumer;

public final class Switcher {
	private static volatile boolean busy;

	private Switcher() {}

	public static boolean busy() {
		return busy;
	}

	public static void to(Accounts.Entry entry, String server, Consumer<Runnable> onGameThread, Consumer<String> status) {
		if (busy) return;
		busy = true;
		status.accept("Signing in as " + entry.name() + "…");
		if (server != null) Platform.leave();
		Accounts.grant(entry.uuid())
				.thenCompose(g -> Login.apply(g.name(), g.uuid(), g.token(), g.xuid()).thenApply(v -> g))
				.whenComplete((g, err) -> onGameThread.accept(() -> {
					busy = false;
					if (err != null) {
						String why = Api.message(err);
						status.accept(why);
						Toasts.show("Couldn't switch account", why, Toasts.Kind.INFO);
						return;
					}
					Api.use(g.site().isEmpty() ? null : g.site());
					Social.reset();
					Wardrobe.reset();
					status.accept("Signed in as " + g.name());
					Toasts.show("Signed in as " + g.name(), server == null ? null : "Joining " + server, Toasts.Kind.INFO);
					if (server != null) Platform.join(server);
				}));
	}
}
