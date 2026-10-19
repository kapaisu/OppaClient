package dev.oppaclient.account;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oppaclient.OppaClient;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class Accounts {
	public record Entry(String uuid, String name) {}

	public record Grant(String name, UUID uuid, String token, String xuid, String site) {}

	private static final List<Entry> ALL = parse(System.getenv("OPPA_ACCOUNTS"));
	private static final String KEY = System.getenv("OPPA_SWITCH_KEY");

	private Accounts() {}

	private static List<Entry> parse(String raw) {
		List<Entry> out = new ArrayList<>();
		if (raw == null) return out;
		for (String part : raw.split(";")) {
			String[] bits = part.split(":", 2);
			if (bits.length == 2 && bits[0].matches("[0-9a-f]{32}") && bits[1].matches("[A-Za-z0-9_]{1,16}")) {
				out.add(new Entry(bits[0], bits[1]));
			}
		}
		return List.copyOf(out);
	}

	public static List<Entry> all() {
		return ALL;
	}

	public static boolean available() {
		return !ALL.isEmpty() && KEY != null && KEY.matches("[0-9a-f]{40}");
	}

	private static UUID uuid(String id) {
		return new UUID(Long.parseUnsignedLong(id.substring(0, 16), 16), Long.parseUnsignedLong(id.substring(16, 32), 16));
	}

	public static CompletableFuture<Grant> grant(String uuid) {
		CompletableFuture<Grant> out = new CompletableFuture<>();
		String nonce = UUID.randomUUID().toString().replace("-", "");
		Path file = FabricLoader.getInstance().getGameDir().resolve(".oppa").resolve(nonce + ".json");
		OppaClient.LOG.info("oppa-token {} {} {}", KEY, uuid, nonce);
		Thread.ofVirtual().start(() -> {
			try {
				for (int i = 0; i < 120 && !Files.exists(file); i++) Thread.sleep(250);
				if (!Files.exists(file)) throw new IllegalStateException("The launcher didn't answer. Is it still open?");
				JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
				Files.deleteIfExists(file);
				if (json.has("error")) throw new IllegalStateException(json.get("error").getAsString());
				String xuid = json.has("xuid") ? json.get("xuid").getAsString() : "";
				out.complete(new Grant(json.get("name").getAsString(), uuid(json.get("uuid").getAsString()), json.get("token").getAsString(),
						xuid.isEmpty() ? null : xuid, json.has("site") ? json.get("site").getAsString() : ""));
			} catch (Exception e) {
				out.completeExceptionally(e);
			}
		});
		return out;
	}
}
