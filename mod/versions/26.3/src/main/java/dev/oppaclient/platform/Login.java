package dev.oppaclient.platform;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.services.ProfileResult;
import com.mojang.authlib.services.FriendsService;
import com.mojang.authlib.services.MinecraftServicesDiscoveryService;
import dev.oppaclient.mixin.SessionAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.server.Services;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class Login {
	private Login() {}

	public static CompletableFuture<Void> apply(String name, UUID uuid, String token, String xuid) {
		Minecraft mc = Minecraft.getInstance();
		CompletableFuture<Void> done = new CompletableFuture<>();
		Thread.ofVirtual().start(() -> {
			try {
				User user = new User(name, uuid, token, Optional.ofNullable(xuid), Optional.empty());
				MinecraftServicesDiscoveryService auth = MinecraftServicesDiscoveryService.create(mc.getProxy(), true);
				UserApiService api = auth.createUserApiService(token);
				UserApiService.UserProperties properties;
				try {
					properties = api.fetchProperties();
				} catch (Exception e) {
					properties = UserApiService.OFFLINE_PROPERTIES;
				}
				Services services = Services.create(auth, mc.gameDirectory);
				ProfileResult profile = services.sessionService().fetchProfile(uuid, true);
				FriendsService friends = auth.createFriendsService(token);
				RemoteFriendListUpdateHandler friendList = new RemoteFriendListUpdateHandler(friends, mc);
				PlayerSocialManager social = new PlayerSocialManager(mc, api, friends, friendList);
				ClientTelemetryManager telemetry = new ClientTelemetryManager(mc, api, user);
				ProfileKeyPairManager keys = ProfileKeyPairManager.create(api, user, mc.gameDirectory.toPath());
				ReportingContext reports = ReportingContext.create(ReportEnvironment.local(), api);
				UserApiService.UserProperties props = properties;
				mc.execute(() -> {
					SessionAccessor a = (SessionAccessor) mc;
					a.oppa$services(services);
					a.oppa$user(user);
					a.oppa$profile(CompletableFuture.completedFuture(profile));
					a.oppa$api(api);
					a.oppa$properties(CompletableFuture.completedFuture(props));
					a.oppa$social(social);
					a.oppa$friendList().close();
					a.oppa$friendList(friendList);
					if (social.isFriendListEnabled()) friendList.start();
					a.oppa$telemetry(telemetry);
					a.oppa$keys(keys);
					a.oppa$reports(reports);
					mc.updateTitle();
					done.complete(null);
				});
			} catch (Throwable t) {
				done.completeExceptionally(t);
			}
		});
		return done;
	}
}
