package dev.oppaclient.hud;

import dev.oppaclient.Type;
import dev.oppaclient.platform.Gfx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class Effects extends Element {
	private static final int ROW = 20;

	Effects() {
		super("effects", "Effects", true, 1, 0, -6, 36);
	}

	@Override
	public List<Option> options() {
		return List.of(new Toggle("icons", "Icons", true), new Toggle("time", "Time left", true));
	}

	private static final List<StatusEffectInstance> active = new ArrayList<>();
	private static long activeFrame = -1;
	private static boolean activePreview;

	private static List<StatusEffectInstance> active(MinecraftClient mc, boolean preview) {
		if (activeFrame == Hud.frame() && activePreview == preview) return active;
		activeFrame = Hud.frame();
		activePreview = preview;
		List<StatusEffectInstance> out = active;
		out.clear();
		if (mc.player != null) {
			for (StatusEffectInstance e : mc.player.getStatusEffects()) {
				if (e.shouldShowIcon()) out.add(e);
			}
		}
		if (out.isEmpty() && preview) {
			out.add(new StatusEffectInstance(StatusEffects.SPEED, 20 * 94, 1));
			out.add(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 20 * 302, 0));
		}
		out.sort(Comparator.comparingInt(StatusEffectInstance::getDuration));
		return out;
	}

	@Override
	public boolean idle(MinecraftClient mc) {
		return active(mc, false).isEmpty();
	}

	private static String name(StatusEffectInstance e) {
		String n = e.getEffectType().value().getName().getString();
		int lvl = e.getAmplifier() + 1;
		return lvl > 1 ? n + " " + roman(lvl) : n;
	}

	private static String roman(int n) {
		String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		return n < r.length ? r[n] : Integer.toString(n);
	}

	private static String time(StatusEffectInstance e) {
		if (e.isInfinite()) return "∞";
		int secs = e.getDuration() / 20;
		return secs / 60 + ":" + String.format("%02d", secs % 60);
	}

	@Override
	public int width(MinecraftClient mc, boolean preview) {
		int w = 0;
		for (StatusEffectInstance e : active(mc, preview)) {
			w = Math.max(w, Math.max(Type.STRONG.width(name(e)), Type.SMALL.width(time(e))));
		}
		return (on("icons") ? 23 : 0) + w;
	}

	@Override
	public int height(MinecraftClient mc, boolean preview) {
		int row = on("time") || on("icons") ? ROW : 12;
		return Math.max(on("icons") ? 18 : 9, active(mc, preview).size() * row - 2);
	}

	@Override
	public void draw(Gfx ctx, MinecraftClient mc, boolean preview) {
		int y = 0;
		int x = on("icons") ? 23 : 0;
		boolean time = on("time");
		for (StatusEffectInstance e : active(mc, preview)) {
			if (x > 0) ctx.effect(e, 0, y, 18);
			text(ctx, Type.STRONG, name(e), x, time || x == 0 ? y : y + 5, 0.95f);
			if (time) {
				boolean ending = !e.isInfinite() && e.getDuration() < 200 && (e.getDuration() / 10) % 2 == 0;
				text(ctx, Type.SMALL, time(e), x, y + 10, ending ? 0.3f : 0.55f);
			}
			y += time ? ROW : (x > 0 ? ROW : 12);
		}
	}
}
