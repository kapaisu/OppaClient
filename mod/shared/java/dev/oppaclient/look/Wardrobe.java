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

	public static void upload(Path file) {
		if (!Api.signedIn()) {
			status = "Start Minecraft from the Oppa launcher to make capes";
			return;
		}
		if (uploading) return;
		uploading = true;
		status = "Making your cape";
		Thread.ofVirtual().start(() -> {
			try {
				long size = Files.size(file);
				if (size > 25L * 1024 * 1024) throw new IllegalArgumentException("That image is over 25 MB");
				Images.Image art = CapeArt.compose(Images.decode(Files.readAllBytes(file)));
				byte[] png = Images.png(art);
				status = "Uploading";
				JsonObject res = Api.upload("cape.php", "image/png", png).join();
				String url = res.get("url").getAsString();
				Looks.remember(url, art);
				int id = res.get("id").getAsInt();
				cape = id;
				adopted = true;
				dev.oppaclient.platform.Platform.showCape();
				Social.soon();
				status = "approved".equals(res.get("state").getAsString()) ? null : "Your cape is waiting for review. Only you can see it until then.";
			} catch (IOException | RuntimeException e) {
				status = "Couldn't make that cape: " + Api.message(e);
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
