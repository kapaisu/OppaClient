package dev.oppaclient.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.SocialInteractionsManager;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;
import net.minecraft.client.session.report.AbuseReportContext;
import net.minecraft.client.session.telemetry.TelemetryManager;
import net.minecraft.util.ApiServices;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

@Mixin(MinecraftClient.class)
public interface SessionAccessor {
	@Accessor("session")
	@Mutable
	void oppa$session(Session session);

	@Accessor("gameProfileFuture")
	@Mutable
	void oppa$profile(CompletableFuture<ProfileResult> profile);

	@Accessor("userApiService")
	@Mutable
	void oppa$api(UserApiService api);

	@Accessor("userPropertiesFuture")
	@Mutable
	void oppa$properties(CompletableFuture<UserApiService.UserProperties> properties);

	@Accessor("socialInteractionsManager")
	@Mutable
	void oppa$social(SocialInteractionsManager social);

	@Accessor("telemetryManager")
	@Mutable
	void oppa$telemetry(TelemetryManager telemetry);

	@Accessor("profileKeys")
	@Mutable
	void oppa$keys(ProfileKeys keys);

	@Accessor("abuseReportContext")
	void oppa$reports(AbuseReportContext reports);

	@Accessor("apiServices")
	@Mutable
	void oppa$services(ApiServices services);
}
