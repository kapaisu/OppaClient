package dev.oppaclient.look;

import com.google.gson.JsonObject;
import dev.oppaclient.social.Api;
import dev.oppaclient.social.Social;
import dev.oppaclient.social.Toasts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Wardrobe {
	private static Look hat = Look.NONE, wings = Look.NONE, aura = Look.NONE;
	private static volatile Integer cape;
	private static volatile boolean adopted, dirty;
	private static long changedAt;
	private static volatile boolean uploading;
	private static volatile String status;

	private Wardrobe() {}

	public static void reset() {
		hat = Look.NONE;
		wings = Look.NONE;
		aura = Look.NONE;
		cape = null;
		dirty = false;
		adopted = false;
	}

	public static Look hat() {
		return hat;
	}

	public static Look wings() {
		return wings;
	}

	public static Look aura() {
		return aura;
	}

	public static Integer cape() {
		return cape;
	}

	public static boolean uploading() {
		return uploading;
	}

	public static String status() {
		Social.Cape c = capeEntry();
		if (status != null && status.contains("review") && c != null && c.state().equals("approved")) status = null;
		return status;
	}

	public static Social.Cape capeEntry() {
		if (cape == null) return null;
		for (Social.Cape c : Social.state().capes()) if (c.id() == cape) return c;
		return null;
	}

	public static Looks.Worn worn() {
		Social.Cape c = capeEntry();
		return new Looks.Worn(hat, wings, aura, c == null ? null : c.url());
	}

	public static void hat(Look l) {
		hat = l;
		touch();
	}

	public static void wings(Look l) {
		wings = l;
		touch();
	}

	public static void aura(Look l) {
		aura = l;
		touch();
	}

	public static void cape(Integer id) {
		cape = id;
		if (id != null) dev.oppaclient.platform.Platform.showCape();
		touch();
	}

	private static void touch() {
		adopted = true;
		dirty = true;
		changedAt = System.currentTimeMillis();
	}

	public static void tick() {
		if (!adopted && Social.loaded()) {
			Social.State s = Social.state();
			hat = Look.parse(s.hat(), Catalog.HATS);
			wings = Look.parse(s.wings(), Catalog.WINGS);
			aura = Look.parse(s.aura(), Catalog.AURAS);
			cape = s.cape();
			adopted = true;
			if (cape != null) dev.oppaclient.platform.Platform.showCape();
		}
		if (dirty && System.currentTimeMillis() - changedAt > 900 && Api.signedIn()) {
			dirty = false;
			JsonObject body = new JsonObject();
			if (cape != null) body.addProperty("cape", cape);
			body.addProperty("hat", hat.encode());
			body.addProperty("wings", wings.encode());
			body.addProperty("aura", aura.encode());
			Api.post("look.php", body).whenComplete((j, err) -> {
				if (err != null) Toasts.show("Couldn't save your look", Api.message(err), Toasts.Kind.INFO);
			});
		}
	}

	public static void open(Path file, java.util.function.Consumer<CapeEditor> ready) {
		if (!Api.signedIn()) {
			status = "Start Minecraft from the Oppa launcher to make capes";
			return;
		}
		status = "Opening the image";
		Thread.ofVirtual().start(() -> {
			try {
				if (Files.size(file) > 25L * 1024 * 1024) throw new IllegalArgumentException("That image is over 25 MB");
				CapeEditor editor = new CapeEditor(Images.decode(Files.readAllBytes(file)));
				status = null;
				ready.accept(editor);
			} catch (IOException | RuntimeException e) {
				status = "Couldn't open that image: " + Api.message(e);
			}
		});
	}

	public static void upload(Images.Image art) {
		if (uploading) return;
		uploading = true;
		status = "Uploading";
		Thread.ofVirtual().start(() -> {
			try {
				JsonObject res = Api.upload("cape.php", "image/png", Images.png(art)).join();
				String url = res.get("url").getAsString();
				Looks.remember(url, art);
				cape = res.get("id").getAsInt();
				adopted = true;
				dev.oppaclient.platform.Platform.showCape();
				Social.soon();
				status = "approved".equals(res.get("state").getAsString()) ? null : "Your cape is waiting for review. Only you can see it until then.";
			} catch (RuntimeException e) {
				status = "Couldn't upload that cape: " + Api.message(e);
			} finally {
				uploading = false;
			}
		});
	}

	public static void delete(Social.Cape c) {
		JsonObject body = new JsonObject();
		body.addProperty("action", "delete");
		body.addProperty("id", c.id());
		Api.post("cape.php", body).whenComplete((j, err) -> {
			if (err != null) status = Api.message(err);
			Social.soon();
		});
		if (cape != null && cape == c.id()) cape = null;
	}
}
