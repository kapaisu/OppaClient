package dev.oppaclient.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.oppaclient.look.Images;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

public final class Library {
	public static final class Saved {
		public String file;
		public String variant;
		public long added;

		public boolean slim() {
			return "slim".equals(variant);
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path DIR = dir();

	private Library() {}

	private static Path dir() {
		String over = System.getenv("OPPA_SKINS");
		return over == null || over.isBlank() ? null : Path.of(over);
	}

	public static boolean available() {
		return DIR != null;
	}

	public static Path path(Saved s) {
		return DIR.resolve(s.file);
	}

	public static List<Saved> list() {
		if (DIR == null) return List.of();
		try {
			List<Saved> out = GSON.fromJson(Files.readString(DIR.resolve("library.json")), new TypeToken<List<Saved>>() {}.getType());
			if (out == null) return List.of();
			out.removeIf(s -> s == null || s.file == null || !Files.isRegularFile(DIR.resolve(s.file)));
			out.sort(Comparator.comparingLong((Saved s) -> s.added).reversed());
			return out;
		} catch (IOException | RuntimeException e) {
			return List.of();
		}
	}

	public static Images.Image check(byte[] png) {
		Images.Image img = Images.decode(png);
		if (img.width() != 64 || (img.height() != 64 && img.height() != 32)) {
			throw new IllegalArgumentException("Skins are 64×64 pixels. This one is " + img.width() + "×" + img.height() + ".");
		}
		return img;
	}

	public static Saved remember(byte[] png, boolean slim) throws IOException {
		if (DIR == null) throw new IOException("Start Minecraft from the Oppa launcher to keep skins");
		Files.createDirectories(DIR);
		String file;
		try {
			file = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(png)) + ".png";
		} catch (NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
		Path target = DIR.resolve(file);
		if (!Files.exists(target)) Files.write(target, png);
		List<Saved> all = new ArrayList<>(list());
		all.removeIf(s -> s.file.equals(file));
		Saved saved = new Saved();
		saved.file = file;
		saved.variant = slim ? "slim" : "classic";
		saved.added = System.currentTimeMillis() / 1000;
		all.add(saved);
		Path tmp = DIR.resolve("library.json.part");
		Files.writeString(tmp, GSON.toJson(all));
		Files.move(tmp, DIR.resolve("library.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		return saved;
	}
}
