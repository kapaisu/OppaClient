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
import dev.oppaclient.Picker;
import dev.oppaclient.OppaClient;
import dev.oppaclient.look.CapeEditor;
import dev.oppaclient.look.Catalog;
import dev.oppaclient.look.Images;
import dev.oppaclient.look.Look;
import dev.oppaclient.look.Looks;
import dev.oppaclient.look.Skins;
import dev.oppaclient.look.Stage;
import dev.oppaclient.look.Wardrobe;
import dev.oppaclient.perf.Limits;
import dev.oppaclient.social.Api;
import dev.oppaclient.social.Chat;
import dev.oppaclient.social.Hosting;
import dev.oppaclient.social.Social;
import dev.oppaclient.social.Toasts;
import dev.oppaclient.platform.BaseScreen;
import dev.oppaclient.platform.Gfx;
import dev.oppaclient.platform.Platform;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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
	private static final String[] TABS = {"HUD", "Visuals", "PvP", "Cosmetics", "Friends", "Settings"};
	public static final int PVP = 2, COSMETICS = 3, FRIENDS = 4;
	private static final String OVERLAY = "overlay", ZOOM = "zoom", FREELOOK = "freelook", CROSSHAIR = "crosshair", CAPE = "cape";
	private static int lastTab;

	private int tab = lastTab;
	private Object detail;
	private long chatSeen;
	private boolean stickBottom;

	private record ChatWith(String uuid, String name) {}
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

	private String fieldKey;
	private String fieldBuffer = "";
	private String search = "";
	private Social.Found found;
	private String foundProblem;
	private boolean searching;
	private final Map<String, String> busy = new HashMap<>();
	private String armed;
	private long armedUntil;
	private final Stage stage = new Stage();
	private boolean turningStage;
	private double stageLast;
	private CapeEditor editor;
	private CapeEditor shownPicture;
	private int viewX, viewY, viewW, viewH;
	private final double[] panLast = new double[2];

	public MenuScreen() {
		this(-1);
	}

	public MenuScreen(int open) {
		super(Text.literal("Oppa"));
		if (open >= 0) tab = lastTab = open;
	}

	public boolean onFriends() {
		return tab == FRIENDS && detail == null;
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
		if (detail instanceof ChatWith with) {
			y = chatPage(ctx, with, x, y, mouseX, my, alpha);
		} else if (detail instanceof Element e) {
			y = elementPage(ctx, e, x, y, mouseX, my, alpha);
		} else if (CAPE.equals(detail) && editor != null) {
			y = capePage(ctx, x, y, mouseX, my, alpha);
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
		} else if (tab == PVP) {
			y = pvp(ctx, x, y, mouseX, my, alpha);
		} else if (tab == COSMETICS) {
			y = cosmetics(ctx, x, y, mouseX, my, alpha);
		} else if (tab == FRIENDS) {
			y = friends(ctx, x, y, mouseX, my, alpha);
		} else {
			y = settings(ctx, x, y, mouseX, my, alpha);
		}
		ctx.endScissor();
		contentHeight = y - start;
		if (stickBottom) {
			stickBottom = false;
			scroll.to(Math.max(0, contentHeight - (height - TOP - 24)));
		}

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
		leaveChat();
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

	private void leaveChat() {
		if (!(detail instanceof ChatWith)) return;
		Chat.close();
		if ("chat".equals(fieldKey)) fieldKey = null;
	}

	private void openChat(String uuid, String name) {
		Chat.open(uuid);
		openDetail(new ChatWith(uuid, name));
		fieldKey = "chat";
		fieldBuffer = "";
		stickBottom = true;
	}

	private void sendChat() {
		if (!(detail instanceof ChatWith with)) return;
		if (Chat.send(with.uuid(), fieldBuffer)) {
			fieldBuffer = "";
			stickBottom = true;
		} else if (!fieldBuffer.isBlank()) {
			Toasts.show("Message not sent", Chat.online() ? null : "Chat is offline", Toasts.Kind.INFO);
		}
	}

	private List<String> wrap(String text, int w) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (Type.UI.width(next) <= w || line.isEmpty()) {
				line.setLength(0);
				line.append(next);
				while (Type.UI.width(line.toString()) > w && line.length() > 1) {
					int cut = line.length() - 1;
					while (cut > 1 && Type.UI.width(line.substring(0, cut)) > w) cut--;
					lines.add(line.substring(0, cut));
					line.delete(0, cut);
				}
			} else {
				lines.add(line.toString());
				line.setLength(0);
				line.append(word);
			}
		}
		if (!line.isEmpty()) lines.add(line.toString());
		return lines;
	}

	private int chatPage(Gfx ctx, ChatWith with, int x, int y, int mouseX, int mouseY, float alpha) {
		y = back(ctx, with.name(), x, y, mouseX, mouseY, alpha);
		List<Chat.Message> messages = Chat.thread(with.uuid());
		if (!Chat.online()) {
			Type.SMALL.draw(ctx, Chat.problem() != null ? Chat.problem() : "Connecting…", x, y + 4, Paint.ink(0.5f * alpha));
			y += ROW;
		} else if (messages.isEmpty()) {
			Type.SMALL.draw(ctx, "Say hi to " + with.name() + ".", x, y + 4, Paint.ink(0.5f * alpha));
			y += ROW;
		}
		String last = null;
		for (Chat.Message m : messages) {
			boolean mine = Chat.mine(m);
			String who = mine ? "You" : with.name();
			if (!who.equals(last)) {
				y += 6;
				Type.SMALL.draw(ctx, who, x, y, Paint.ink(0.45f * alpha));
				y += 12;
				last = who;
			}
			for (String line : wrap(m.text(), column())) {
				Type.UI.draw(ctx, line, x, y, Paint.ink((mine ? 0.7f : 0.95f) * alpha));
				y += 12;
			}
		}
		y += 10;
		field(ctx, "chat", "Message " + with.name(), "", x, y, column() - Type.UI.width("Send") - 26, mouseX, mouseY, alpha);
		chip(ctx, "Send", x + column(), y + 3, mouseX, mouseY, alpha, true, click -> sendChat());
		if (Chat.changed() != chatSeen) {
			chatSeen = Chat.changed();
			stickBottom = true;
		}
		return y + ROW + 8;
	}

	private void closeDetail() {
		leaveChat();
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

	private boolean arm(String key) {
		long now = System.currentTimeMillis();
		if (key.equals(armed) && now < armedUntil) {
			armed = null;
			return true;
		}
		armed = key;
		armedUntil = now + 3000;
		return false;
	}

	private boolean armedFor(String key) {
		return key.equals(armed) && System.currentTimeMillis() < armedUntil;
	}

	private int chip(Gfx ctx, String label, int right, int y, int mouseX, int mouseY, float alpha, boolean solid, Consumer<Press> action) {
		int w = Type.UI.width(label) + 16, h = 15;
		int x = right - w;
		boolean hover = action != null && inside(mouseX, mouseY, x, y, w, h);
		if (solid) {
			Paint.round(ctx, x, y, w, h, h / 2f, Paint.ink((hover ? 1f : 0.88f) * alpha));
			Type.UI.draw(ctx, label, x + 8, y + 4, Paint.shade(0.9f * alpha));
		} else {
			Paint.round(ctx, x, y, w, h, h / 2f, Paint.ink((hover ? 0.16f : 0.09f) * alpha));
			Type.UI.draw(ctx, label, x + 8, y + 4, Paint.ink((action == null ? 0.45f : 0.9f) * alpha));
		}
		if (action != null) hits.add(new Hit(x, y, w, h, true, action));
		return x - 6;
	}

	private void field(Gfx ctx, String key, String placeholder, String value, int x, int y, int w, int mouseX, int mouseY, float alpha) {
		boolean editing = key.equals(fieldKey);
		boolean hover = inside(mouseX, mouseY, x - 8, y, w + 8, ROW);
		Paint.round(ctx, x - 8, y + 1, w + 8, ROW - 2, 7, Paint.ink((editing ? 0.1f : hover ? 0.08f : 0.06f) * alpha));
		String shown = editing ? fieldBuffer : value;
		if (shown.isEmpty() && !editing) Type.UI.draw(ctx, placeholder, x, y + 6, Paint.ink(0.4f * alpha));
		else Type.UI.draw(ctx, shown + (editing && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : ""), x, y + 6, Paint.ink(0.95f * alpha));
		hits.add(new Hit(x - 8, y, w + 8, ROW, true, false, true, click -> {
			if (!key.equals(fieldKey)) {
				fieldKey = key;
				fieldBuffer = value;
			}
		}));
	}

	private void commitField() {
		if (fieldKey == null) return;
		if (fieldKey.equals("chat")) {
			sendChat();
			return;
		}
		String key = fieldKey, value = fieldBuffer.trim();
		fieldKey = null;
		if (key.equals("search")) {
			search = value;
			lookup();
		}
	}

	private void lookup() {
		found = null;
		foundProblem = null;
		if (search.isEmpty()) return;
		if (!search.matches("[A-Za-z0-9_]{1,16}")) {
			foundProblem = "That isn't a Minecraft name.";
			return;
		}
		searching = true;
		String asked = search;
		Social.lookup(asked).whenComplete((f, err) -> client.execute(() -> {
			if (!asked.equals(search)) return;
			searching = false;
			if (err != null) foundProblem = Api.message(err);
			else found = f;
		}));
	}

	private void act(String key, java.util.concurrent.CompletableFuture<?> work, String done) {
		busy.put(key, "");
		work.whenComplete((r, err) -> client.execute(() -> {
			busy.remove(key);
			if (err != null) Toasts.show("Couldn't do that", Api.message(err), Toasts.Kind.INFO);
			else if (done != null) Toasts.show(done, null, Toasts.Kind.INFO);
			if (!search.isEmpty() && found != null) lookup();
		}));
	}

	private String currentServer() {
		if (client.world == null) return null;
		if (client.isInSingleplayer()) return Hosting.ready() ? Hosting.address() : null;
		if (client.getCurrentServerEntry() == null) return null;
		return Hosting.label(client.getCurrentServerEntry().address.trim());
	}

	private void toggleHost() {
		if (Hosting.active()) {
			Hosting.stop();
			return;
		}
		int port = Platform.openLan();
		if (port < 0) Toasts.show("Couldn't open this world", null, Toasts.Kind.INFO);
		else Hosting.start(port);
	}

	private static String hostLine() {
		if (!Hosting.active()) return "Let friends join this world from anywhere.";
		if (!Hosting.ready()) return Hosting.problem() != null ? Hosting.problem() : "Opening…";
		int n = Hosting.guests();
		return n == 0 ? "Hosting | invite friends below" : "Hosting | " + n + (n == 1 ? " friend here" : " friends here");
	}

	private int person(Gfx ctx, String uuid, String name, String detail, int x, int y, int mouseX, int mouseY, float alpha) {
		int w = column();
		if (inside(mouseX, mouseY, x - 8, y, w + 16, 30)) Paint.round(ctx, x - 8, y, w + 16, 30, 7, Paint.ink(0.04f * alpha));
		Skins.face(ctx, uuid, x, y + 5, 20, alpha);
		Type.STRONG.draw(ctx, name, x + 28, y + 6, Paint.ink(0.95f * alpha));
		Type.SMALL.draw(ctx, detail, x + 28, y + 18, Paint.ink(0.5f * alpha));
		return y + 30;
	}

	private int friends(Gfx ctx, int x, int y, int mouseX, int mouseY, float alpha) {
		if (!Api.signedIn()) {
			Type.UI.draw(ctx, "Start Minecraft from the Oppa launcher to use friends.", x, y + 6, Paint.ink(0.6f * alpha));
			return y + ROW * 2;
		}
		int right = x + column();
		if (Platform.singleplayer()) {
			y = section(ctx, "This world", x, y, alpha);
			Type.UI.draw(ctx, hostLine(), x, y + 6, Paint.ink(0.6f * alpha));
			chip(ctx, Hosting.active() ? "Stop" : "Host", right, y + 3, mouseX, mouseY, alpha, !Hosting.active(), click -> toggleHost());
			y += ROW + 6;
		}
		int fieldW = column() - Type.UI.width("Search") - 26;
		field(ctx, "search", "Look up a player", search, x, y, fieldW, mouseX, mouseY, alpha);
		chip(ctx, "Search", right, y + 3, mouseX, mouseY, alpha, true, click -> {
			if (fieldKey != null) commitField();
			else lookup();
		});
		y += ROW + 6;
		if (searching) {
			Type.UI.draw(ctx, "Looking up " + search + "…", x, y + 6, Paint.ink(0.5f * alpha));
			y += ROW;
		} else if (foundProblem != null) {
			Type.UI.draw(ctx, foundProblem, x, y + 6, Paint.ink(0.6f * alpha));
			y += ROW;
		} else if (found != null) {
			Social.Found f = found;
			String detail = !f.oppa() ? "Doesn't use Oppa yet" : f.me() ? "That's you" : f.friend() ? "Already your friend" : f.requested() ? "Request sent" : f.wants() ? "Wants to be friends" : "Uses Oppa";
			int top = y;
			y = person(ctx, f.oppa() ? f.uuid() : null, f.name(), detail, x, y, mouseX, mouseY, alpha);
			if (f.oppa() && !f.me()) {
				String key = "found:" + f.uuid();
				if (busy.containsKey(key)) chip(ctx, "…", right, top + 7, mouseX, mouseY, alpha, false, null);
				else if (f.friend()) {
					String server = currentServer();
					if (server != null) chip(ctx, "Invite", right, top + 7, mouseX, mouseY, alpha, true,
							click -> act(key, Social.invite(f.uuid(), server), "Invited " + f.name()));
				} else if (!f.requested()) {
					chip(ctx, f.wants() ? "Accept" : "Add friend", right, top + 7, mouseX, mouseY, alpha, true,
							click -> act(key, Social.friend("add", f.uuid()), f.wants() ? "You and " + f.name() + " are friends" : "Sent " + f.name() + " a request"));
				}
			}
		}

		Social.State st = Social.state();
		if (!Social.loaded()) {
			y = section(ctx, "Friends", x, y, alpha);
			Type.UI.draw(ctx, Social.problem() != null ? Social.problem() : "Loading…", x, y + 6, Paint.ink(0.5f * alpha));
			return y + ROW;
		}
		String here = currentServer();
		if (!st.invites().isEmpty()) {
			y = section(ctx, "Invites", x, y, alpha);
			for (Social.Invite inv : st.invites()) {
				int top = y;
				y = person(ctx, inv.uuid(), inv.name(), "Invited you to " + Social.place(inv.server()), x, y, mouseX, mouseY, alpha);
				if (!inv.server().equalsIgnoreCase(here == null ? "" : here)) {
					chip(ctx, "Join", right, top + 7, mouseX, mouseY, alpha, true, click -> Platform.join(inv.server()));
				}
			}
		}
		if (!st.incoming().isEmpty()) {
			y = section(ctx, "Requests", x, y, alpha);
			for (Social.Person p : st.incoming()) {
				int top = y;
				y = person(ctx, p.uuid(), p.name(), "Wants to be friends", x, y, mouseX, mouseY, alpha);
				String key = "req:" + p.uuid();
				if (busy.containsKey(key)) {
					chip(ctx, "…", right, top + 7, mouseX, mouseY, alpha, false, null);
				} else {
					int next = chip(ctx, "Accept", right, top + 7, mouseX, mouseY, alpha, true, click -> act(key, Social.friend("accept", p.uuid()), "You and " + p.name() + " are friends"));
					chip(ctx, "Decline", next, top + 7, mouseX, mouseY, alpha, false, click -> act(key, Social.friend("decline", p.uuid()), null));
				}
			}
		}
		y = section(ctx, st.friends().isEmpty() ? "No friends yet" : "Friends", x, y, alpha);
		if (st.friends().isEmpty()) {
			Type.SMALL.draw(ctx, "Look up someone who plays with Oppa and add them.", x, y + 6, Paint.ink(0.5f * alpha));
			y += ROW;
		}
		for (Social.Friend f : st.friends()) {
			int top = y;
			String key = "friend:" + f.uuid();
			boolean removing = armedFor(key);
			y = person(ctx, f.uuid(), f.name(), removing ? "Right-click again to remove" : f.describe(), x, y, mouseX, mouseY, alpha * (f.online() ? 1f : 0.6f));
			if (inside(mouseX, mouseY, x - 8, top, column() + 16, 30)) hint = "Right-click to remove.";
			if (busy.containsKey(key)) {
				chip(ctx, "…", right, top + 7, mouseX, mouseY, alpha, false, null);
			} else {
				int next = right;
				if (f.joinable() && !f.server().equalsIgnoreCase(here == null ? "" : here)) {
					next = chip(ctx, "Join", next, top + 7, mouseX, mouseY, alpha, true, click -> Platform.join(f.server()));
				}
				if (here != null && f.online() && !here.equalsIgnoreCase(f.server())) {
					next = chip(ctx, "Invite", next, top + 7, mouseX, mouseY, alpha, false, click -> act(key, Social.invite(f.uuid(), here), "Invited " + f.name()));
				}
				int unread = Chat.unread(f.uuid());
				chip(ctx, unread > 0 ? "Message | " + unread : "Message", next, top + 7, mouseX, mouseY, alpha, unread > 0, click -> openChat(f.uuid(), f.name()));
			}
			hits.add(new Hit(x - 8, top, column() + 16, 30, true, true, false, click -> {
				if (click.secondary() && arm(key)) act(key, Social.friend("remove", f.uuid()), "Removed " + f.name());
			}));
		}
		if (!st.outgoing().isEmpty()) {
			y = section(ctx, "Sent", x, y, alpha);
			for (Social.Person p : st.outgoing()) {
				int top = y;
				y = person(ctx, p.uuid(), p.name(), "Waiting for them to accept", x, y, mouseX, mouseY, alpha);
				String key = "sent:" + p.uuid();
				if (!busy.containsKey(key)) chip(ctx, "Cancel", right, top + 7, mouseX, mouseY, alpha, false, click -> act(key, Social.friend("cancel", p.uuid()), null));
			}
		}
		return y + 8;
	}

	private static List<String> names(Catalog.Item[] items) {
		List<String> out = new ArrayList<>();
		out.add("None");
		for (Catalog.Item i : items) out.add(i.name());
		return out;
	}

	private int lookRow(Gfx ctx, String key, String label, Catalog.Item[] items, Look look, Consumer<Look> set, int x, int y, int mouseX, int mouseY, float alpha) {
		int picked = look.none() ? 0 : Catalog.index(items, look.style()) + 1;
		choice(ctx, label, null, x, y, mouseX, mouseY, alpha, names(items), picked, i -> {
			if (i == 0) set.accept(Look.NONE);
			else {
				Catalog.Item item = items[i - 1];
				set.accept(look.none() ? new Look(item.id(), item.primary(), item.secondary()) : new Look(item.id(), look.primary(), look.secondary()));
			}
		});
		y += ROW;
		if (look.none()) return y;
		Catalog.Item item = Catalog.find(items, look.style());
		y = color(ctx, key + ".a", item.primaryLabel(), null, x, y, mouseX, mouseY, alpha, look.primary(), item.primary(), c -> set.accept(look.primary(c)));
		y = color(ctx, key + ".b", item.secondaryLabel(), null, x, y, mouseX, mouseY, alpha, look.secondary(), item.secondary(), c -> set.accept(look.secondary(c)));
		if (items == Catalog.HATS) {
			float lift = look.lift();
			slider(ctx, "Height", "Moves the hat up or down on your head.", x, y, mouseX, mouseY, alpha, lift, -Look.MAX_LIFT, Look.MAX_LIFT,
					lift == 0 ? "Default" : String.format(Locale.ROOT, "%+.1f", lift), v -> set.accept(Wardrobe.hat().lift(stepped(v, 0.5f))));
			y += ROW;
			float glow = look.glow();
			slider(ctx, "Glow", "How bright the glowing parts shine.", x, y, mouseX, mouseY, alpha, glow, 0, 1,
					glow == 0 ? "Off" : Math.round(glow * 100) + "%", v -> set.accept(Wardrobe.hat().glow(stepped(v, 0.1f))));
			y += ROW;
		}
		return y;
	}

	private void capeTile(Gfx ctx, Social.Cape cape, int x, int y, int w, int h, int mouseX, int mouseY, float alpha) {
		boolean chosen = cape == null ? Wardrobe.cape() == null : Wardrobe.cape() != null && Wardrobe.cape() == cape.id();
		boolean hover = inside(mouseX, mouseY, x - 3, y - 3, w + 6, h + 6);
		if (chosen) Paint.round(ctx, x - 3, y - 3, w + 6, h + 6, 6, Paint.ink(0.16f * alpha));
		else if (hover) Paint.round(ctx, x - 3, y - 3, w + 6, h + 6, 6, Paint.ink(0.07f * alpha));
		Images.Image img = cape == null ? null : Looks.cape(cape.url());
		if (cape == null) {
			Type.SMALL.drawCentered(ctx, "None", x + w / 2, y + h / 2 - 4, Paint.ink(0.55f * alpha));
		} else if (img == null) {
			Paint.round(ctx, x, y, w, h, 3, Paint.ink(0.08f * alpha));
		} else {
			Canvas c = Canvas.flat().alpha(alpha * (cape.state().equals("rejected") ? 0.35f : 1f));
			int s = img.width() / 64;
			float cw = w / 10f, chh = h / 16f;
			for (int j = 0; j < 16; j++) for (int i = 0; i < 10; i++) {
				int px = img.at((1 + i) * s + s / 2, (1 + j) * s + s / 2);
				c.rect(x + i * cw, y + j * chh, x + (i + 1) * cw, y + (j + 1) * chh, px | 0xFF000000);
			}
			ctx.canvas(c, x, y, w, h);
		}
		if (cape != null && !cape.state().equals("approved")) {
			String tag = armedFor("cape:" + cape.id()) ? "Delete?" : cape.state().equals("rejected") ? "Rejected" : "In review";
			Type.SMALL.drawCentered(ctx, tag, x + w / 2, y + h + 5, Paint.ink(0.5f * alpha));
		} else if (cape != null && armedFor("cape:" + cape.id())) {
			Type.SMALL.drawCentered(ctx, "Delete?", x + w / 2, y + h + 5, Paint.ink(0.8f * alpha));
		}
		hits.add(new Hit(x - 3, y - 3, w + 6, h + 6, true, true, false, click -> {
			if (click.secondary()) {
				if (cape != null && arm("cape:" + cape.id())) Wardrobe.delete(cape);
				return;
			}
			if (cape == null) Wardrobe.cape(null);
			else if (!cape.state().equals("rejected")) Wardrobe.cape(cape.id());
		}));
		if (hover && cape != null) hint = "Right-click to delete.";
	}

	private void wardrobeStage(Gfx ctx, int sx, int sy, int sw, int sh, int mouseX, int mouseY, float alpha, boolean scrolls) {
		Social.Cape worn = Wardrobe.capeEntry();
		wardrobeStage(ctx, sx, sy, sw, sh, mouseX, mouseY, alpha, scrolls, worn == null ? null : Looks.cape(worn.url()), worn != null);
	}

	private void wardrobeStage(Gfx ctx, int sx, int sy, int sw, int sh, int mouseX, int mouseY, float alpha, boolean scrolls, Images.Image capeImage, boolean showCape) {
		Paint.round(ctx, sx, sy, sw, sh, 12, Paint.ink(0.04f * alpha));
		java.util.UUID me = client.getSession().getUuidOrNull();
		Skins.Skin skin = Skins.get(me == null ? null : me.toString());
		if (skin != null) stage.skin(skin.image(), skin.slim());
		else if (Skins.failed(me == null ? null : me.toString())) stage.skin(dev.oppaclient.Cosmetics.defaultSkin(), false);
		stage.cape(capeImage);
		stage.draw(ctx, sx, sy + 6, sw, sh - 12, Wardrobe.hat(), Wardrobe.wings(), Wardrobe.aura(), showCape, (float) now, alpha);
		if (skin == null && !Skins.failed(me == null ? null : me.toString())) Type.SMALL.drawCentered(ctx, "Loading your skin…", sx + sw / 2, sy + sh - 16, Paint.ink(0.45f * alpha));
		if (inside(mouseX, mouseY, sx, sy, sw, sh)) hint = "Drag to turn around.";
		hits.add(new Hit(sx, sy, sw, sh, scrolls, click -> {
			stageLast = click.x();
			dragging = (mx, my) -> {
				stage.turn((float) (mx - stageLast));
				stageLast = mx;
			};
		}));
	}

	private int cosmetics(Gfx ctx, int x, int y, int mouseX, int mouseY, float alpha) {
		int side = (width - column()) / 2 - 36;
		if (side >= 130) {
			int sw = Math.min(200, side), sh = Math.min(height - TOP - 30, Math.round(sw * 1.35f));
			wardrobeStage(ctx, (width + column()) / 2 + 24, TOP + 12, sw, sh, mouseX, mouseY, alpha, false);
		} else {
			wardrobeStage(ctx, x, y, column(), 170, mouseX, mouseY, alpha, true);
			y += 180;
		}
		if (!Api.signedIn()) {
			Type.SMALL.draw(ctx, "Start Minecraft from the Oppa launcher to save cosmetics.", x, y + 6, Paint.ink(0.6f * alpha));
			y += ROW;
		}
		y = section(ctx, "Cape", x, y, alpha);
		row(ctx, x, y, mouseX, mouseY, alpha, "Or drop an image onto this window.", click -> Picker.image("Choose an image for your cape", this::editCape));
		label(ctx, Wardrobe.uploading() ? "Making your cape…" : "Use an image…", x, y, alpha);
		Type.UI.drawRight(ctx, "›", x + column(), y + 6, Paint.ink(0.3f * alpha));
		y += ROW;
		if (Wardrobe.status() != null) {
			Type.SMALL.draw(ctx, Wardrobe.status(), x, y + 4, Paint.ink(0.55f * alpha));
			y += 16;
		}
		List<Social.Cape> capes = Social.state().capes();
		int tw = 24, th = 38, gap = 14, perRow = Math.max(1, (column() + gap) / (tw + gap));
		int cy = y + 6;
		for (int i = 0; i <= capes.size(); i++) {
			int col = i % perRow, rowN = i / perRow;
			capeTile(ctx, i == 0 ? null : capes.get(i - 1), x + col * (tw + gap), cy + rowN * (th + 20), tw, th, mouseX, mouseY, alpha);
		}
		y = cy + ((capes.size()) / perRow + 1) * (th + 20);

		y = section(ctx, "Hat", x, y, alpha);
		y = lookRow(ctx, "hat", "Style", Catalog.HATS, Wardrobe.hat(), Wardrobe::hat, x, y, mouseX, mouseY, alpha);
		y = section(ctx, "Wings", x, y, alpha);
		y = lookRow(ctx, "wings", "Style", Catalog.WINGS, Wardrobe.wings(), Wardrobe::wings, x, y, mouseX, mouseY, alpha);
		y = section(ctx, "Aura", x, y, alpha);
		y = lookRow(ctx, "aura", "Style", Catalog.AURAS, Wardrobe.aura(), Wardrobe::aura, x, y, mouseX, mouseY, alpha);
		return y + ROW;
	}

	private void editCape(java.nio.file.Path file) {
		Wardrobe.open(file, ready -> client.execute(() -> {
			editor = ready;
			stage.face((float) Math.toRadians(200));
			if (tab != COSMETICS) switchTab(COSMETICS);
			openDetail(CAPE);
		}));
	}

	private int capePage(Gfx ctx, int x, int y, int mouseX, int mouseY, float alpha) {
		y = back(ctx, "Fit your cape", x, y, mouseX, mouseY, alpha);
		int side = (width - column()) / 2 - 36;
		boolean beside = side >= 130;
		if (beside) {
			int sw = Math.min(200, side), sh = Math.min(height - TOP - 30, Math.round(sw * 1.35f));
			wardrobeStage(ctx, (width + column()) / 2 + 24, TOP + 12, sw, sh, mouseX, mouseY, alpha, false, editor.cape(), true);
		}
		viewX = x;
		viewY = y;
		viewW = column();
		viewH = 220;
		Paint.round(ctx, viewX, viewY, viewW, viewH, 12, Paint.ink(0.04f * alpha));
		int frameH = viewH - 24, frameW = Math.round(frameH * CapeEditor.ASPECT);
		int frameX = viewX + (viewW - frameW) / 2, frameY = viewY + 12;
		float scale = frameH / editor.cropHeight();
		Images.Image picture = editor.picture();
		Identifier texture = OppaClient.id("cape/editor");
		if (shownPicture != editor) {
			Platform.upload(texture, picture.width(), picture.height(), picture.argb());
			shownPicture = editor;
		}
		float k = editor.sourceWidth() / picture.width();
		ctx.scissor(viewX, Math.max(viewY, TOP), viewX + viewW, viewY + viewH);
		ctx.push();
		ctx.translate(frameX - editor.left() * scale, frameY - editor.top() * scale);
		ctx.scale(scale * k, scale * k);
		ctx.blit(texture, 0, 0, picture.width(), picture.height(), 0, 0, picture.width(), picture.height(), Paint.argb(alpha, 0xFFFFFF));
		ctx.pop();
		int dim = Paint.shade(0.62f * alpha);
		ctx.fill(viewX, viewY, viewX + viewW, frameY, dim);
		ctx.fill(viewX, frameY + frameH, viewX + viewW, viewY + viewH, dim);
		ctx.fill(viewX, frameY, frameX, frameY + frameH, dim);
		ctx.fill(frameX + frameW, frameY, viewX + viewW, frameY + frameH, dim);
		int edge = Paint.ink(0.7f * alpha);
		ctx.fill(frameX - 1, frameY - 1, frameX + frameW + 1, frameY, edge);
		ctx.fill(frameX - 1, frameY + frameH, frameX + frameW + 1, frameY + frameH + 1, edge);
		ctx.fill(frameX - 1, frameY, frameX, frameY + frameH, edge);
		ctx.fill(frameX + frameW, frameY, frameX + frameW + 1, frameY + frameH, edge);
		ctx.endScissor();
		ctx.scissor(0, TOP, width, height - 16);
		if (inside(mouseX, mouseY, viewX, viewY, viewW, viewH)) hint = "Drag to move the image. Scroll to zoom.";
		hits.add(new Hit(viewX, viewY, viewW, viewH, true, click -> {
			panLast[0] = click.x();
			panLast[1] = click.y();
			dragging = (mx, my) -> {
				editor.drag((float) (mx - panLast[0]), (float) (my - panLast[1]), scale);
				panLast[0] = mx;
				panLast[1] = my;
			};
		}));
		y += viewH + 8;
		slider(ctx, "Zoom", null, x, y, mouseX, mouseY, alpha, editor.zoom(), 1, 8,
				String.format(Locale.ROOT, "%.1fx", editor.zoom()), v -> editor.zoom(v));
		y += ROW;
		if (!beside) {
			wardrobeStage(ctx, x, y + 6, column(), 170, mouseX, mouseY, alpha, true, editor.cape(), true);
			y += 182;
		}
		y += 8;
		int next = chip(ctx, Wardrobe.uploading() ? "Uploading…" : "Use this cape", x + column(), y, mouseX, mouseY, alpha, true, Wardrobe.uploading() ? null : click -> {
			Wardrobe.upload(editor.result());
			editor = null;
			closeDetail();
		});
		chip(ctx, "Cancel", next, y, mouseX, mouseY, alpha, false, click -> {
			editor = null;
			closeDetail();
		});
		return y + ROW + 8;
	}

	@Override
	protected void dropped(List<java.nio.file.Path> paths) {
		if (paths.isEmpty()) return;
		if (tab != COSMETICS) switchTab(COSMETICS);
		editCape(paths.getFirst());
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
		toggle(ctx, "fullbright", "Fullbright", null, x, y, mouseX, mouseY, alpha, () -> cfg.fullbright, v -> cfg.fullbright = v);
		y += ROW;
		toggle(ctx, "nametag", "Own nametag", "Shows your name above you in third person.", x, y, mouseX, mouseY, alpha,
				() -> cfg.ownNametag, v -> cfg.ownNametag = v);
		y += ROW;
		toggle(ctx, "badges", "Oppa badges", "Shows the Oppa logo next to other players using Oppa.", x, y, mouseX, mouseY, alpha,
				() -> cfg.badges, v -> cfg.badges = v);
		y += ROW;
		toggle(ctx, "cosmetics", "Other players' cosmetics", "Shows the capes, hats, wings and auras other Oppa players wear.", x, y, mouseX, mouseY, alpha,
				() -> cfg.cosmetics, v -> cfg.cosmetics = v);
		y += ROW;
		List<String> times = List.of("Off", "Day", "Sunset", "Night");
		choice(ctx, "Time", "Only changes what you see. Everyone else keeps the real time.", x, y, mouseX, mouseY, alpha,
				times, Math.max(0, times.indexOf(cfg.time)), i -> cfg.time = times.get(i));
		y += ROW;
		toggle(ctx, "weather", "Clear weather", "Hides rain and storms on your screen only.", x, y, mouseX, mouseY, alpha,
				() -> cfg.clearWeather, v -> cfg.clearWeather = v);
		return y + ROW;
	}

	private int pvp(Gfx ctx, int x, int y, int mouseX, int mouseY, float alpha) {
		Config cfg = Config.get();
		y = section(ctx, "HUD", x, y, alpha);
		for (String id : List.of("keys", "cps", "reach", "combo", "armor", "items")) {
			for (Element e : Hud.ELEMENTS) {
				if (!e.id.equals(id)) continue;
				Config.Placement p = e.placement();
				link(ctx, "hud." + e.id, e.label, x, y, mouseX, mouseY, alpha, () -> p.enabled, v -> p.enabled = v, () -> openDetail(e));
				y += ROW;
			}
		}
		y = section(ctx, "Movement", x, y, alpha);
		toggle(ctx, "pvp.sprint", "Toggle sprint", "Press sprint once instead of holding it.", x, y, mouseX, mouseY, alpha,
				() -> client.options.getSprintToggled().getValue(), v -> {
					client.options.getSprintToggled().setValue(v);
					client.options.write();
				});
		y += ROW;
		toggle(ctx, "pvp.sneak", "Toggle sneak", "Press sneak once instead of holding it.", x, y, mouseX, mouseY, alpha,
				() -> client.options.getSneakToggled().getValue(), v -> {
					client.options.getSneakToggled().setValue(v);
					client.options.write();
				});
		y += ROW;
		y = section(ctx, "Screen", x, y, alpha);
		link(ctx, "crosshair", "Crosshair", x, y, mouseX, mouseY, alpha, () -> cfg.crosshair, v -> cfg.crosshair = v, () -> openDetail(CROSSHAIR));
		y += ROW;
		slider(ctx, "Low fire", "Lowers the fire overlay so you can see while burning.", x, y, mouseX, mouseY, alpha,
				cfg.lowFire, 0, 1, cfg.lowFire == 0 ? "Off" : Math.round(cfg.lowFire * 100) + "%", v -> cfg.lowFire = stepped(v, 0.05f));
		y += ROW;
		double tiltNow = client.options.getDamageTiltStrength().getValue();
		slider(ctx, "Damage tilt", "How much the camera shakes when you get hit.", x, y, mouseX, mouseY, alpha,
				(float) tiltNow, 0, 1, tiltNow == 0 ? "Off" : Math.round(tiltNow * 100) + "%", v -> {
					client.options.getDamageTiltStrength().setValue((double) stepped(v, 0.05f));
					client.options.write();
				});
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
		row(ctx, x, y, mouseX, mouseY, alpha, "Change your skin or switch Minecraft account.", click -> client.setScreen(new AccountScreen(this, 0)));
		label(ctx, "Skin and account", x, y, alpha);
		Type.UI.drawRight(ctx, "›", x + column(), y + 6, Paint.ink(0.3f * alpha));
		y += ROW + 6;
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
		choice(ctx, "Particles", "Caps particle bursts, skips far ones and thins out crit particles.", x, y, mouseX, mouseY, alpha,
				Limits.LEVELS, Math.max(0, Limits.LEVELS.indexOf(cfg.particles)), i -> cfg.particles = Limits.LEVELS.get(i));
		y += ROW;
		choice(ctx, "Distant entities", "Hides far dropped items, XP orbs, item frames and armor stands.", x, y, mouseX, mouseY, alpha,
				Limits.LEVELS, Math.max(0, Limits.LEVELS.indexOf(cfg.entities)), i -> cfg.entities = Limits.LEVELS.get(i));
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
		if (!"chat".equals(fieldKey)) commitField();
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
		if (CAPE.equals(detail) && editor != null && inside(mouseX, mouseY, viewX, viewY, viewW, viewH)) {
			editor.zoom(editor.zoom() * (float) Math.pow(1.12, vertical));
			return true;
		}
		int max = Math.max(0, contentHeight - (height - TOP - 24));
		scroll.to((float) Math.max(0, Math.min(max, scroll.target() - vertical * 28)));
		return true;
	}

	@Override
	protected boolean typed(int codepoint) {
		if ("chat".equals(fieldKey)) {
			if (codepoint >= 32 && fieldBuffer.length() < 256) fieldBuffer += Character.toString(codepoint);
			return true;
		}
		if (fieldKey != null) {
			if (fieldBuffer.length() < 16 && (Character.isLetterOrDigit(codepoint) || codepoint == '_') && codepoint < 128) {
				fieldBuffer += Character.toString(codepoint);
			}
			return true;
		}
		if (editingHex == null) return false;
		if (Character.digit(codepoint, 16) >= 0 && hexBuffer.length() < 6) {
			hexBuffer += Character.toString(codepoint).toUpperCase(Locale.ROOT);
		}
		return true;
	}

	@Override
	protected boolean key(int key, int scancode, int modifiers) {
		if (fieldKey != null) {
			if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
				commitField();
			} else if (key == GLFW.GLFW_KEY_ESCAPE) {
				fieldKey = null;
			} else if (key == GLFW.GLFW_KEY_BACKSPACE) {
				if (!fieldBuffer.isEmpty()) fieldBuffer = fieldBuffer.substring(0, fieldBuffer.length() - 1);
			} else if (key == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
				boolean chat = fieldKey.equals("chat");
				String pasted = chat ? fieldBuffer + client.keyboard.getClipboard().replaceAll("\\p{Cntrl}", " ") : client.keyboard.getClipboard().replaceAll("[^A-Za-z0-9_]", "");
				int max = chat ? 256 : 16;
				fieldBuffer = pasted.length() > max ? pasted.substring(0, max) : pasted;
			}
			return true;
		}
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
		Chat.close();
		commitHex();
		fieldKey = null;
		Config.get().save();
		OverlayConfig.save();
	}
}
