package dev.oppaclient;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oppaclient.look.Looks;
import dev.oppaclient.platform.Platform;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

public final class Badges {
	private static final URI LOOKUP = URI.create(dev.oppaclient.social.Api.url("users.php"));
	private static final long RECHECK = 10_000;
	private static final int BATCH = 100;

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static final Set<UUID> online = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, Long> checked = new ConcurrentHashMap<>();
	private static Component mark;
	private static volatile boolean busy;
	private static int ticks;
	private static final Set<Integer> stands = new HashSet<>();

	public static boolean has(AbstractClientPlayer player) {
		if (!Config.get().badges) return false;
		Minecraft mc = Minecraft.getInstance();
		return player == mc.player || online.contains(player.getUUID());
	}

	public static Component decorate(Component name) {
		if (mark == null) {
			mark = Component.literal("").setStyle(Platform.font(OppaClient.id("badge")));
		}
		return Component.empty().append(mark).append(" ").append(name);
	}

	public static boolean rides(Entity entity) {
		return entity.getRootVehicle() instanceof AbstractClientPlayer player && player != entity && has(player);
	}

	public static boolean marks(Entity entity) {
		return entity instanceof ArmorStand && stands.contains(entity.getId());
	}

	private static boolean nametagStand(ArmorStand stand) {
		return stand.isInvisible() && stand.hasCustomName() && stand.isCustomNameVisible();
	}

	private static void findStands(Minecraft mc) {
		stands.clear();
		if (mc.level == null || !Config.get().badges) return;
		for (AbstractClientPlayer p : mc.level.players()) {
			if (!has(p)) continue;
			double x = p.getX(), y = p.getY(), z = p.getZ();
			AABB area = new AABB(x - 0.6, y + 1.2, z - 0.6, x + 0.6, y + 4, z + 0.6);
			ArmorStand closest = null;
			for (ArmorStand stand : mc.level.getEntitiesOfClass(ArmorStand.class, area, Badges::nametagStand)) {
				if (closest == null || stand.getY() < closest.getY()) closest = stand;
			}
			if (closest != null) stands.add(closest.getId());
		}
	}

	public static void tick(Minecraft mc) {
		findStands(mc);
		if (++ticks % 40 != 0 || busy || mc.level == null || !(Config.get().badges || Config.get().cosmetics)) return;
		long now = System.currentTimeMillis();
		List<UUID> due = new ArrayList<>();
		for (AbstractClientPlayer p : mc.level.players()) {
			if (p == mc.player) continue;
			Long last = checked.get(p.getUUID());
			if (last == null || now - last > RECHECK) due.add(p.getUUID());
			if (due.size() == BATCH) break;
		}
		if (due.isEmpty()) return;
		if (checked.size() > 4096) checked.clear();
		for (UUID id : due) checked.put(id, now);
		lookup(due);
	}

	private static void lookup(List<UUID> ids) {
		JsonArray list = new JsonArray();
		for (UUID id : ids) list.add(id.toString().replace("-", ""));
		JsonObject body = new JsonObject();
		body.add("uuids", list);
		HttpRequest req = HttpRequest.newBuilder(LOOKUP)
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json")
				.header("User-Agent", "oppaclient/" + Platform.gameVersion())
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();
		busy = true;
		HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> {
			busy = false;
			if (err != null || res.statusCode() != 200) return;
			try {
				Set<String> yes = new HashSet<>();
				JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
				for (var e : json.getAsJsonArray("online")) yes.add(e.getAsString());
				JsonObject looks = json.has("looks") && json.get("looks").isJsonObject() ? json.getAsJsonObject("looks") : new JsonObject();
				for (UUID id : ids) {
					String key = id.toString().replace("-", "");
					if (yes.contains(key)) online.add(id);
					else online.remove(id);
					Looks.accept(id, looks.get(key));
				}
			} catch (RuntimeException e) {
				OppaClient.LOG.debug("badge lookup: {}", e.toString());
			}
		});
	}
}
