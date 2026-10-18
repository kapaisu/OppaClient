package dev.oppaclient.look;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.oppaclient.social.Api;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Looks {
	public record Worn(Look hat, Look wings, Look aura, String cape) {
		public static final Worn NONE = new Worn(Look.NONE, Look.NONE, Look.NONE, null);

		public boolean empty() {
			return hat.none() && wings.none() && aura.none() && cape == null;
		}
	}

	private static final Map<UUID, Worn> others = new ConcurrentHashMap<>();
	private static final Map<String, Images.Image> capes = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> loading = new ConcurrentHashMap<>();

	private Looks() {}

	public static void accept(UUID id, JsonElement json) {
		if (json == null || !json.isJsonObject()) {
			others.remove(id);
			return;
		}
		JsonObject o = json.getAsJsonObject();
		String cape = o.has("cape") && !o.get("cape").isJsonNull() ? o.get("cape").getAsString() : null;
		Worn w = new Worn(
				Look.parse(str(o, "hat"), Catalog.HATS),
				Look.parse(str(o, "wings"), Catalog.WINGS),
				Look.parse(str(o, "aura"), Catalog.AURAS),
				cape != null && cape.startsWith(Api.url("cape.php?h=")) ? cape : null);
		if (w.empty()) others.remove(id);
		else others.put(id, w);
	}

	private static String str(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
	}

	public static Worn of(UUID id, boolean self) {
		if (self) return Wardrobe.worn();
		return others.getOrDefault(id, Worn.NONE);
	}

	public static Images.Image cape(String url) {
		if (url == null) return null;
		Images.Image img = capes.get(url);
		if (img != null || loading.putIfAbsent(url, true) != null) return img;
		Api.bytes(url).whenComplete((bytes, err) -> {
			if (err == null) {
				try {
					capes.put(url, Images.decode(bytes));
				} catch (RuntimeException ignored) {
				}
			}
		});
		return null;
	}

	public static void remember(String url, Images.Image img) {
		capes.put(url, img);
		loading.put(url, true);
	}
}
