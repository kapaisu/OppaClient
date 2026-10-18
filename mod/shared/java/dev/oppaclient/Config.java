package dev.oppaclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("oppaclient.json");
	private static Config current;

	public static final class Placement {
		public boolean enabled;
		public float ax;
		public float ay;
		public int ox;
		public int oy;

		public float scale = 1f;
		public Map<String, String> options = new LinkedHashMap<>();

		@SuppressWarnings("unused")
		private Placement() {}

		Placement(boolean enabled, float ax, float ay, int ox, int oy) {
			this.enabled = enabled;
			this.ax = ax;
			this.ay = ay;
			this.ox = ox;
			this.oy = oy;
		}
	}

	public Map<String, Placement> hud = new LinkedHashMap<>();
	public float scale = 1f;
	public boolean shadow = true;
	public boolean backdrop = false;
	public boolean oppaFont = true;

	public boolean zoom = true;
	public float zoomFactor = 4f;
	public boolean zoomSmooth = true;
	public boolean zoomScroll = true;
	public boolean zoomCinematic = false;

	public float lowFire = 0f;
	public String time = "Off";
	public boolean clearWeather = false;

	public boolean freelook = true;
	public boolean freelookToggle = false;

	public boolean crosshair = false;
	public String crosshairStyle = "Plus";
	public float crosshairSize = 5f;
	public float crosshairGap = 2f;
	public float crosshairThickness = 1f;
	public boolean crosshairOutline = true;
	public int crosshairColor = 0xFFFFFF;

	public boolean fullbright = false;
	public boolean ownNametag = false;
	public boolean badges = true;
	public boolean cosmetics = true;
	public boolean hudHidden = false;

	public int backgroundFps = 30;
	public int menuFps = 60;

	public static Config get() {
		if (current == null) {
			current = load();
		}
		return current;
	}

	public Placement placement(String id, boolean enabled, float ax, float ay, int ox, int oy) {
		return hud.computeIfAbsent(id, k -> new Placement(enabled, ax, ay, ox, oy));
	}

	private static Config load() {
		try {
			if (Files.exists(FILE)) {
				Config c = GSON.fromJson(Files.readString(FILE), Config.class);
				if (c != null) {
					if (c.hud == null) c.hud = new LinkedHashMap<>();
					for (Placement p : c.hud.values()) {
						if (p.options == null) p.options = new LinkedHashMap<>();
						if (p.scale <= 0) p.scale = 1f;
					}
					return c;
				}
			}
		} catch (IOException | RuntimeException e) {
			OppaClient.LOG.warn("couldn't read {}, starting fresh", FILE, e);
		}
		return new Config();
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			Path tmp = FILE.resolveSibling("oppaclient.json.tmp");
			Files.writeString(tmp, GSON.toJson(this));
			Files.move(tmp, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			OppaClient.LOG.error("couldn't save {}", FILE, e);
		}
	}
}
