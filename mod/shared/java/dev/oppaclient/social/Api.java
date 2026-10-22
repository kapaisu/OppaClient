package dev.oppaclient.social;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class Api {
	private static final String BASE = base();
	private static volatile String token = System.getenv("OPPA_TOKEN");
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(8))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	private static String agent = "oppaclient";

	private Api() {}

	private static String base() {
		String over = System.getenv("OPPA_API");
		return over != null && over.startsWith("http://127.0.0.1") ? over : "https://oppaclient.com/api";
	}

	public static void agent(String gameVersion) {
		agent = "oppaclient/" + gameVersion;
	}

	public static void use(String next) {
		token = next;
	}

	static String token() {
		return token;
	}

	public static boolean signedIn() {
		String t = token;
		return t != null && t.matches("[0-9a-f]{64}");
	}

	public static String url(String file) {
		return BASE + "/" + file;
	}

	private static HttpRequest.Builder request(String file) {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url(file)))
				.timeout(Duration.ofSeconds(20))
				.header("User-Agent", agent);
		if (signedIn()) b.header("X-Oppa-Token", token);
		return b;
	}

	public static CompletableFuture<JsonObject> post(String file, JsonObject body) {
		HttpRequest req = request(file)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();
		return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(Api::parse);
	}

	public static CompletableFuture<JsonObject> upload(String file, String type, byte[] data) {
		HttpRequest req = request(file)
				.timeout(Duration.ofSeconds(120))
				.header("Content-Type", type)
				.POST(HttpRequest.BodyPublishers.ofByteArray(data))
				.build();
		return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(Api::parse);
	}

	public static CompletableFuture<byte[]> bytes(String url) {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent", agent);
		if (signedIn() && url.startsWith(BASE)) b.header("X-Oppa-Token", token);
		return HTTP.sendAsync(b.build(), HttpResponse.BodyHandlers.ofByteArray()).thenApply(r -> {
			if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode());
			return r.body();
		});
	}

	private static JsonObject parse(HttpResponse<String> res) {
		JsonElement json;
		try {
			json = JsonParser.parseString(res.body());
		} catch (RuntimeException e) {
			throw new IllegalStateException("Oppa's server sent something odd (" + res.statusCode() + ")");
		}
		JsonObject obj = json.isJsonObject() ? json.getAsJsonObject() : new JsonObject();
		if (res.statusCode() != 200) {
			throw new IllegalStateException(obj.has("error") ? obj.get("error").getAsString() : "Oppa's server returned " + res.statusCode());
		}
		return obj;
	}

	public static String message(Throwable t) {
		while (t.getCause() != null && (t instanceof java.util.concurrent.CompletionException || t.getMessage() == null)) t = t.getCause();
		String m = t.getMessage();
		if (t instanceof java.net.ConnectException || t instanceof java.net.http.HttpTimeoutException) return "Can't reach Oppa";
		return m == null || m.isBlank() ? "Something went wrong" : m;
	}
}
