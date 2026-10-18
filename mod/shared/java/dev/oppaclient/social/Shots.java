package dev.oppaclient.social;

import dev.oppaclient.look.Images;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class Shots {
	private static final int LIMIT = 7 * 1024 * 1024;
	private static final Map<Path, String> links = new ConcurrentHashMap<>();
	private static Path dir;
	private static FileTime dirTime;
	private static long newestSeen, lastCheck;
	private static volatile Path latest;
	private static volatile boolean uploading;

	private Shots() {}

	public static Path latest() {
		return latest;
	}

	public static void watch(Path folder, String keyName) {
		long now = System.currentTimeMillis();
		if (now - lastCheck < 750) return;
		lastCheck = now;
		try {
			if (!folder.equals(dir)) {
				dir = folder;
				dirTime = null;
				newestSeen = now;
			}
			if (!Files.isDirectory(folder)) return;
			FileTime t = Files.getLastModifiedTime(folder);
			if (t.equals(dirTime)) return;
			dirTime = t;
			Path found = null;
			long best = newestSeen;
			try (Stream<Path> files = Files.list(folder)) {
				for (Path p : (Iterable<Path>) files::iterator) {
					String n = p.getFileName().toString().toLowerCase();
					if (!n.endsWith(".png")) continue;
					long m = Files.getLastModifiedTime(p).toMillis();
					if (m > best) {
						best = m;
						found = p;
					}
				}
			}
			if (found != null) {
				newestSeen = best;
				latest = found;
				Toasts.show("Screenshot saved", Api.signedIn() ? "Press " + keyName + " to share a link" : found.getFileName().toString(), Toasts.Kind.SHOT);
			}
		} catch (IOException ignored) {
		}
	}

	public static void share(Path shot, Consumer<String> clipboard) {
		if (shot == null) {
			Toasts.show("No screenshot yet", "Take one first", Toasts.Kind.INFO);
			return;
		}
		if (!Api.signedIn()) {
			Toasts.show("Can't share", "Start Minecraft from the Oppa launcher", Toasts.Kind.INFO);
			return;
		}
		String known = links.get(shot);
		if (known != null) {
			clipboard.accept(known);
			Toasts.show("Link copied", known.replace("https://", ""), Toasts.Kind.INFO);
			return;
		}
		if (uploading) return;
		uploading = true;
		Toasts.show("Uploading screenshot", null, Toasts.Kind.INFO);
		Thread.ofVirtual().start(() -> {
			try {
				byte[] data = Files.readAllBytes(shot);
				String type = "image/png";
				if (data.length > LIMIT) {
					Images.Image img = Images.decode(data);
					if (img.width() > 2560) img = Images.resample(img, 0, 0, img.width(), img.height(), 2560, img.height() * 2560 / img.width());
					data = Images.jpeg(img, 90);
					type = "image/jpeg";
				}
				String url = Api.upload("shot.php", type, data).join().get("url").getAsString();
				links.put(shot, url);
				clipboard.accept(url);
				Toasts.dismiss("Uploading screenshot");
				Toasts.show("Link copied", url.replace("https://", ""), Toasts.Kind.INFO);
			} catch (IOException | RuntimeException e) {
				Toasts.dismiss("Uploading screenshot");
				Toasts.show("Couldn't share", Api.message(e), Toasts.Kind.INFO);
			} finally {
				uploading = false;
			}
		});
	}
}
