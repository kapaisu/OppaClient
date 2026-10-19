package dev.oppaclient.platform;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import dev.oppaclient.mixin.SessionAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.SocialInteractionsManager;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;
import net.minecraft.client.session.report.AbuseReportContext;
import net.minecraft.client.session.report.ReporterEnvironment;
import net.minecraft.client.session.telemetry.TelemetryManager;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class Login {
	private Login() {}

	public static CompletableFuture<Void> apply(String name, UUID uuid, String token, String xuid) {
		MinecraftClient mc = MinecraftClient.getInstance();
		CompletableFuture<Void> done = new CompletableFuture<>();
		Thread.ofVirtual().start(() -> {
			try {
				Session session = new Session(name, uuid, token, Optional.ofNullable(xuid), Optional.empty(), Session.AccountType.MSA);
				YggdrasilAuthenticationService auth = new YggdrasilAuthenticationService(mc.getNetworkProxy());
				UserApiService api = auth.createUserApiService(token);
				UserApiService.UserProperties properties;
				try {
					properties = api.fetchProperties();
				} catch (Exception e) {
					properties = UserApiService.OFFLINE_PROPERTIES;
				}
			ProfileResult profile = mc.getSessionService().fetchProfile(uuid, true);
				SocialInteractionsManager social = new SocialInteractionsManager(mc, api);
				TelemetryManager telemetry = new TelemetryManager(mc, api, session);
				ProfileKeys keys = ProfileKeys.create(api, session, mc.runDirectory.toPath());
				AbuseReportContext reports = AbuseReportContext.create(ReporterEnvironment.ofIntegratedServer(), api);
				UserApiService.UserProperties props = properties;
				mc.execute(() -> {
					SessionAccessor a = (SessionAccessor) mc;
					a.oppa$session(session);
					a.oppa$profile(CompletableFuture.completedFuture(profile));
					a.oppa$api(api);
					a.oppa$properties(CompletableFuture.completedFuture(props));
					a.oppa$social(social);
					a.oppa$telemetry(telemetry);
					a.oppa$keys(keys);
					a.oppa$reports(reports);
					mc.updateWindowTitle();
					done.complete(null);
				});
			} catch (Throwable t) {
				done.completeExceptionally(t);
			}
		});
		return done;
	}
}
