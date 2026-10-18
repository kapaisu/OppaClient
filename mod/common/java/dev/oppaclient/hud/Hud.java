package dev.oppaclient.hud;

import dev.oppaclient.Config;
import dev.oppaclient.Paint;
import dev.oppaclient.menu.LayoutScreen;
import dev.oppaclient.platform.BaseScreen;
import dev.oppaclient.platform.Gfx;
import dev.oppaclient.social.ToastView;
import dev.oppaclient.platform.Platform;
import net.minecraft.client.MinecraftClient;

import java.util.List;

public final class Hud {
	public static final List<Element> ELEMENTS = List.of(
			new Readouts.Fps(),
			new Readouts.Coords(),
			new Readouts.Cps(),
			new Keystrokes(),
			new Armor(),
			new Effects(),
			new Readouts.Ping(),
			new Readouts.Clock(),
			new Readouts.Speed(),
			new Readouts.Biome(),
			new Readouts.Day(),
			new Readouts.Memory(),
			new Readouts.Server(),
			new Readouts.Reach(),
			new Readouts.Combo(),
			new Readouts.Items());

	private static final Element EFFECTS = ELEMENTS.stream().filter(e -> e instanceof Effects).findFirst().orElseThrow();

	public static final float REFERENCE_SCALE = 2f;
	private static long frame;

	private Hud() {}

	public static long frame() {
		return frame;
	}

	public static void nextFrame() {
		frame++;
	}

	public static float fit() {
		return REFERENCE_SCALE / Platform.guiScale();
	}

	public static boolean drawsEffects() {
		return EFFECTS.placement().enabled && !Config.get().hudHidden;
	}

	public static boolean drawsCrosshair(MinecraftClient mc) {
		return Config.get().crosshair && mc.options.getPerspective().isFirstPerson()
				&& !mc.getDebugHud().shouldShowDebugHud() && mc.interactionManager != null
				&& mc.interactionManager.getCurrentGameMode() != net.minecraft.world.GameMode.SPECTATOR;
	}

	public record Rect(int x, int y, int w, int h) {
		public boolean contains(double px, double py) {
			return px >= x && py >= y && px < x + w && py < y + h;
		}
	}

	public static Rect rect(Element e, MinecraftClient mc, boolean preview) {
		Config.Placement p = e.placement();
		float fit = fit();
		float scale = Config.get().scale * p.scale * fit;
		int w = Math.round(e.width(mc, preview) * scale);
		int h = Math.round(e.height(mc, preview) * scale);
		int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
		int x = Math.round(p.ax * (sw - w)) + Math.round(p.ox * fit);
		int y = Math.round(p.ay * (sh - h)) + Math.round(p.oy * fit);
		return new Rect(Math.max(0, Math.min(sw - w, x)), Math.max(0, Math.min(sh - h, y)), w, h);
	}

	public static void draw(Gfx ctx, MinecraftClient mc, Element e, Rect r, boolean preview) {
		float fit = fit();
		float scale = Config.get().scale * e.placement().scale * fit;
		if (Config.get().backdrop) {
			Paint.round(ctx, r.x() - 4 * fit, r.y() - 3 * fit, r.w() + 8 * fit, r.h() + 6 * fit, 5 * fit, Paint.shade(0.35f));
		}
		ctx.push();
		ctx.translate(r.x(), r.y());
		ctx.scale(scale, scale);
		Paint.density(Platform.guiScale() * scale);
		e.draw(ctx, mc, preview);
		Paint.density(0);
		ctx.pop();
	}

	public static void render(Gfx ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		frame++;
		Clicks.poll(mc);
		if (mc.options.hudHidden) return;
		if (!Config.get().hudHidden && !(mc.currentScreen instanceof LayoutScreen) && !mc.getDebugHud().shouldShowDebugHud()) {
			for (Element e : ELEMENTS) {
				if (!e.placement().enabled || e.idle(mc)) continue;
				draw(ctx, mc, e, rect(e, mc, false), false);
			}
		}
		if (!(mc.currentScreen instanceof BaseScreen)) ToastView.draw(ctx, mc.getWindow().getScaledWidth(), 8);
	}
}
