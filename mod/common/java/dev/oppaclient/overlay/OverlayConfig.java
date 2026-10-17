package dev.oppaclient.overlay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.oppaclient.OppaClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public final class OverlayConfig {
	public static final OverlayConfig DEFAULTS = new OverlayConfig();

	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("oppaclient-overlay.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static OverlayConfig instance = new OverlayConfig();

	public boolean enabled = true;

	public boolean blocks = true;
	public boolean entities = true;
	public boolean entityOwnColor = false;
	@JsonAdapter(Hex.class)
	public int entityColor = 0xFFB13B;
	public boolean hideWithHud = true;

	public float thickness = 0.012f;
	public float coreHeat = 0.6f;
	public float haloRadius = 0.12f;
	public float haloStrength = 0.55f;
	public float falloff = 3.0f;
	public float brightness = 1.0f;
	public float inflate = 0.004f;
	public float distanceScaling = 0.5f;

	public ColorMode colorMode = ColorMode.SOLID;
	@JsonAdapter(Hex.class)
	public int primary = 0xFF2F6E;
	@JsonAdapter(Hex.class)
	public int secondary = 0x2FD8FF;
	public Gas gas = Gas.NEON;
	public float flowSpeed = 0.35f;
	public float flowScale = 1.0f;

	public Strike strike = Strike.ON_ACQUIRE;
	public float strikeTime = 0.32f;
	public float flicker = 0.06f;
	public float breathe = 0.1f;
	public float breatheRate = 0.5f;
	public float fadeOut = 0.16f;
	public boolean miningCharge = true;
	public boolean breakBurst = true;

	public Motion motion = Motion.GLIDE;
	public float response = 6.0f;
	public float bounce = 0.4f;
	public float glideRange = 3.0f;

	public float spill = 0.3f;
	public float spillReach = 0.3f;
	public float fill = 0.0f;
	public float throughWalls = 0.0f;
	public Blend blend = Blend.ADDITIVE;
	public float shaderBoost = 1.5f;

	public static OverlayConfig get() {
		return instance;
	}

	public static void load() {
		if (!Files.exists(PATH)) {
			instance = new OverlayConfig();
			return;
		}
		try (Reader reader = Files.newBufferedReader(PATH)) {
			OverlayConfig loaded = GSON.fromJson(reader, OverlayConfig.class);
			instance = loaded != null ? loaded : new OverlayConfig();
		} catch (IOException | JsonParseException e) {
			OppaClient.LOG.warn("Could not read {}, using defaults", PATH, e);
			instance = new OverlayConfig();
		}
		OverlaySettings.sanitize(instance);
	}

	public static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			Path tmp = PATH.resolveSibling("oppaclient-overlay.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp)) {
				GSON.toJson(instance, writer);
			}
			Files.move(tmp, PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			OppaClient.LOG.warn("Could not write {}", PATH, e);
		}
	}

	static final class Hex extends TypeAdapter<Integer> {
		@Override
		public void write(JsonWriter out, Integer value) throws IOException {
			out.value(String.format(Locale.ROOT, "#%06X", value & 0xFFFFFF));
		}

		@Override
		public Integer read(JsonReader in) throws IOException {
			String s = in.nextString().trim();
			if (s.startsWith("#")) {
				s = s.substring(1);
			}
			try {
				return Integer.parseInt(s, 16) & 0xFFFFFF;
			} catch (NumberFormatException e) {
				throw new JsonParseException("Bad color " + s, e);
			}
		}
	}
}
