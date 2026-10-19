package dev.oppaclient.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.server.Services;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public interface SessionAccessor {
	@Accessor("user")
	@Mutable
	void oppa$user(User user);

	@Accessor("profileFuture")
	@Mutable
	void oppa$profile(CompletableFuture<ProfileResult> profile);

	@Accessor("userApiService")
	@Mutable
	void oppa$api(UserApiService api);

	@Accessor("userPropertiesFuture")
	@Mutable
	void oppa$properties(CompletableFuture<UserApiService.UserProperties> properties);

	@Accessor("playerSocialManager")
	@Mutable
	void oppa$social(PlayerSocialManager social);

	@Accessor("telemetryManager")
	@Mutable
	void oppa$telemetry(ClientTelemetryManager telemetry);

	@Accessor("profileKeyPairManager")
	@Mutable
	void oppa$keys(ProfileKeyPairManager keys);

	@Accessor("reportingContext")
	void oppa$reports(ReportingContext reports);

	@Accessor("services")
	@Mutable
	void oppa$services(Services services);
}
