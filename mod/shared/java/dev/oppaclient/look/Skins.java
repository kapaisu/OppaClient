package dev.oppaclient.look;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oppaclient.Paint;
import dev.oppaclient.overlay.Canvas;
import dev.oppaclient.platform.Gfx;
import dev.oppaclient.social.Api;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Skins {
	public record Skin(Images.Image image, boolean slim) {}

	private static final Map<String, Skin> cache = new ConcurrentHashMap<>();
	private static final Map<String, Long> asked = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> failed = new ConcurrentHashMap<>();
	private static final Canvas canvas = Canvas.flat();

	private Skins() {}

	public static Skin get(String uuid) {
		if (uuid == null) return null;
		String id = uuid.replace("-", "");
		Skin s = cache.get(id);
		if (s != null) return s;
		long now = System.currentTimeMillis();
		Long last = asked.get(id);
		if (last != null && now - last < 60_000) return null;
		asked.put(id, now);
		Api.bytes("https://sessionserver.mojang.com/session/minecraft/profile/" + id).thenCompose(body -> {
			JsonObject profile = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
			for (JsonElement p : profile.getAsJsonArray("properties")) {
				JsonObject prop = p.getAsJsonObject();
				if (!"textures".equals(prop.get("name").getAsString())) continue;
				JsonObject tex = JsonParser.parseString(new String(Base64.getDecoder().decode(prop.get("value").getAsString()), StandardCharsets.UTF_8))
						.getAsJsonObject().getAsJsonObject("textures");
				if (tex == null || !tex.has("SKIN")) break;
				JsonObject skin = tex.getAsJsonObject("SKIN");
				boolean slim = skin.has("metadata") && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());
				return Api.bytes(skin.get("url").getAsString().replace("http://", "https://")).thenApply(png -> new Skin(Images.decode(png), slim));
			}
			throw new IllegalStateException("no skin");
		}).whenComplete((skin, err) -> {
			if (skin != null) cache.put(id, skin);
			else failed.put(id, true);
		});
		return null;
	}

	public static void put(String uuid, Skin skin) {
		String id = uuid.replace("-", "");
		cache.put(id, skin);
		failed.remove(id);
	}

	public static boolean failed(String uuid) {
		return uuid == null || failed.containsKey(uuid.replace("-", ""));
	}

	public static void face(Gfx g, String uuid, int x, int y, int size, float alpha) {
		Skin s = get(uuid);
		if (s == null) {
			Paint.round(g, x, y, size, size, 3, Paint.ink(0.08f * alpha));
			return;
		}
		float px = size / 8f;
		canvas.alpha(alpha);
		for (int layer = 0; layer < 2; layer++) {
			int u = layer == 0 ? 8 : 40;
			for (int j = 0; j < 8; j++) {
				for (int i = 0; i < 8; i++) {
					int c = s.image().at(u + i, 8 + j);
					if (layer == 1 && (c >>> 24) < 128) continue;
					canvas.rect(i * px, j * px, (i + 1) * px, (j + 1) * px, c | 0xFF000000);
				}
			}
		}
		g.push();
		g.translate(x, y);
		g.canvas(canvas, 0, 0, size, size);
		g.pop();
	}
}
