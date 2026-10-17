package dev.oppaclient;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oppaclient.platform.Platform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.Box;

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

public final class Badges {
	private static final URI LOOKUP = URI.create("https://oppaclient.com/api/users.php");
	private static final long RECHECK = 60_000;
	private static final int BATCH = 100;

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static final Set<UUID> online = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, Long> checked = new ConcurrentHashMap<>();
	private static Text mark;
	private static volatile boolean busy;
	private static int ticks;
	private static final Set<Integer> stands = new HashSet<>();

	public static boolean has(AbstractClientPlayerEntity player) {
		if (!Config.get().badges) return false;
		MinecraftClient mc = MinecraftClient.getInstance();
		return player == mc.player || online.contains(player.getUuid());
	}

	public static Text decorate(Text name) {
		if (mark == null) {
			mark = Text.literal("").setStyle(Platform.font(OppaClient.id("badge")));
		}
		return Text.empty().append(mark).append(" ").append(name);
	}

	public static boolean rides(Entity entity) {
		return entity.getRootVehicle() instanceof AbstractClientPlayerEntity player && player != entity && has(player);
	}

	public static boolean marks(Entity entity) {
		return entity instanceof ArmorStandEntity && stands.contains(entity.getId());
	}

	private static boolean nametagStand(ArmorStandEntity stand) {
		return stand.isInvisible() && stand.hasCustomName() && stand.isCustomNameVisible();
	}

	private static void findStands(MinecraftClient mc) {
		stands.clear();
		if (mc.world == null || !Config.get().badges) return;
		for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
			if (!has(p)) continue;
			double x = p.getX(), y = p.getY(), z = p.getZ();
			Box area = new Box(x - 0.6, y + 1.2, z - 0.6, x + 0.6, y + 4, z + 0.6);
			ArmorStandEntity closest = null;
			for (ArmorStandEntity stand : mc.world.getEntitiesByClass(ArmorStandEntity.class, area, Badges::nametagStand)) {
				if (closest == null || stand.getY() < closest.getY()) closest = stand;
			}
			if (closest != null) stands.add(closest.getId());
		}
	}

	public static void tick(MinecraftClient mc) {
		findStands(mc);
		if (++ticks % 40 != 0 || busy || mc.world == null || !Config.get().badges) return;
		long now = System.currentTimeMillis();
		List<UUID> due = new ArrayList<>();
		for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
			if (p == mc.player) continue;
			Long last = checked.get(p.getUuid());
			if (last == null || now - last > RECHECK) due.add(p.getUuid());
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
				for (var e : JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonArray("online")) yes.add(e.getAsString());
				for (UUID id : ids) {
					if (yes.contains(id.toString().replace("-", ""))) online.add(id);
					else online.remove(id);
				}
			} catch (RuntimeException e) {
				OppaClient.LOG.debug("badge lookup: {}", e.toString());
			}
		});
	}
}
