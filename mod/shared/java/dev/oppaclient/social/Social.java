package dev.oppaclient.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class Social {
	public record Friend(String uuid, String name, String status, String server, String game, long seen) {
		public boolean online() {
			return !status.equals("offline");
		}

		public boolean joinable() {
			return status.equals("server") && !server.isEmpty();
		}

		public String describe() {
			String v = game.isEmpty() ? "" : " | " + game;
			return switch (status) {
				case "server" -> "On " + server + v;
				case "singleplayer" -> "Singleplayer" + v;
				case "multiplayer" -> "Multiplayer" + v;
				case "menu" -> "In game" + v;
				case "launcher" -> "In the launcher";
				default -> seen > 0 ? "Last online " + ago(System.currentTimeMillis() / 1000 - seen) : "Offline";
			};
		}
	}

	public record Person(String uuid, String name) {}

	public record Invite(long id, String uuid, String name, String server, String game) {}

	public record Cape(int id, String hash, String state, String url) {}

	public record State(List<Friend> friends, List<Person> incoming, List<Person> outgoing, List<Invite> invites,
			Integer cape, String hat, String wings, String aura, List<Cape> capes) {
		static final State EMPTY = new State(List.of(), List.of(), List.of(), List.of(), null, "", "", "", List.of());
	}

	public record Found(boolean oppa, String uuid, String name, boolean me, boolean friend, boolean requested, boolean wants) {}

	private static volatile State state = State.EMPTY;
	private static volatile boolean loaded, busy;
	private static volatile String problem;
	private static long last;
	private static boolean first = true;
	private static final Set<Long> seenInvites = new HashSet<>();
	private static final Set<String> seenRequests = new HashSet<>();
	private static volatile long wantSoon;

	private Social() {}

	public static State state() {
		return state;
	}

	public static boolean loaded() {
		return loaded;
	}

	public static String problem() {
		return problem;
	}

	public static void soon() {
		wantSoon = System.currentTimeMillis();
	}

	public static void tick(long intervalMs) {
		if (!Api.signedIn() || busy) return;
		long now = System.currentTimeMillis();
		if (now - last < intervalMs && wantSoon < last) return;
		last = now;
		busy = true;
		Api.post("me.php", new JsonObject()).whenComplete((json, err) -> {
			busy = false;
			if (err != null) {
				problem = Api.message(err);
				return;
			}
			problem = null;
			State next = parse(json);
			announce(next);
			state = next;
			loaded = true;
		});
	}

	private static void announce(State next) {
		boolean quiet = first;
		first = false;
		for (Invite i : next.invites()) {
			if (seenInvites.add(i.id()) && !quiet) {
				Toasts.show(i.name() + " invited you", i.server(), Toasts.Kind.INVITE);
			}
		}
		for (Person p : next.incoming()) {
			if (seenRequests.add(p.uuid()) && !quiet) {
				Toasts.show(p.name() + " wants to be friends", "Open the Oppa menu to accept", Toasts.Kind.REQUEST);
			}
		}
	}

	private static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}

	private static long num(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? 0 : e.getAsLong();
	}

	private static List<JsonObject> list(JsonObject o, String k) {
		List<JsonObject> out = new ArrayList<>();
		JsonElement e = o.get(k);
		if (e instanceof JsonArray a) for (JsonElement x : a) if (x.isJsonObject()) out.add(x.getAsJsonObject());
		return out;
	}

	private static State parse(JsonObject j) {
		List<Friend> friends = new ArrayList<>();
		for (JsonObject f : list(j, "friends")) {
			friends.add(new Friend(str(f, "uuid"), str(f, "name"), str(f, "status"), str(f, "server"), str(f, "game"), num(f, "seen")));
		}
		friends.sort((a, b) -> rank(a.status()) != rank(b.status()) ? rank(a.status()) - rank(b.status()) : a.name().compareToIgnoreCase(b.name()));
		List<Person> incoming = new ArrayList<>(), outgoing = new ArrayList<>();
		for (JsonObject p : list(j, "incoming")) incoming.add(new Person(str(p, "uuid"), str(p, "name")));
		for (JsonObject p : list(j, "outgoing")) outgoing.add(new Person(str(p, "uuid"), str(p, "name")));
		List<Invite> invites = new ArrayList<>();
		for (JsonObject i : list(j, "invites")) {
			invites.add(new Invite(num(i, "id"), str(i, "uuid"), str(i, "name"), str(i, "server"), str(i, "game")));
		}
		JsonObject look = j.has("look") && j.get("look").isJsonObject() ? j.getAsJsonObject("look") : new JsonObject();
		Integer cape = look.has("cape") && !look.get("cape").isJsonNull() ? look.get("cape").getAsInt() : null;
		List<Cape> capes = new ArrayList<>();
		for (JsonObject c : list(j, "capes")) capes.add(new Cape((int) num(c, "id"), str(c, "hash"), str(c, "state"), str(c, "url")));
		return new State(List.copyOf(friends), List.copyOf(incoming), List.copyOf(outgoing), List.copyOf(invites),
				cape, str(look, "hat"), str(look, "wings"), str(look, "aura"), List.copyOf(capes));
	}

	private static int rank(String status) {
		return switch (status) {
			case "server" -> 0;
			case "singleplayer", "multiplayer", "menu" -> 1;
			case "launcher" -> 2;
			default -> 3;
		};
	}

	public static String ago(long secs) {
		if (secs < 120) return "just now";
		if (secs < 3600) return secs / 60 + " min ago";
		if (secs < 86400) return secs / 3600 + " h ago";
		if (secs < 172800) return "yesterday";
		return secs / 86400 + " days ago";
	}

	public static CompletableFuture<Found> lookup(String name) {
		JsonObject body = new JsonObject();
		body.addProperty("name", name);
		return Api.post("lookup.php", body).thenApply(j -> new Found(
				j.has("oppa") && j.get("oppa").getAsBoolean(), str(j, "uuid"), str(j, "name"),
				j.has("me") && j.get("me").getAsBoolean(), j.has("friend") && j.get("friend").getAsBoolean(),
				j.has("requested") && j.get("requested").getAsBoolean(), j.has("wants") && j.get("wants").getAsBoolean()));
	}

	public static CompletableFuture<String> friend(String action, String uuid) {
		JsonObject body = new JsonObject();
		body.addProperty("action", action);
		body.addProperty("uuid", uuid);
		return Api.post("friend.php", body).thenApply(j -> {
			soon();
			return str(j, "state");
		});
	}

	public static CompletableFuture<Void> invite(String uuid, String server) {
		JsonObject body = new JsonObject();
		body.addProperty("uuid", uuid);
		if (server != null) body.addProperty("server", server);
		return Api.post("invite.php", body).thenApply(j -> null);
	}
}
