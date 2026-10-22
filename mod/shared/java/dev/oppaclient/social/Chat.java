package dev.oppaclient.social;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Chat {
	public record Message(long id, String from, String text, long at) {}

	private static final int KEEP = 200;
	private static final Map<String, List<Message>> threads = new HashMap<>();
	private static final Map<String, Integer> unread = new HashMap<>();
	private static volatile Relay.Link link;
	private static volatile boolean started;
	private static volatile String me = "";
	private static volatile String problem;
	private static volatile String viewing;
	private static volatile long changed;

	private Chat() {}

	public static synchronized void start() {
		if (started || !Api.signedIn()) return;
		started = true;
		Thread.startVirtualThread(Chat::run);
	}

	public static boolean online() {
		return link != null;
	}

	public static String problem() {
		return problem;
	}

	public static long changed() {
		return changed;
	}

	public static boolean mine(Message m) {
		return m.from().equals(me);
	}

	public static synchronized List<Message> thread(String uuid) {
		return List.copyOf(threads.getOrDefault(Relay.plain(uuid), List.of()));
	}

	public static synchronized int unread(String uuid) {
		return unread.getOrDefault(Relay.plain(uuid), 0);
	}

	public static void open(String uuid) {
		String who = Relay.plain(uuid);
		viewing = who;
		synchronized (Chat.class) {
			unread.remove(who);
		}
		JsonObject history = new JsonObject();
		history.addProperty("op", "history");
		history.addProperty("with", who);
		write(history);
		JsonObject read = new JsonObject();
		read.addProperty("op", "read");
		read.addProperty("with", who);
		write(read);
	}

	public static void close() {
		viewing = null;
	}

	public static boolean send(String uuid, String text) {
		String body = text.strip();
		if (body.isEmpty() || link == null) return false;
		JsonObject msg = new JsonObject();
		msg.addProperty("op", "send");
		msg.addProperty("to", Relay.plain(uuid));
		msg.addProperty("text", body.length() > 500 ? body.substring(0, 500) : body);
		return write(msg);
	}

	private static boolean write(JsonObject msg) {
		Relay.Link l = link;
		if (l == null) return false;
		try {
			l.write(msg);
			return true;
		} catch (IOException e) {
			l.close();
			return false;
		}
	}

	private static void run() {
		int wait = 2;
		while (true) {
			try (Relay.Link l = Relay.open("chat", null)) {
				l.socket.setSoTimeout(90000);
				if (l.reply.has("uuid")) me = Relay.plain(l.reply.get("uuid").getAsString());
				link = l;
				problem = null;
				wait = 2;
				JsonObject ask = new JsonObject();
				ask.addProperty("op", "unread");
				l.write(ask);
				String open = viewing;
				if (open != null) open(open);
				while (true) handle(l, l.read());
			} catch (IOException e) {
				problem = e.getMessage();
			} catch (RuntimeException e) {
				problem = "Chat had a problem";
			}
			link = null;
			changed = System.nanoTime();
			try {
				Thread.sleep(wait * 1000L);
			} catch (InterruptedException e) {
				return;
			}
			wait = Math.min(60, wait * 2);
		}
	}

	private static void handle(Relay.Link l, JsonObject msg) throws IOException {
		String op = msg.has("op") ? msg.get("op").getAsString() : "";
		switch (op) {
			case "ping" -> {
				JsonObject pong = new JsonObject();
				pong.addProperty("op", "pong");
				l.write(pong);
			}
			case "msg" -> received(msg);
			case "history" -> history(msg);
			case "unread" -> counts(msg);
			case "error" -> Toasts.show("Message not sent", msg.has("error") ? msg.get("error").getAsString() : null, Toasts.Kind.INFO);
			default -> {}
		}
		changed = System.nanoTime();
	}

	private static Message message(JsonObject m) {
		return new Message(m.get("id").getAsLong(), Relay.plain(m.get("from").getAsString()), m.get("text").getAsString(), m.get("at").getAsLong());
	}

	private static void received(JsonObject m) {
		Message msg = message(m);
		String other = mine(msg) ? Relay.plain(m.get("to").getAsString()) : msg.from();
		synchronized (Chat.class) {
			List<Message> list = threads.computeIfAbsent(other, k -> new ArrayList<>());
			if (list.stream().noneMatch(x -> x.id() == msg.id())) list.add(msg);
			while (list.size() > KEEP) list.remove(0);
			if (!mine(msg) && !other.equals(viewing)) unread.merge(other, 1, Integer::sum);
		}
		if (mine(msg)) return;
		if (other.equals(viewing)) {
			JsonObject read = new JsonObject();
			read.addProperty("op", "read");
			read.addProperty("with", other);
			write(read);
		} else {
			String name = m.has("name") ? m.get("name").getAsString() : "A friend";
			Toasts.show(name, msg.text().length() > 60 ? msg.text().substring(0, 60) + "…" : msg.text(), Toasts.Kind.INFO);
		}
	}

	private static void history(JsonObject m) {
		String with = Relay.plain(m.get("with").getAsString());
		List<Message> list = new ArrayList<>();
		for (JsonElement e : m.getAsJsonArray("messages")) {
			JsonObject o = e.getAsJsonObject();
			list.add(new Message(o.get("id").getAsLong(), Relay.plain(o.get("from").getAsString()), o.get("text").getAsString(), o.get("at").getAsLong()));
		}
		synchronized (Chat.class) {
			List<Message> current = threads.getOrDefault(with, List.of());
			for (Message x : current) if (list.stream().noneMatch(y -> y.id() == x.id())) list.add(x);
			list.sort((a, b) -> Long.compare(a.id(), b.id()));
			threads.put(with, list);
		}
	}

	private static void counts(JsonObject m) {
		JsonObject c = m.getAsJsonObject("counts");
		synchronized (Chat.class) {
			unread.clear();
			for (String k : c.keySet()) {
				String who = Relay.plain(k);
				if (!who.equals(viewing)) unread.put(who, c.get(k).getAsInt());
			}
		}
	}
}
