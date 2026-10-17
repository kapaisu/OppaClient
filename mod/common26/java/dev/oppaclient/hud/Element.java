package dev.oppaclient.hud;

import dev.oppaclient.Config;
import dev.oppaclient.Paint;
import dev.oppaclient.Type;
import dev.oppaclient.platform.Gfx;
import java.util.List;
import net.minecraft.client.Minecraft;

public abstract class Element {
	public final String id;
	public final String label;
	private final boolean defaultOn;
	private final float ax, ay;
	private final int ox, oy;

	public sealed interface Option permits Toggle, Choice {
		String key();

		String label();
	}

	public record Toggle(String key, String label, boolean fallback) implements Option {}

	public record Choice(String key, String label, List<String> values) implements Option {}

	protected Element(String id, String label, boolean defaultOn, float ax, float ay, int ox, int oy) {
		this.id = id;
		this.label = label;
		this.defaultOn = defaultOn;
		this.ax = ax;
		this.ay = ay;
		this.ox = ox;
		this.oy = oy;
	}

	private Config.Placement placement;
	private List<Option> options;

	public Config.Placement placement() {
		if (placement == null) placement = Config.get().placement(id, defaultOn, ax, ay, ox, oy);
		return placement;
	}

	private List<Option> declared() {
		if (options == null) options = options();
		return options;
	}

	public void resetPosition() {
		Config.Placement p = placement();
		p.ax = ax;
		p.ay = ay;
		p.ox = ox;
		p.oy = oy;
		p.scale = 1f;
	}

	public List<Option> options() {
		return List.of();
	}

	public boolean on(String key) {
		String v = placement().options.get(key);
		if (v != null) return Boolean.parseBoolean(v);
		for (Option o : declared()) {
			if (o instanceof Toggle t && t.key().equals(key)) return t.fallback();
		}
		return false;
	}

	public void set(String key, boolean value) {
		placement().options.put(key, Boolean.toString(value));
	}

	public String pick(String key) {
		String v = placement().options.get(key);
		for (Option o : declared()) {
			if (o instanceof Choice c && c.key().equals(key)) {
				return v != null && c.values().contains(v) ? v : c.values().getFirst();
			}
		}
		return v == null ? "" : v;
	}

	public void set(String key, String value) {
		placement().options.put(key, value);
	}

	public boolean idle(Minecraft mc) {
		return false;
	}

	public abstract int width(Minecraft mc, boolean preview);

	public abstract int height(Minecraft mc, boolean preview);

	public abstract void draw(Gfx ctx, Minecraft mc, boolean preview);

	protected static void text(Gfx ctx, Type type, String s, int x, int y, float alpha) {
		type.draw(ctx, s, x, y, Paint.ink(alpha), Config.get().shadow);
	}
}
