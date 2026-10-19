package dev.oppaclient.account;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class Mojang {
	private static final URI SKINS = URI.create("https://api.minecraftservices.com/minecraft/profile/skins");
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	private Mojang() {}

	public static CompletableFuture<Void> upload(String accessToken, byte[] png, boolean slim) {
		String boundary = "oppa" + UUID.randomUUID().toString().replace("-", "");
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n" + (slim ? "slim" : "classic") + "\r\n");
		write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\nContent-Type: image/png\r\n\r\n");
		body.writeBytes(png);
		write(body, "\r\n--" + boundary + "--\r\n");
		HttpRequest req = HttpRequest.newBuilder(SKINS)
				.timeout(Duration.ofSeconds(30))
				.header("Authorization", "Bearer " + accessToken)
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
				.build();
		return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
			int code = res.statusCode();
			if (code == 200) return null;
			if (code == 401) throw new IllegalStateException("Your sign-in expired. Restart Minecraft from the launcher.");
			if (code == 429) throw new IllegalStateException("Mojang says slow down. Try again in a minute.");
			throw new IllegalStateException("Mojang returned " + code);
		});
	}

	private static void write(ByteArrayOutputStream out, String s) {
		out.writeBytes(s.getBytes(StandardCharsets.UTF_8));
	}
}
