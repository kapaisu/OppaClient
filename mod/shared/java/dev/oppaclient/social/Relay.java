package dev.oppaclient.social;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HexFormat;

final class Relay {
	private static final String PIN = "c834003bd0436b8eeb64d8aadc552ecef1c9c7859022de9bdbccbd6fd81166d0";
	private static volatile InetSocketAddress where;

	private Relay() {}

	static final class Link implements Closeable {
		final Socket socket;
		final InputStream in;
		final OutputStream out;
		JsonObject reply = new JsonObject();

		Link(Socket socket) throws IOException {
			this.socket = socket;
			in = socket.getInputStream();
			out = socket.getOutputStream();
		}

		JsonObject read() throws IOException {
			ByteArrayOutputStream line = new ByteArrayOutputStream(256);
			while (true) {
				int b = in.read();
				if (b < 0) throw new IOException("Oppa relay closed the connection");
				if (b == '\n') break;
				line.write(b);
				if (line.size() > 65536) throw new IOException("Oppa relay sent too much");
			}
			return JsonParser.parseString(line.toString(StandardCharsets.UTF_8)).getAsJsonObject();
		}

		synchronized void write(JsonObject msg) throws IOException {
			out.write((msg + "\n").getBytes(StandardCharsets.UTF_8));
			out.flush();
		}

		@Override
		public void close() {
			try {
				socket.close();
			} catch (IOException ignored) {
			}
		}
	}

	static Link open(String op, JsonObject extra) throws IOException {
		String token = Api.token();
		if (token == null || !Api.signedIn()) throw new IOException("Start Minecraft from the Oppa launcher");
		String over = System.getenv("OPPA_RELAY");
		boolean local = over != null && over.startsWith("127.0.0.1:");
		InetSocketAddress address = local ? new InetSocketAddress("127.0.0.1", Integer.parseInt(over.substring(over.indexOf(':') + 1))) : where();
		String pin = local && System.getenv("OPPA_RELAY_PIN") != null ? System.getenv("OPPA_RELAY_PIN") : PIN;
		SSLContext ctx;
		try {
			ctx = SSLContext.getInstance("TLS");
			ctx.init(null, new TrustManager[] {pinned(pin)}, null);
		} catch (Exception e) {
			throw new IOException("Couldn't set up a secure connection", e);
		}
		Socket plain = new Socket();
		SSLSocket socket;
		try {
			plain.connect(address, 8000);
			plain.setTcpNoDelay(true);
			socket = (SSLSocket) ctx.getSocketFactory().createSocket(plain, address.getHostString(), address.getPort(), true);
			socket.setSoTimeout(30000);
			socket.startHandshake();
		} catch (SSLException e) {
			plain.close();
			throw new IOException("Your network or antivirus is blocking Oppa. Try another network or allow Minecraft through it.", e);
		} catch (IOException e) {
			plain.close();
			throw new IOException("Couldn't reach Oppa. Check your internet connection.", e);
		}
		Link link = new Link(socket);
		JsonObject hello = extra == null ? new JsonObject() : extra.deepCopy();
		hello.addProperty("op", op);
		hello.addProperty("token", token);
		link.write(hello);
		JsonObject reply = link.read();
		if (!reply.has("ok") || !reply.get("ok").getAsBoolean()) {
			link.close();
			throw new IOException(reply.has("error") ? reply.get("error").getAsString() : "Oppa relay said no");
		}
		socket.setSoTimeout(0);
		link.reply = reply;
		return link;
	}

	private static InetSocketAddress where() throws IOException {
		InetSocketAddress known = where;
		if (known != null && !known.isUnresolved()) return known;
		try {
			JsonObject j = Api.post("relay.php", new JsonObject()).get(15, java.util.concurrent.TimeUnit.SECONDS);
			String host = j.get("host").getAsString();
			int port = j.get("port").getAsInt();
			if (!host.matches("[A-Za-z0-9.-]{1,253}") || port < 1 || port > 65535) throw new IOException("bad relay address");
			known = new InetSocketAddress(host, port);
		} catch (Exception e) {
			throw new IOException("Couldn't reach Oppa");
		}
		if (known.isUnresolved()) throw new IOException("Couldn't reach Oppa");
		where = known;
		return known;
	}

	private static X509TrustManager pinned(String pin) {
		return new X509TrustManager() {
			@Override
			public void checkClientTrusted(X509Certificate[] chain, String auth) throws CertificateException {
				throw new CertificateException("not a server");
			}

			@Override
			public void checkServerTrusted(X509Certificate[] chain, String auth) throws CertificateException {
				if (chain == null || chain.length == 0 || pin.isEmpty()) throw new CertificateException("no relay certificate");
				try {
					String got = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded()));
					if (!MessageDigest.isEqual(got.getBytes(StandardCharsets.US_ASCII), pin.getBytes(StandardCharsets.US_ASCII))) {
						throw new CertificateException("Oppa relay certificate doesn't match");
					}
				} catch (java.security.NoSuchAlgorithmException e) {
					throw new CertificateException(e);
				}
			}

			@Override
			public X509Certificate[] getAcceptedIssuers() {
				return new X509Certificate[0];
			}
		};
	}

	static void pipe(Link link, Socket local, Runnable done) {
		Runnable close = () -> {
			link.close();
			try {
				local.close();
			} catch (IOException ignored) {
			}
		};
		Thread.startVirtualThread(() -> {
			try {
				link.in.transferTo(local.getOutputStream());
			} catch (IOException ignored) {
			}
			close.run();
		});
		Thread.startVirtualThread(() -> {
			try {
				local.getInputStream().transferTo(link.out);
			} catch (IOException ignored) {
			}
			close.run();
			done.run();
		});
	}

	static String plain(String uuid) {
		return uuid == null ? "" : uuid.replace("-", "").toLowerCase(java.util.Locale.ROOT);
	}
}
