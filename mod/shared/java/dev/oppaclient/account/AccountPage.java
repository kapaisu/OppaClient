package dev.oppaclient.account;

import dev.oppaclient.Logo;
import dev.oppaclient.Paint;
import dev.oppaclient.Type;
import dev.oppaclient.look.Images;
import dev.oppaclient.look.Looks;
import dev.oppaclient.look.Skins;
import dev.oppaclient.look.Stage;
import dev.oppaclient.look.Wardrobe;
import dev.oppaclient.overlay.Canvas;
import dev.oppaclient.platform.Gfx;
import dev.oppaclient.social.Api;
import dev.oppaclient.social.Social;
import dev.oppaclient.social.Toasts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class AccountPage {
	public interface Host {
		int width();

		int height();

		void close();

		String uuid();

		String name();

		String accessToken();

		String server();

		void pick(Consumer<Path> done);

		void onGameThread(Runnable r);
	}

	private record Hit(int x, int y, int w, int h, Consumer<Boolean> action) {}

	private static final int TOP = 34;
	private static final String[] TABS = {"Skin", "Account"};

	private final Host host;
	private final List<Hit> hits = new ArrayList<>();
	private final Stage stage = new Stage();
	private final Map<String, Images.Image> thumbs = new HashMap<>();
	private final Canvas faces = Canvas.flat();
	private final Paint.Smooth open = new Paint.Smooth(0, 14).to(1);
	private int tab;
	private List<Library.Saved> saved = Library.list();
	private Library.Saved picked;
	private boolean slim;
	private boolean slimChosen;
	private volatile boolean busy;
	private volatile String status;
	private String armed;
	private long armedUntil;
	private double turnLast;
	private boolean turning;

	public AccountPage(Host host, int tab) {
		this.host = host;
		this.tab = tab;
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private Images.Image thumb(Library.Saved s) {
		return thumbs.computeIfAbsent(s.file, f -> {
			try {
				return Images.decode(Files.readAllBytes(Library.path(s)));
			} catch (IOException | RuntimeException e) {
				return null;
			}
		});
	}

	private void face(Gfx g, Images.Image img, int x, int y, int size, float alpha) {
		if (img == null) {
			Paint.round(g, x, y, size, size, 3, Paint.ink(0.08f * alpha));
			return;
		}
		float px = size / 8f;
		faces.alpha(alpha);
		for (int layer = 0; layer < 2; layer++) {
			int u = layer == 0 ? 8 : 40;
			for (int j = 0; j < 8; j++) {
				for (int i = 0; i < 8; i++) {
					int c = img.at(u + i, 8 + j);
					if (layer == 1 && (c >>> 24) < 128) continue;
					faces.rect(x + i * px, y + j * px, x + (i + 1) * px, y + (j + 1) * px, c | 0xFF000000);
				}
			}
		}
		g.canvas(faces, x, y, size, size);
	}

	private int pill(Gfx g, String label, int right, int y, int mx, int my, float a, boolean solid, boolean enabled, Runnable action) {
		int w = Type.STRONG.width(label) + 24, h = 17;
		int x = right - w;
		boolean hover = enabled && inside(mx, my, x, y, w, h);
		if (solid) {
			Paint.round(g, x, y, w, h, h / 2f, Paint.ink((enabled ? hover ? 1f : 0.88f : 0.3f) * a));
			Type.STRONG.draw(g, label, x + 12, y + 4, Paint.shade(0.9f * a));
		} else {
			Paint.round(g, x, y, w, h, h / 2f, Paint.ink((hover ? 0.16f : 0.09f) * a));
			Type.STRONG.draw(g, label, x + 12, y + 4, Paint.ink((enabled ? 0.92f : 0.4f) * a));
		}
		if (enabled) hits.add(new Hit(x, y, w, h, secondary -> action.run()));
		return x - 6;
	}

	public void draw(Gfx g, int mx, int my) {
		hits.clear();
		float a = open.get();
		int w = host.width();
		Logo.draw(g, 14, 9, 15, 0.92f * a);
		drawTabs(g, mx, my, a);
		pill(g, "Done", w - 14, 8, mx, my, a, true, true, host::close);
		if (tab == 0) skin(g, mx, my, a);
		else accounts(g, mx, my, a);
	}

	private void drawTabs(Gfx g, int mx, int my, float a) {
		int gap = 4, padX = 9, total = -gap;
		for (String t : TABS) total += Type.STRONG.width(t) + padX * 2 + gap;
		int x = (host.width() - total) / 2;
		for (int i = 0; i < TABS.length; i++) {
			int tw = Type.STRONG.width(TABS[i]) + padX * 2;
			if (i == tab) Paint.round(g, x, 8, tw, 17, 8.5f, Paint.ink(0.12f * a));
			else if (inside(mx, my, x, 8, tw, 17)) Paint.round(g, x, 8, tw, 17, 8.5f, Paint.ink(0.05f * a));
			Type.STRONG.draw(g, TABS[i], x + padX, 12, Paint.ink((i == tab ? 0.95f : 0.6f) * a));
			int index = i;
			hits.add(new Hit(x, 8, tw, 17, secondary -> {
				tab = index;
				armed = null;
			}));
			x += tw + gap;
		}
	}

	private Skins.Skin current() {
		Skins.Skin s = Skins.get(host.uuid());
		if (s == null && Skins.failed(host.uuid())) return new Skins.Skin(dev.oppaclient.Cosmetics.defaultSkin(), false);
		return s;
	}

	private void skin(Gfx g, int mx, int my, float a) {
		int column = Math.min(460, host.width() - 40);
		int left = (host.width() - column) / 2;
		int stageW = Math.min(170, column / 2 - 10), stageH = Math.min(host.height() - TOP - 40, Math.round(stageW * 1.45f));
		int sx = left, sy = TOP + 10;
		Paint.round(g, sx, sy, stageW, stageH, 12, Paint.ink(0.04f * a));
		Images.Image shown = picked != null ? thumb(picked) : current() != null ? current().image() : null;
		boolean shownSlim = picked != null ? slim : current() != null && current().slim();
		if (shown != null) stage.skin(shown, shownSlim);
		Social.Cape cape = Wardrobe.capeEntry();
		stage.cape(cape == null ? null : Looks.cape(cape.url()));
		if (shown != null) {
			stage.draw(g, sx, sy + 6, stageW, stageH - 12, Wardrobe.hat(), Wardrobe.wings(), Wardrobe.aura(), cape != null, System.nanoTime() / 1e9f, a);
		} else {
			Type.SMALL.drawCentered(g, "Loading your skin…", sx + stageW / 2, sy + stageH / 2, Paint.ink(0.45f * a));
		}
		hits.add(new Hit(sx, sy, stageW, stageH, secondary -> turning = true));

		int x = sx + stageW + 22, right = left + column;
		int y = sy;
		Type.SMALL.draw(g, "Your skins", x, y + 2, Paint.ink(0.45f * a));
		y += 16;
		int tile = 28, gap = 8, perRow = Math.max(1, (right - x + gap) / (tile + gap));
		List<Library.Saved> list = saved;
		int count = list.size() + 1;
		for (int i = 0; i < count; i++) {
			int tx = x + (i % perRow) * (tile + gap), ty = y + (i / perRow) * (tile + gap);
			boolean hover = inside(mx, my, tx, ty, tile, tile);
			if (i == 0) {
				Paint.round(g, tx, ty, tile, tile, 6, Paint.ink((hover ? 0.14f : 0.07f) * a));
				Type.STRONG.drawCentered(g, "+", tx + tile / 2, ty + tile / 2 - 4, Paint.ink(0.8f * a));
				hits.add(new Hit(tx, ty, tile, tile, secondary -> host.pick(this::add)));
				continue;
			}
			Library.Saved s = list.get(i - 1);
			boolean chosen = picked != null && picked.file.equals(s.file);
			if (chosen) Paint.round(g, tx - 3, ty - 3, tile + 6, tile + 6, 8, Paint.ink(0.16f * a));
			else if (hover) Paint.round(g, tx - 3, ty - 3, tile + 6, tile + 6, 8, Paint.ink(0.07f * a));
			face(g, thumb(s), tx + 2, ty + 2, tile - 4, a);
			hits.add(new Hit(tx - 3, ty - 3, tile + 6, tile + 6, secondary -> {
				picked = s;
				slim = s.slim();
				slimChosen = false;
				status = null;
			}));
		}
		y += ((count - 1) / perRow + 1) * (tile + gap) + 4;
		if (!Library.available()) {
			Type.SMALL.draw(g, "Start Minecraft from the Oppa launcher to keep skins.", x, y, Paint.ink(0.5f * a));
			y += 14;
		}

		Type.SMALL.draw(g, "Model", x, y + 6, Paint.ink(0.45f * a));
		int mright = right;
		String[] models = {"Slim", "Classic"};
		for (String m : models) {
			int mw = Type.UI.width(m) + 14;
			int px = mright - mw;
			boolean on = m.equals("Slim") == slim;
			if (on) Paint.round(g, px, y + 2, mw, 15, 7.5f, Paint.ink(0.14f * a));
			else if (inside(mx, my, px, y + 2, mw, 15)) Paint.round(g, px, y + 2, mw, 15, 7.5f, Paint.ink(0.06f * a));
			Type.UI.draw(g, m, px + 7, y + 6, Paint.ink((on ? 0.95f : 0.5f) * a));
			boolean value = m.equals("Slim");
			hits.add(new Hit(px, y + 2, mw, 15, secondary -> {
				slim = value;
				slimChosen = true;
			}));
			mright = px - 4;
		}
		y += 26;
		boolean ready = picked != null && !busy && host.accessToken() != null;
		pill(g, busy ? "Applying…" : "Use this skin", right, y, mx, my, a, true, ready, this::apply);
		y += 26;
		String line = status != null ? status : picked == null ? "Pick a skin, or press + to add one from a file. You can also drop a file here." : null;
		if (line != null) wrap(g, line, x, y, right - x, a);
	}

	private void wrap(Gfx g, String text, int x, int y, int width, float a) {
		StringBuilder row = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = row.isEmpty() ? word : row + " " + word;
			if (Type.SMALL.width(next) > width && !row.isEmpty()) {
				Type.SMALL.draw(g, row.toString(), x, y, Paint.ink(0.55f * a));
				y += 11;
				row.setLength(0);
				row.append(word);
			} else {
				row.setLength(0);
				row.append(next);
			}
		}
		if (!row.isEmpty()) Type.SMALL.draw(g, row.toString(), x, y, Paint.ink(0.55f * a));
	}

	private void add(Path file) {
		try {
			byte[] png = Files.readAllBytes(file);
			Library.check(png);
			boolean guess = slimChosen ? slim : guessSlim(Images.decode(png));
			Library.Saved s = Library.remember(png, guess);
			saved = Library.list();
			thumbs.remove(s.file);
			picked = s;
			slim = guess;
			status = "Added. Press Use this skin to wear it.";
		} catch (IOException | RuntimeException e) {
			status = Api.message(e);
		}
	}

	private static boolean guessSlim(Images.Image img) {
		return img.height() == 64 && (img.at(54, 20) >>> 24) == 0 && (img.at(54, 31) >>> 24) == 0;
	}

	public void drop(List<Path> paths) {
		if (paths.isEmpty()) return;
		tab = 0;
		add(paths.getFirst());
	}

	private void apply() {
		Library.Saved s = picked;
		String token = host.accessToken();
		if (s == null || token == null) return;
		byte[] png;
		try {
			png = Files.readAllBytes(Library.path(s));
		} catch (IOException e) {
			status = Api.message(e);
			return;
		}
		boolean useSlim = slim;
		busy = true;
		status = null;
		Mojang.upload(token, png, useSlim).whenComplete((v, err) -> host.onGameThread(() -> {
			busy = false;
			if (err != null) {
				status = Api.message(err);
				return;
			}
			Images.Image img = thumb(s);
			if (img != null) Skins.put(host.uuid(), new Skins.Skin(img, useSlim));
			try {
				Library.remember(png, useSlim);
				saved = Library.list();
			} catch (IOException ignored) {
			}
			picked = null;
			status = "Done. Other players see your new skin after you rejoin.";
			Toasts.show("Skin changed", "Rejoin to see it on servers", Toasts.Kind.INFO);
		}));
	}

	private void accounts(Gfx g, int mx, int my, float a) {
		int column = Math.min(300, host.width() - 40);
		int x = (host.width() - column) / 2, y = TOP + 10;
		if (!Accounts.available()) {
			Type.UI.draw(g, "Start Minecraft from the Oppa launcher to switch accounts.", x, y + 6, Paint.ink(0.6f * a));
			return;
		}
		Type.SMALL.draw(g, "Accounts", x, y + 2, Paint.ink(0.45f * a));
		y += 16;
		String me = host.uuid() == null ? "" : host.uuid().replace("-", "");
		for (Accounts.Entry e : Accounts.all()) {
			boolean current = e.uuid().equals(me);
			boolean hover = !current && inside(mx, my, x - 8, y, column + 16, 30);
			if (hover) Paint.round(g, x - 8, y, column + 16, 30, 7, Paint.ink(0.05f * a));
			Skins.Skin s = Skins.get(e.uuid());
			face(g, s == null ? null : s.image(), x, y + 5, 20, a);
			Type.STRONG.draw(g, e.name(), x + 28, y + 6, Paint.ink(0.95f * a));
			String key = "switch:" + e.uuid();
			boolean confirm = key.equals(armed) && System.currentTimeMillis() < armedUntil;
			String detail = current ? "Signed in" : confirm ? "Click again to sign in as " + e.name() : "Switch to this account";
			Type.SMALL.draw(g, detail, x + 28, y + 18, Paint.ink((confirm ? 0.85f : 0.5f) * a));
			if (!current) {
				hits.add(new Hit(x - 8, y, column + 16, 30, secondary -> {
					if (Switcher.busy()) return;
					if (key.equals(armed) && System.currentTimeMillis() < armedUntil) {
						armed = null;
						Switcher.to(e, host.server(), host::onGameThread, line -> status = line);
					} else {
						armed = key;
						armedUntil = System.currentTimeMillis() + 4000;
					}
				}));
			}
			y += 32;
		}
		y += 6;
		if (status != null) {
			Type.SMALL.draw(g, status, x, y, Paint.ink(0.8f * a));
			y += 14;
		}
		Type.SMALL.draw(g, "Add accounts in the Oppa launcher.", x, y, Paint.ink(0.45f * a));
		if (host.server() != null) {
			Type.SMALL.draw(g, "You'll leave " + host.server() + " and join it again as the new account.", x, y + 12, Paint.ink(0.45f * a));
		}
	}

	public boolean press(double x, double y, boolean secondary) {
		for (Hit h : List.copyOf(hits)) {
			if (inside(x, y, h.x, h.y, h.w, h.h)) {
				h.action.accept(secondary);
				turnLast = x;
				return true;
			}
		}
		return false;
	}

	public boolean drag(double x) {
		if (!turning) return false;
		stage.turn((float) (x - turnLast));
		turnLast = x;
		return true;
	}

	public void release() {
		turning = false;
	}
}
