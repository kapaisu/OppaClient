package dev.oppaclient.social;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class Hosting {
	public static final String SCHEME = "oppa:";

	private static volatile boolean on;
	private static volatile int lanPort;
	private static volatile String self;
	private static volatile String problem;
	private static volatile Relay.Link control;
	private static volatile String visiting, visitingLocal;
	private static final Set<Socket> guests = ConcurrentHashMap.newKeySet();

	private Hosting() {}

	public static boolean active() {
		return on;
	}

	public static boolean ready() {
		return on && control != null;
	}

	public static int guests() {
		return guests.size();
	}

	public static String problem() {
		return problem;
	}

	public static String address() {
		return self == null ? null : SCHEME + self;
	}

	public static int freePort() {
		try (ServerSocket probe = new ServerSocket(0)) {
			return probe.getLocalPort();
		} catch (IOException e) {
			return -1;
		}
	}

	public static synchronized void start(int port) {
		if (on) return;
		on = true;
		lanPort = port;
		problem = null;
		Thread.startVirtualThread(Hosting::run);
	}

	public static synchronized void stop() {
		if (!on) return;
		on = false;
		Relay.Link c = control;
		control = null;
		if (c != null) c.close();
		for (Socket s : guests) {
			try {
				s.close();
			} catch (IOException ignored) {
			}
		}
		guests.clear();
	}

	private static void run() {
		int wait = 2;
		while (on) {
			try (Relay.Link link = Relay.open("host", null)) {
				link.socket.setSoTimeout(90000);
				control = link;
				if (link.reply.has("uuid")) self = Relay.plain(link.reply.get("uuid").getAsString());
				problem = null;
				wait = 2;
				serve(link);
			} catch (IOException e) {
				control = null;
				if (!on) return;
				problem = e.getMessage();
			}
			control = null;
			try {
				Thread.sleep(wait * 1000L);
			} catch (InterruptedException e) {
				return;
			}
			wait = Math.min(30, wait * 2);
		}
	}

	private static void serve(Relay.Link link) throws IOException {
		while (on) {
			JsonObject msg = link.read();
			String op = msg.has("op") ? msg.get("op").getAsString() : "";
			if (op.equals("ping")) {
				JsonObject pong = new JsonObject();
				pong.addProperty("op", "pong");
				link.write(pong);
			} else if (op.equals("guest")) {
				String ticket = msg.get("ticket").getAsString();
				String uuid = Relay.plain(msg.get("uuid").getAsString());
				String name = msg.has("name") ? msg.get("name").getAsString() : "A friend";
				if (friend(uuid)) {
					Toasts.show(name + " is joining", null, Toasts.Kind.INFO);
					Thread.startVirtualThread(() -> admit(ticket, name));
				} else {
					JsonObject deny = new JsonObject();
					deny.addProperty("op", "deny");
					deny.addProperty("ticket", ticket);
					link.write(deny);
				}
			}
		}
	}

	private static boolean friend(String uuid) {
		for (Social.Friend f : Social.state().friends()) {
			if (Relay.plain(f.uuid()).equals(uuid)) return true;
		}
		return false;
	}

	private static void admit(String ticket, String name) {
		JsonObject hello = new JsonObject();
		hello.addProperty("ticket", ticket);
		try {
			Relay.Link link = Relay.open("accept", hello);
			Socket local = new Socket(InetAddress.getLoopbackAddress(), lanPort);
			local.setTcpNoDelay(true);
			guests.add(local);
			Relay.pipe(link, local, () -> guests.remove(local));
		} catch (IOException e) {
			Toasts.show("Couldn't let " + name + " in", e.getMessage(), Toasts.Kind.INFO);
		}
	}

	public static boolean redirect(String address, Consumer<String> connect) {
		if (address == null || !address.startsWith(SCHEME)) return false;
		String host = Relay.plain(address.substring(SCHEME.length()));
		Toasts.show("Joining world", "Asking the host to let you in", Toasts.Kind.INFO);
		Thread.startVirtualThread(() -> {
			JsonObject hello = new JsonObject();
			hello.addProperty("host", host);
			Relay.Link link = null;
			try (ServerSocket door = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
				link = Relay.open("join", hello);
				door.setSoTimeout(30000);
				String local = "127.0.0.1:" + door.getLocalPort();
				visiting = SCHEME + host;
				visitingLocal = local;
				connect.accept(local);
				Socket game = door.accept();
				game.setTcpNoDelay(true);
				Relay.pipe(link, game, () -> {
					if (local.equals(visitingLocal)) {
						visiting = null;
						visitingLocal = null;
					}
				});
			} catch (IOException e) {
				if (link != null) link.close();
				visiting = null;
				visitingLocal = null;
				Toasts.show("Couldn't join", e.getMessage(), Toasts.Kind.INFO);
			}
		});
		return true;
	}

	public static String label(String address) {
		String v = visiting;
		return v != null && address != null && address.equals(visitingLocal) ? v : address;
	}
}
