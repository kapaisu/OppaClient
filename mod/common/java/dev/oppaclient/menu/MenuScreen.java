package dev.oppaclient.menu;

import dev.oppaclient.Config;
import dev.oppaclient.Keys;
import dev.oppaclient.Logo;
import dev.oppaclient.Paint;
import dev.oppaclient.Type;
import dev.oppaclient.hud.Element;
import dev.oppaclient.hud.Hud;
import dev.oppaclient.overlay.Canvas;
import dev.oppaclient.overlay.Colors;
import dev.oppaclient.overlay.Labeled;
import dev.oppaclient.overlay.OverlayConfig;
import dev.oppaclient.overlay.OverlaySettings;
import dev.oppaclient.overlay.Preview;
import dev.oppaclient.platform.BaseScreen;
import dev.oppaclient.platform.Gfx;
import dev.oppaclient.platform.Platform;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

public final class MenuScreen extends BaseScreen {
	private static final int ROW = 20, TOP = 34, COLUMN = 300;
	private static final String[] TABS = {"HUD", "Visuals", "Settings"};
	private static final String OVERLAY = "overlay", ZOOM = "zoom", FREELOOK = "freelook", CROSSHAIR = "crosshair";
	private static int lastTab;

	private int tab = lastTab;
	private Object detail;
	private int slideFrom = 1;

	private final Paint.Smooth open = new Paint.Smooth(0, 14).to(1);
	private final Paint.Smooth contentIn = new Paint.Smooth(1, 16);
	private final Paint.Smooth pillX = new Paint.Smooth(-1, 20);
	private final Paint.Smooth pillW = new Paint.Smooth(0, 20);
	private final Paint.Smooth scroll = new Paint.Smooth(0, 18);
	private int contentHeight;

	private final Map<String, Switch> switches = new HashMap<>();
	private KeyBinding rebinding;
	private long resetArmedUntil;
	private Drag dragging;
	private final Map<String, float[]> hsv = new HashMap<>();
	private String editingHex;
	private String hexBuffer = "";
	private Consumer<Integer> hexTarget;

	private static final int[] PALETTE = {
			0xFF2F6E, 0xFF3A1A, 0xFFA51F,
			0x7CFF4F, 0x35E0D0, 0x2FD8FF,
			0x3B7CFF, 0xB65BFF, 0xF2F4F8
	};

	private interface Drag {
		void to(double x, double y);
	}
	private String expandedColor;
	private String hint;

	private final Preview preview = new Preview();
	private double now, lastFrame;
	private float dt;
	private boolean turning;
	private double turnDown, turnLast;

	private record Hit(int x, int y, int w, int h, boolean scrolls, boolean secondary, boolean hexField, Consumer<Press> action) {
		Hit(int x, int y, int w, int h, boolean scrolls, Consumer<Press> action) {
			this(x, y, w, h, scrolls, false, false, action);
		}
	}

	private final List<Hit> hits = new ArrayList<>();

	public MenuScreen() {
		super(Text.literal("Oppa"));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	protected void drawBackground(Gfx ctx) {
		if (client.world == null) Backdrop.draw(ctx);
		ctx.fill(0, 0, width, height, Paint.shade(0.55f * open.get()));
	}

	private int column() {
		return Math.min(COLUMN, width - 40);
	}

	@Override
	protected void draw(Gfx ctx, int mouseX, int mouseY) {
		hits.clear();
		hint = null;
		now = System.nanoTime() / 1e9;
		dt = lastFrame == 0 ? 0 : (float) Math.min(0.1, now - lastFrame);
		lastFrame = now;
		float a = open.get();
		float c = contentIn.get();
		int lift = Math.round((1 - a) * 6);

		Logo.draw(ctx, 14, 9 + lift, 15, 0.92f * a);
		drawTabs(ctx, mouseX, mouseY, a, lift);
		pill(ctx, "Edit Layout", width - 14, 8 + lift, mouseX, mouseY, a, click -> client.setScreen(new LayoutScreen(this)));

		int maxScroll = Math.max(0, contentHeight - (height - TOP - 24));
		if (scroll.target() > maxScroll) scroll.to(maxScroll);
		int x = (width - column()) / 2 + Math.round((1 - c) * 14 * slideFrom);
		int y = TOP + 8 - Math.round(scroll.get());
		float alpha = a * c;
		int my = mouseY < TOP ? Integer.MIN_VALUE : mouseY;

		ctx.scissor(0, TOP, width, height - 16);
		int start = y;
		if (detail instanceof Element e) {
			y = elementPage(ctx, e, x, y, mouseX, my, alpha);
		} else if (OVERLAY.equals(detail)) {
			y = overlayPage(ctx, x, y, mouseX, my, alpha);
		} else if (ZOOM.equals(detail)) {
			y = zoomPage(ctx, x, y, mouseX, my, alpha);
		} else if (FREELOOK.equals(detail)) {
			y = freelookPage(ctx, x, y, mouseX, my, alpha);
		} else if (CROSSHAIR.equals(detail)) {
			y = crosshairPage(ctx, x, y, mouseX, my, alpha);
		} else if (tab == 0) {
			y = hudList(ctx, x, y, mouseX, my, alpha);
		} else if (tab == 1) {
			y = visuals(ctx, x, y, mouseX, my, alpha);
		} else {
			y = settings(ctx, x, y, mouseX, my, alpha);
		}
		ctx.endScissor();
		contentHeight = y - start;

		if (hint != null) {
			Type.SMALL.drawCentered(ctx, hint, width / 2, height - 12, Paint.ink(0.5f * a));
		}
	}

	private void drawTabs(Gfx ctx, int mouseX, int mouseY, float a, int lift) {
		int gap = 4, padX = 9;
		int total = -gap;
		for (String t : TABS) total += Type.STRONG.width(t) + padX * 2 + gap;
		int x = (width - total) / 2;
		int y = 8 + lift;
		int[] xs = new int[TABS.length];
		for (int i = 0; i < TABS.length; i++) {
			int w = Type.STRONG.width(TABS[i]) + padX * 2;
			xs[i] = x;
			if (i == tab) {
				if (pillX.get() < 0) {
					pillX.snap(x);
					pillW.snap(w);
				}
				pillX.to(x);
				pillW.to(w);
			}
			if (i != tab && inside(mouseX, mouseY, x, y, w, 17)) Paint.round(ctx, x, y, w, 17, 8.5f, Paint.ink(0.05f * a));
			final int index = i;
			hits.add(new Hit(x, y, w, 17, false, click -> switchTab(index)));
			x += w + gap;
		}
		Paint.round(ctx, pillX.get(), y, pillW.get(), 17, 8.5f, Paint.ink(0.12f * a));
		for (int i = 0; i < TABS.length; i++) {
			Type.STRONG.draw(ctx, TABS[i], xs[i] + padX, y + 4, Paint.ink((i == tab ? 0.95f : 0.6f) * a));
		}
	}

	private void pill(Gfx ctx, String label, int right, int y, int mouseX, int mouseY, float a, Consumer<Press> action) {
		int w = Type.STRONG.width(label) + 24, h = 17;
		int x = right - w;
		boolean hover = inside(mouseX, mouseY, x, y, w, h);
		Paint.round(ctx, x, y, w, h, h / 2f, Paint.ink((hover ? 1f : 0.88f) * a));
		Type.STRONG.draw(ctx, label, x + 12, y + 4, Paint.shade(0.9f * a));
		hits.add(new Hit(x, y, w, h, false, action));
	}

	private void switchTab(int index) {
		if (index == tab && detail == null) return;
		slideFrom = index > tab ? 1 : -1;
		tab = lastTab = index;
		detail = null;
		rebinding = null;
		restartContent();
	}

	private void restartContent() {
		contentIn.snap(0);
		contentIn.to(1);
		scroll.snap(0);
	}

	private void openDetail(Object page) {
		detail = page;
		slideFrom = 1;
		expandedColor = null;
		restartContent();
	}

	private void closeDetail() {
		detail = null;
		slideFrom = -1;
		restartContent();
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private void row(Gfx ctx, int x, int y, int mouseX, int mouseY, float alpha, String rowHint, Consumer<Press> action) {
		int w = column();
		if (inside(mouseX, mouseY, x - 8, y, w + 16, ROW)) {
			Paint.round(ctx, x - 8, y, w + 16, ROW, 6, Paint.ink(0.05f * alpha));
			if (rowHint != null) hint = rowHint;
		}
		if (action != null) hits.add(new Hit(x - 8, y, w + 16, ROW, true, action));
	}

	private void label(Gfx ctx, String s, int x, int y, float alpha) {
		Type.STRONG.draw(ctx, s, x, y + 6, Paint.ink(0.92f * alpha));
	}

	private int section(Gfx ctx, String title, int x, int y, float alpha) {
		Type.SMALL.draw(ctx, title, x, y + 10, Paint.ink(0.45f * alpha));
		return y + 22;
	}

	private int back(Gfx ctx, String title, int x, int y, int mouseX, int mouseY, float alpha) {
		String back = "‹  " + TABS[tab];
		int bw = Type.UI.width(back);
		boolean hover = inside(mouseX, mouseY, x - 4, y - 2, bw + 8, 13);
		Type.UI.draw(ctx, back, x, y, Paint.ink((hover ? 0.9f : 0.5f) * alpha));
		hits.add(new Hit(x - 4, y - 2, bw + 8, 13, true, click -> closeDetail()));
		y += 18;
		Type.TITLE.draw(ctx, title, x, y, Paint.ink(0.95f * alpha));
		return y + Type.TITLE.cap + 14;
	}

	private void toggle(Gfx ctx, String key, String label, String rowHint, int x, int y, int mouseX, int mouseY, float alpha,
			BooleanSupplier get, Consumer<Boolean> set) {
		row(ctx, x, y, mouseX, mouseY, alpha, rowHint, click -> set.accept(!get.getAsBoolean()));
		label(ctx, label, x, y, alpha);
		switches.computeIfAbsent(key, k -> new Switch(get.getAsBoolean()))
				.draw(ctx, x + column() - Switch.W, y + 5, get.getAsBoolean(), alpha);
	}

	private void link(Gfx ctx, String key, String label, int x, int y, int mouseX, int mouseY, float alpha,
			BooleanSupplier get, Consumer<Boolean> set, Runnable open) {
		int switchX = x + column() - Switch.W;
		row(ctx, x, y, mouseX, mouseY, alpha, null, click -> {
			if (click.x() >= switchX - 6) set.accept(!get.getAsBoolean());
			else open.run();
		});
		label(ctx, label, x, y, alpha);
		Type.UI.draw(ctx, "›", switchX - 12, y + 6, Paint.ink(0.3f * alpha));
		switches.computeIfAbsent(key, k -> new Switch(get.getAsBoolean())).draw(ctx, switchX, y + 5, get.getAsBoolean(), alpha);
	}

	private void slider(Gfx ctx, String label, String rowHint, int x, int y, int mouseX, int mouseY, float alpha,
			float value, float min, float max, String shown, Consumer<Float> set) {
		int track = 110;
		int tx = x + column() - track;
		DoubleConsumer drag = mx -> set.accept(min + (float) Math.max(0, Math.min(1, (mx - tx) / track)) * (max - min));
		row(ctx, x, y, mouseX, mouseY, alpha, rowHint, click -> {
			if (click.x() >= tx - 6) {
				dragging = (px, py) -> drag.accept(px);
				drag.accept(click.x());
			}
		});
		label(ctx, label, x, y, alpha);
		Type.UI.drawRight(ctx, shown, tx - 10, y + 6, Paint.ink(0.55f * alpha));
		float t = Math.max(0, Math.min(1, (value - min) / (max - min)));
		int ty = y + ROW / 2;
		Paint.round(ctx, tx, ty - 1, track, 2, 1, Paint.ink(0.18f * alpha));
		Paint.round(ctx, tx, ty - 1, track * t, 2, 1, Paint.ink(0.9f * alpha));
		Paint.round(ctx, tx + track * t - 4, ty - 4, 8, 8, 4, Paint.ink(alpha));
	}

	private static float stepped(float v, float step) {
		return Math.round(v / step) * step;
	}

	private void choice(Gfx ctx, String label, String rowHint, int x, int y, int mouseX, int mouseY, float alpha,
			List<String> values, int picked, Consumer<Integer> set) {
		row(ctx, x, y, mouseX, mouseY, alpha, rowHint, null);
		label(ctx, label, x, y, alpha);
		int right = x + column();
		int total = 0;
		for (String v : values) total += Type.UI.width(v) + 14;
		if (values.size() <= 4 && total <= column() - Type.STRONG.width(label) - 16) {
			for (int i = values.size() - 1; i >= 0; i--) {
				String v = values.get(i);
				int w = Type.UI.width(v) + 12;
				int px = right - w;
				boolean on = i == picked;
				if (on) Paint.round(ctx, px, y + 3, w, ROW - 6, (ROW - 6) / 2f, Paint.ink(0.12f * alpha));
				else if (inside(mouseX, mouseY, px, y + 3, w, ROW - 6)) Paint.round(ctx, px, y + 3, w, ROW - 6, (ROW - 6) / 2f, Paint.ink(0.05f * alpha));
				Type.UI.draw(ctx, v, px + 6, y + 6, Paint.ink((on ? 0.95f : 0.5f) * alpha));
				final int index = i;
				hits.add(new Hit(px, y + 3, w, ROW - 6, true, click -> set.accept(index)));
				right = px - 2;
			}
			return;
		}
		int box = 0;
		for (String v : values) box = Math.max(box, Type.UI.width(v));
		box += 36;
		int bx = right - box;
		String current = values.get(picked);
		Type.UI.draw(ctx, "‹", bx + 4, y + 6, Paint.ink((inside(mouseX, mouseY, bx, y, box / 2, ROW) ? 0.9f : 0.4f) * alpha));
		Type.UI.drawRight(ctx, "›", right - 4, y + 6, Paint.ink((inside(mouseX, mouseY, bx + box / 2, y, box / 2, ROW) ? 0.9f : 0.4f) * alpha));
		Type.UI.drawCentered(ctx, current, bx + box / 2, y + 6, Paint.ink(0.92f * alpha));
		int n = values.size();
		hits.add(new Hit(bx, y, box / 2, ROW, true, click -> set.accept(Math.floorMod(picked - 1, n))));
		hits.add(new Hit(bx + box / 2, y, box - box / 2, ROW, true, click -> set.accept(Math.floorMod(picked + 1, n))));
	}

	private int color(Gfx ctx, String key, String label, String rowHint, int x, int y, int mouseX, int mouseY, float alpha,
			int rgb, int fallback, Consumer<Integer> set) {
		int right = x + column();
		boolean editing = key.equals(editingHex);
		String shown = String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
		int hexW = Type.UI.width(shown);
		int hexX = right - 18 - hexW;
		Consumer<Press> reset = click -> {
			set.accept(fallback);
			hsv.remove(key);
		};
		hits.add(new Hit(hexX - 4, y, hexW + 8, ROW, true, true, true, click -> {
			if (click.secondary()) {
				reset.accept(click);
				return;
			}
			commitHex();
			editingHex = key;
			hexTarget = set;
			hexBuffer = String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF);
		}));
		row(ctx, x, y, mouseX, mouseY, alpha, rowHint == null ? "Right-click resets the color." : rowHint, null);
		hits.add(new Hit(x - 8, y, column() + 16, ROW, true, true, false, click -> {
			if (click.secondary()) reset.accept(click);
			else expandedColor = key.equals(expandedColor) ? null : key;
		}));
		label(ctx, label, x, y, alpha);
		Paint.round(ctx, right - 12, y + 4, 12, 12, 4, Paint.argb(alpha, rgb));
		String hex = editing ? "#" + hexBuffer + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : " ") : shown;
		boolean hoverHex = inside(mouseX, mouseY, hexX - 4, y, hexW + 8, ROW);
		Type.UI.draw(ctx, hex, hexX, y + 6, Paint.ink((editing || hoverHex ? 0.95f : 0.5f) * alpha));
		y += ROW;
		if (!key.equals(expandedColor)) return y;
		return picker(ctx, key, x, y, mouseX, mouseY, alpha, rgb, set);
	}

	private int picker(Gfx ctx, String key, int left, int top, int mouseX, int mouseY, float alpha, int rgb, Consumer<Integer> set) {
		float[] state = hsv.computeIfAbsent(key, k -> Colors.toHsv(rgb));
		if (Colors.hsv(state[0], state[1], state[2]) != (rgb & 0xFFFFFF)) {
			float[] fresh = Colors.toHsv(rgb);
			if (fresh[1] < 0.01f || fresh[2] < 0.01f) fresh[0] = state[0];
			System.arraycopy(fresh, 0, state, 0, 3);
		}
		int right = left + column();
		int chip = 14, chipGap = 6, gridW = chip * 3 + chipGap * 2;
		int fy0 = top + 6, fieldH = 64;
		int fx0 = left, fieldW = Math.max(60, right - left - gridW - 40);
		int fx1 = fx0 + fieldW, fy1 = fy0 + fieldH;

		Canvas flat = Canvas.flat().alpha(alpha);
		int hue = Colors.hsv(state[0], 1f, 1f);
		flat.gradient(fx0, fy0, fx1, fy1, 0xFFFFFFFF, 0xFF000000 | hue, 0xFFFFFFFF, 0xFF000000 | hue);
		flat.gradient(fx0, fy0, fx1, fy1, 0x00000000, 0x00000000, 0xFF000000, 0xFF000000);
		float px = fx0 + state[1] * fieldW, py = fy0 + (1f - state[2]) * fieldH;
		flat.roundOutline(px - 4.5f, py - 4.5f, px + 4.5f, py + 4.5f, 4.5f, 1f, 0x80000000);
		flat.roundOutline(px - 3.5f, py - 3.5f, px + 3.5f, py + 3.5f, 3.5f, 1.2f, 0xFFFFFFFF);

		int hx0 = fx1 + 10, hx1 = hx0 + 8;
		for (int i = 0; i < 6; i++) {
			float a = fy0 + fieldH * i / 6f, b = fy0 + fieldH * (i + 1) / 6f;
			int ca = 0xFF000000 | Colors.hsv(i / 6f, 1f, 1f), cb = 0xFF000000 | Colors.hsv((i + 1) / 6f, 1f, 1f);
			flat.gradient(hx0, a, hx1, b, ca, ca, cb, cb);
		}
		float hy = fy0 + state[0] * fieldH;
		flat.roundRect(hx0 - 2, hy - 1.5f, hx1 + 2, hy + 1.5f, 1f, 0xFFFFFFFF);

		int gx0 = right - gridW, gy0 = fy0 + (fieldH - gridW) / 2;
		for (int i = 0; i < PALETTE.length; i++) {
			int cx = gx0 + (i % 3) * (chip + chipGap), cy = gy0 + (i / 3) * (chip + chipGap);
			int color = PALETTE[i];
			float grow = inside(mouseX, mouseY, cx - 2, cy - 2, chip + 4, chip + 4) ? 1f : 0f;
			flat.roundRect(cx - grow, cy - grow, cx + chip + grow, cy + chip + grow, 4 + grow, 0xFF000000 | color);
			if (color == (rgb & 0xFFFFFF)) {
				float c = chip / 2f;
				float[] chipHsv = Colors.toHsv(color);
				int dot = chipHsv[2] > 0.85f && chipHsv[1] < 0.2f ? 0xFF15161A : 0xFFFFFFFF;
				flat.roundRect(cx + c - 2, cy + c - 2, cx + c + 2, cy + c + 2, 2, dot);
			}
			hits.add(new Hit(cx - 2, cy - 2, chip + 4, chip + 4, true, click -> {
				set.accept(color);
				hsv.remove(key);
			}));
		}
		ctx.canvas(flat, left, top, column(), fieldH + 12);

		Drag field = (mx, my) -> {
			state[1] = Math.max(0f, Math.min(1f, (float) ((mx - fx0) / fieldW)));
			state[2] = Math.max(0f, Math.min(1f, 1f - (float) ((my - fy0) / fieldH)));
			set.accept(Colors.hsv(state[0], state[1], state[2]));
		};
		Drag strip = (mx, my) -> {
			state[0] = Math.max(0f, Math.min(0.9999f, (float) ((my - fy0) / fieldH)));
			set.accept(Colors.hsv(state[0], state[1], state[2]));
		};
		hits.add(new Hit(fx0 - 4, fy0 - 4, fieldW + 8, fieldH + 8, true, click -> {
			dragging = field;
			field.to(click.x(), click.y());
		}));
		hits.add(new Hit(hx0 - 4, fy0 - 4, hx1 - hx0 + 8, fieldH + 8, true, click -> {
			dragging = strip;
			strip.to(click.x(), click.y());
		}));
		return fy1 + 12;
	}

	private void commitHex() {
		if (editingHex == null) return;
		if (hexBuffer.length() == 6 || hexBuffer.length() == 3) {
			String full = hexBuffer.length() == 3
					? "" + hexBuffer.charAt(0) + hexBuffer.charAt(0) + hexBuffer.charAt(1) + hexBuffer.charAt(1) + hexBuffer.charAt(2) + hexBuffer.charAt(2)
					: hexBuffer;
			hexTarget.accept(Integer.parseInt(full, 16));
			hsv.remove(editingHex);
		}
		editingHex = null;
		hexTarget = null;
		hexBuffer = "";
	}

	private void keyRow(Gfx ctx, String label, KeyBinding key, int x, int y, int mouseX, int mouseY, float alpha) {
		row(ctx, x, y, mouseX, mouseY, alpha, null, click -> rebinding = rebinding == key ? null : key);
		label(ctx, label, x, y, alpha);
		boolean waiting = rebinding == key;
		if (waiting) hint = "Backspace clears the key. Esc cancels.";
		String name = waiting ? "Press a key" : key.isUnbound() ? "None" : key.getBoundKeyLocalizedText().getString();
		Type.UI.drawRight(ctx, name, x + column(), y + 6, Paint.ink((waiting ? 0.95f : 0.55f) * alpha));
	}

	private int hudList(Gfx ctx, int x, int y, int mouseX, int mouseY, float alpha) {
		for (Element e : Hud.ELEMENTS) {
			Config.Placement p = e.placement();
			link(ctx, "hud." + e.id, e.label, x, y, mouseX, mouseY, alpha, () -> p.enabled, v -> p.enabled = v, () -> openDetail(e));
			y += ROW;
		}
		return y;
	}

	private int elementPage(Gfx ctx, Element e, int x, int y, int mouseX, int mouseY, float alpha) {
		y = back(ctx, e.label, x, y, mouseX, mouseY, alpha);
		Config.Placement p = e.placement();
		toggle(ctx, "hud." + e.id, "Visible", null, x, y, mouseX, mouseY, alpha, () -> p.enabled, v -> p.enabled = v);
		y += ROW;
		slider(ctx, "Size", null, x, y, mouseX, mouseY, alpha, p.scale, 0.5f, 3f,
				Math.round(p.scale * 100) + "%", v -> p.scale = stepped(v, 0.05f));
		y += ROW;
		for (Element.Option o : e.options()) {
			switch (o) {
				case Element.Toggle t -> toggle(ctx, e.id + "." + t.key(), t.label(), null, x, y, mouseX, mouseY, alpha,
						() -> e.on(t.key()), v -> e.set(t.key(), v));
				case Element.Choice c -> choice(ctx, c.label(), null, x, y, mouseX, mouseY, alpha,
						c.values(), Math.max(0, c.values().indexOf(e.pick(c.key()))), i -> e.set(c.key(), c.values().get(i)));
			}
			y += ROW;
		}
		row(ctx, x, y, mouseX, mouseY, alpha, null, click -> e.resetPosition());
		label(ctx, "Reset position and size", x, y, alpha);
		return y + ROW;
	}

	private int visuals(Gfx ctx, int x, int y, int mouseX, int mouseY, float alpha) {
		Config cfg = Config.get();
		OverlayConfig ov = OverlayConfig.get();
		link(ctx, "overlay", "Block overlay", x, y, mouseX, mouseY, alpha, () -> ov.enabled, v -> ov.enabled = v, () -> openDetail(OVERLAY));
		y += ROW;
		link(ctx, "zoom", "Zoom", x, y, mouseX, mouseY, alpha, () -> cfg.zoom, v -> cfg.zoom = v, () -> openDetail(ZOOM));
		y += ROW;
		link(ctx, "freelook", "Free look", x, y, mouseX, mouseY, alpha, () -> cfg.freelook, v -> cfg.freelook = v, () -> openDetail(FREELOOK));
		y += ROW;
		link(ctx, "crosshair", "Crosshair", x, y, mouseX, mouseY, alpha, () -> cfg.crosshair, v -> cfg.crosshair = v, () -> openDetail(CROSSHAIR));
		y += ROW;
		toggle(ctx, "fullbright", "Fullbright", null, x, y, mouseX, mouseY, alpha, () -> cfg.fullbright, v -> cfg.fullbright = v);
		y += ROW;
		toggle(ctx, "nametag", "Own nametag", "Shows your name above you in third person.", x, y, mouseX, mouseY, alpha,
				() -> cfg.ownNametag, v -> cfg.ownNametag = v);
		y += ROW;
		toggle(ctx, "badges", "Oppa badges", "Shows the Oppa logo next to other players using Oppa.", x, y, mouseX, mouseY, alpha,
				() -> cfg.badges, v -> cfg.badges = v);
		y += ROW;
		slider(ctx, "Low fire", "Lowers the fire overlay so you can see while burning.", x, y, mouseX, mouseY, alpha,
				cfg.lowFire, 0, 1, cfg.lowFire == 0 ? "Off" : Math.round(cfg.lowFire * 100) + "%", v -> cfg.lowFire = stepped(v, 0.05f));
		y += ROW;
		List<String> times = List.of("Off", "Day", "Sunset", "Night");
		choice(ctx, "Time", "Only changes what you see. Everyone else keeps the real time.", x, y, mouseX, mouseY, alpha,
				times, Math.max(0, times.indexOf(cfg.time)), i -> cfg.time = times.get(i));
		y += ROW;
		toggle(ctx, "weather", "Clear weather", "Hides rain and storms on your screen only.", x, y, mouseX, mouseY, alpha,
				() -> cfg.clearWeather, v -> cfg.clearWeather = v);
		return y + ROW;
	}

	private int overlayPage(Gfx ctx, int x, int y, int mouseX, int mouseY, float alpha) {
		OverlayConfig cfg = OverlayConfig.get();
		y = back(ctx, "Block overlay", x, y, mouseX, mouseY, alpha);
		int side = (width - column()) / 2 - 36;
		if (side >= 150) {
			int sw = Math.min(220, side), sh = Math.round(sw * 0.9f);
			stage(ctx, (width + column()) / 2 + 24, TOP + 12, sw, sh, mouseX, mouseY, alpha, false);
		} else {
			stage(ctx, x, y, column(), 140, mouseX, mouseY, alpha, true);
			y += 150;
		}
		toggle(ctx, "overlay", "Enabled", null, x, y, mouseX, mouseY, alpha, () -> cfg.enabled, v -> cfg.enabled = v);
		y += ROW;
		for (OverlaySettings.Category category : OverlaySettings.CATEGORIES) {
			List<OverlaySettings.Setting> shown = category.settings().stream().filter(s -> s.visible().test(cfg)).toList();
			if (shown.isEmpty()) continue;
			y = section(ctx, category.name(), x, y, alpha);
			for (OverlaySettings.Setting s : shown) {
				y = setting(ctx, s, cfg, x, y, mouseX, mouseY, alpha);
			}
		}
		return y;
	}

	private int setting(Gfx ctx, OverlaySettings.Setting s, OverlayConfig cfg, int x, int y, int mouseX, int mouseY, float alpha) {
		String key = "ov." + s.label();
		switch (s) {
			case OverlaySettings.Toggle t -> toggle(ctx, key, t.label(), t.hint(), x, y, mouseX, mouseY, alpha,
					() -> t.get().test(cfg), v -> t.set().accept(cfg, v));
			case OverlaySettings.Range r -> slider(ctx, r.label(), r.hint(), x, y, mouseX, mouseY, alpha,
					r.value(cfg), r.min(), r.max(), r.format(r.value(cfg)), v -> r.apply(cfg, v));
			case OverlaySettings.Choice<?> c -> {
				Labeled[] values = c.values();
				choice(ctx, c.label(), c.hint(), x, y, mouseX, mouseY, alpha,
						Arrays.stream(values).map(Labeled::label).toList(), c.index(cfg), i -> c.select(cfg, i));
			}
			case OverlaySettings.Color col -> {
				return color(ctx, key, col.label(), col.hint(), x, y, mouseX, mouseY, alpha,
						col.get().applyAsInt(cfg), col.get().applyAsInt(OverlayConfig.DEFAULTS), v -> col.set().accept(cfg, v));
			}
		}
		return y + ROW;
	}

	private int zoomPage(Gfx ctx, int x, int y, int mouseX, int mouseY, float alpha) {
		Config cfg = Config.get();
		y = back(ctx, "Zoom", x, y, mouseX, mouseY, alpha);
		toggle(ctx, "zoom", "Enabled", null, x, y, mouseX, mouseY, alpha, () -> cfg.zoom, v -> cfg.zoom = v);
		y += ROW;
		keyRow(ctx, "Key", Keys.ZOOM, x, y, mouseX, mouseY, alpha);
		y += ROW;
		slider(ctx, "Amount", null, x, y, mouseX, mouseY, alpha, cfg.zoomFactor, 1.5f, 10f,
				String.format("%.1fx", cfg.zoomFactor), v -> cfg.zoomFactor = stepped(v, 0.5f));
		y += ROW;
		toggle(ctx, "zoom.smooth", "Smooth", null, x, y, mouseX, mouseY, alpha, () -> cfg.zoomSmooth, v -> cfg.zoomSmooth = v);
		y += ROW;
		toggle(ctx, "zoom.scroll", "Scroll to adjust", null, x, y, mouseX, mouseY, alpha, () -> cfg.zoomScroll, v -> cfg.zoomScroll = v);
		y += ROW;
		toggle(ctx, "zoom.cinematic", "Cinematic camera", "Smooths mouse movement while zoomed in.", x, y, mouseX, mouseY, alpha,
				() -> cfg.zoomCinematic, v -> cfg.zoomCinematic = v);
		return y + ROW;
	}

	private int freelookPage(Gfx ctx, int x, int y, int mouseX, int mouseY, float alpha) {
		Config cfg = Config.get();
		y = back(ctx, "Free look", x, y, mouseX, mouseY, alpha);
		toggle(ctx, "freelook", "Enabled", "Turn the camera around you while you keep moving the way you face.", x, y, mouseX, mouseY, alpha,
				() -> cfg.freelook, v -> cfg.freelook = v);
		y += ROW;
		keyRow(ctx, "Key", Keys.FREELOOK, x, y, mouseX, mouseY, alpha);
		y += ROW;
		List<String> modes = List.of("Hold", "Toggle");
		choice(ctx, "Mode", null, x, y, mouseX, mouseY, alpha, modes, cfg.freelookToggle ? 1 : 0, i -> cfg.freelookToggle = i == 1);
		return y + ROW;
	}

	private int crosshairPage(Gfx ctx, int x, int y, int mouseX, int mouseY, float alpha) {
		Config cfg = Config.get();
		y = back(ctx, "Crosshair", x, y, mouseX, mouseY, alpha);
		toggle(ctx, "crosshair", "Enabled", "Replaces the vanilla crosshair in first person.", x, y, mouseX, mouseY, alpha,
				() -> cfg.crosshair, v -> cfg.crosshair = v);
		y += ROW;
		List<String> styles = List.of("Plus", "Plus + dot", "Dot", "Square");
		choice(ctx, "Style", null, x, y, mouseX, mouseY, alpha, styles, Math.max(0, styles.indexOf(cfg.crosshairStyle)),
				i -> cfg.crosshairStyle = styles.get(i));
		y += ROW;
		slider(ctx, "Size", null, x, y, mouseX, mouseY, alpha, cfg.crosshairSize, 1, 12,
				String.format("%.1f", cfg.crosshairSize), v -> cfg.crosshairSize = stepped(v, 0.5f));
		y += ROW;
		slider(ctx, "Gap", null, x, y, mouseX, mouseY, alpha, cfg.crosshairGap, 0, 8,
				String.format("%.1f", cfg.crosshairGap), v -> cfg.crosshairGap = stepped(v, 0.5f));
		y += ROW;
		slider(ctx, "Thickness", null, x, y, mouseX, mouseY, alpha, cfg.crosshairThickness, 0.5f, 3,
				String.format("%.1f", cfg.crosshairThickness), v -> cfg.crosshairThickness = stepped(v, 0.25f));
		y += ROW;
		toggle(ctx, "crosshair.outline", "Outline", null, x, y, mouseX, mouseY, alpha, () -> cfg.crosshairOutline, v -> cfg.crosshairOutline = v);
		y += ROW;
		y = color(ctx, "crosshair.color", "Color", null, x, y, mouseX, mouseY, alpha, cfg.crosshairColor, 0xFFFFFF, v -> cfg.crosshairColor = v);

		int sx = x + column() / 2, sy = y + 34;
		Paint.round(ctx, x, y + 8, column(), 52, 10, Paint.ink(0.04f * alpha));
		ctx.push();
		ctx.translate(sx - client.getWindow().getScaledWidth() / 2f, sy - client.getWindow().getScaledHeight() / 2f);
		dev.oppaclient.hud.Crosshair.draw(ctx, client);
		ctx.pop();
		return y + 70;
	}

	private void stage(Gfx ctx, int sx, int sy, int sw, int sh, int mouseX, int mouseY, float alpha, boolean scrolls) {
		OverlayConfig cfg = OverlayConfig.get();
		int names = 18;
		Paint.round(ctx, sx, sy, sw, sh, 12, Paint.ink(0.04f * alpha));
		preview.draw(ctx, cfg, sx, sy, sw, sh - names, now, dt, alpha);
		if (inside(mouseX, mouseY, sx, sy, sw, sh - names)) hint = "Drag to turn the block. Click to strike the tube again.";
		hits.add(new Hit(sx, sy, sw, sh - names, scrolls, click -> {
			turning = true;
			turnDown = turnLast = click.x();
			dragging = (mx, my) -> {
				preview.drag(mx - turnLast);
				turnLast = mx;
			};
		}));

		int gap = 14, total = -gap;
		for (Preview.Shape shape : Preview.SHAPES) total += Type.SMALL.width(shape.name()) + gap;
		int nx = sx + (sw - total) / 2, ny = sy + sh - names + 3;
		for (int i = 0; i < Preview.SHAPES.length; i++) {
			String name = Preview.SHAPES[i].name();
			int w = Type.SMALL.width(name);
			boolean on = preview.shape() == i;
			boolean hover = inside(mouseX, mouseY, nx - 4, ny - 3, w + 8, 13);
			Type.SMALL.draw(ctx, name, nx, ny, Paint.ink((on ? 0.95f : hover ? 0.7f : 0.4f) * alpha));
			final int index = i;
			hits.add(new Hit(nx - 4, ny - 3, w + 8, 13, scrolls, click -> preview.select(index, now, cfg)));
			nx += w + gap;
		}
	}

	private int settings(Gfx ctx, int x, int y, int mouseX, int mouseY, float alpha) {
		Config cfg = Config.get();
		slider(ctx, "HUD scale", null, x, y, mouseX, mouseY, alpha, cfg.scale, 0.5f, 2f,
				Math.round(cfg.scale * 100) + "%", v -> cfg.scale = stepped(v, 0.05f));
		y += ROW;
		toggle(ctx, "shadow", "Text shadow", null, x, y, mouseX, mouseY, alpha, () -> cfg.shadow, v -> cfg.shadow = v);
		y += ROW;
		toggle(ctx, "backdrop", "Backdrop", null, x, y, mouseX, mouseY, alpha, () -> cfg.backdrop, v -> cfg.backdrop = v);
		y += ROW;
		toggle(ctx, "font", "Oppa font", null, x, y, mouseX, mouseY, alpha, () -> cfg.oppaFont, v -> cfg.oppaFont = v);
		y += ROW;
		toggle(ctx, "hudhidden", "Hide HUD", null, x, y, mouseX, mouseY, alpha, () -> cfg.hudHidden, v -> cfg.hudHidden = v);
		y += ROW;

		y = section(ctx, "Keybinds", x, y, alpha);
		for (Keys.Labeled k : Keys.ALL) {
			keyRow(ctx, k.label(), k.key(), x, y, mouseX, mouseY, alpha);
			y += ROW;
		}

		y = section(ctx, "Performance", x, y, alpha);
		List<String> bg = List.of("Off", "15", "30", "60");
		choice(ctx, "Background FPS", "Frame cap while the game window isn't focused.", x, y, mouseX, mouseY, alpha,
				bg, Math.max(0, bg.indexOf(cfg.backgroundFps == 0 ? "Off" : Integer.toString(cfg.backgroundFps))),
				i -> cfg.backgroundFps = i == 0 ? 0 : Integer.parseInt(bg.get(i)));
		y += ROW;
		List<String> menu = List.of("Off", "30", "60", "120");
		choice(ctx, "Menu FPS", "Frame cap while a menu is open in a world.", x, y, mouseX, mouseY, alpha,
				menu, Math.max(0, menu.indexOf(cfg.menuFps == 0 ? "Off" : Integer.toString(cfg.menuFps))),
				i -> cfg.menuFps = i == 0 ? 0 : Integer.parseInt(menu.get(i)));
		y += ROW;

		y = section(ctx, "Layout", x, y, alpha);
		boolean armed = System.currentTimeMillis() < resetArmedUntil;
		row(ctx, x, y, mouseX, mouseY, alpha, null, click -> {
			if (System.currentTimeMillis() < resetArmedUntil) {
				Hud.ELEMENTS.forEach(Element::resetPosition);
				resetArmedUntil = 0;
			} else {
				resetArmedUntil = System.currentTimeMillis() + 3000;
			}
		});
		label(ctx, "Reset layout", x, y, alpha);
		if (armed) Type.UI.drawRight(ctx, "Click again", x + column(), y + 6, Paint.ink(0.95f * alpha));
		return y + ROW;
	}

	@Override
	protected boolean pressed(double cx, double cy, int button) {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;
		Press press = new Press(cx, cy, button == GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		for (Hit h : List.copyOf(hits)) {
			if (h.scrolls && (cy < TOP || cy > height - 16)) continue;
			if (press.secondary() && !h.secondary) continue;
			if (inside(cx, cy, h.x, h.y, h.w, h.h)) {
				if (!h.hexField) commitHex();
				h.action.accept(press);
				return true;
			}
		}
		commitHex();
		rebinding = null;
		return false;
	}

	@Override
	protected boolean dragged(double cx, double cy, int button) {
		if (dragging == null) return false;
		dragging.to(cx, cy);
		return true;
	}

	@Override
	protected boolean released(double cx, double cy, int button) {
		if (turning) {
			preview.release();
			if (Math.abs(turnLast - turnDown) < 2) preview.strike(now, OverlayConfig.get());
			turning = false;
		}
		dragging = null;
		return false;
	}

	@Override
	protected boolean scrolled(double mouseX, double mouseY, double vertical) {
		int max = Math.max(0, contentHeight - (height - TOP - 24));
		scroll.to((float) Math.max(0, Math.min(max, scroll.target() - vertical * 28)));
		return true;
	}

	@Override
	protected boolean typed(int codepoint) {
		if (editingHex == null) return false;
		if (Character.digit(codepoint, 16) >= 0 && hexBuffer.length() < 6) {
			hexBuffer += Character.toString(codepoint).toUpperCase(Locale.ROOT);
		}
		return true;
	}

	@Override
	protected boolean key(int key, int scancode, int modifiers) {
		if (editingHex != null) {
			if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
				commitHex();
			} else if (key == GLFW.GLFW_KEY_ESCAPE) {
				editingHex = null;
				hexBuffer = "";
			} else if (key == GLFW.GLFW_KEY_BACKSPACE) {
				if (!hexBuffer.isEmpty()) hexBuffer = hexBuffer.substring(0, hexBuffer.length() - 1);
			} else if (key == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
				String pasted = client.keyboard.getClipboard().replaceAll("[^0-9a-fA-F]", "").toUpperCase(Locale.ROOT);
				hexBuffer = pasted.length() > 6 ? pasted.substring(0, 6) : pasted;
			}
			return true;
		}
		if (rebinding != null) {
			if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
				rebinding.setBoundKey(InputUtil.UNKNOWN_KEY);
				KeyBinding.updateKeysByCode();
				client.options.write();
			} else if (key != GLFW.GLFW_KEY_ESCAPE) {
				Platform.bind(rebinding, key, scancode);
				KeyBinding.updateKeysByCode();
				client.options.write();
			}
			rebinding = null;
			return true;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE && detail != null) {
			closeDetail();
			return true;
		}
		if (Platform.matches(Keys.MENU, key, scancode)) {
			close();
			return true;
		}
		if (key == GLFW.GLFW_KEY_TAB) {
			switchTab((tab + 1) % TABS.length);
			return true;
		}
		return false;
	}

	@Override
	public void removed() {
		commitHex();
		Config.get().save();
		OverlayConfig.save();
	}
}
