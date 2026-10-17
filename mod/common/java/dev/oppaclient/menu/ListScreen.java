package dev.oppaclient.menu;

import dev.oppaclient.Logo;
import dev.oppaclient.Paint;
import dev.oppaclient.Type;
import dev.oppaclient.platform.BaseScreen;
import dev.oppaclient.platform.Gfx;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

abstract class ListScreen extends BaseScreen {
	static final int TOP = 40, BAR = 46;

	protected final Screen parent;
	protected int selected = -1;
	private final Paint.Smooth open = new Paint.Smooth(0, 14).to(1);
	private final Paint.Smooth scroll = new Paint.Smooth(0, 18);
	private long lastClick;
	private int lastIndex = -1;

	enum Style { PRIMARY, SECONDARY, PLAIN }

	record Button(String label, Style style, BooleanSupplier enabled, Runnable run) {}

	private record Hit(int x, int y, int w, int h, Runnable run) {}

	private final List<Hit> hits = new ArrayList<>();

	protected ListScreen(Screen parent, String title) {
		super(Text.literal(title));
		this.parent = parent;
	}

	abstract int count();

	abstract int rowHeight();

	abstract void row(Gfx g, int index, int x, int y, int w, float alpha);

	abstract List<Button> buttons();

	abstract void activate(int index);

	abstract String empty();

	void delete(int index) {}

	protected int column() {
		return Math.min(460, width - 40);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	protected void drawBackground(Gfx g) {
		if (client.world == null) Backdrop.draw(g);
		g.fill(0, 0, width, height, Paint.shade(0.55f * open.get()));
	}

	@Override
	protected void draw(Gfx g, int mouseX, int mouseY) {
		hits.clear();
		float a = open.get();
		int lift = Math.round((1 - a) * 6);
		Logo.draw(g, 14, 9 + lift, 15, 0.92f * a);
		Type.STRONG.drawCentered(g, getTitle().getString(), width / 2, 13 + lift, Paint.ink(0.95f * a));

		int x = (width - column()) / 2;
		int rh = rowHeight();
		int listBottom = height - BAR;
		int max = Math.max(0, count() * rh - (listBottom - TOP - 8));
		if (scroll.target() > max) scroll.to(max);
		int y = TOP + 4 - Math.round(scroll.get());

		if (count() == 0) {
			Type.UI.drawCentered(g, empty(), width / 2, TOP + 60, Paint.ink(0.5f * a));
		}
		g.scissor(0, TOP, width, listBottom);
		for (int i = 0; i < count(); i++, y += rh) {
			if (y + rh < TOP || y > listBottom) continue;
			boolean hover = mouseY >= TOP && mouseY < listBottom && mouseX >= x - 8 && mouseX < x + column() + 8 && mouseY >= y && mouseY < y + rh;
			if (i == selected) Paint.round(g, x - 8, y, column() + 16, rh - 2, 8, Paint.ink(0.11f * a));
			else if (hover) Paint.round(g, x - 8, y, column() + 16, rh - 2, 8, Paint.ink(0.05f * a));
			row(g, i, x, y, column(), a);
			final int index = i;
			if (y + rh > TOP && y < listBottom) hits.add(new Hit(x - 8, Math.max(y, TOP), column() + 16, Math.min(y + rh, listBottom) - Math.max(y, TOP), () -> click(index)));
		}
		g.endScissor();

		drawButtons(g, mouseX, mouseY, a);
	}

	private void drawButtons(Gfx g, int mouseX, int mouseY, float a) {
		List<Button> list = buttons();
		int gap = 6, h = 20, total = -gap;
		int[] widths = new int[list.size()];
		for (int i = 0; i < list.size(); i++) {
			widths[i] = Type.STRONG.width(list.get(i).label()) + (list.get(i).style() == Style.PLAIN ? 14 : 26);
			total += widths[i] + gap;
		}
		int x = (width - total) / 2, y = height - BAR + (BAR - h) / 2;
		for (int i = 0; i < list.size(); i++) {
			Button b = list.get(i);
			int w = widths[i];
			boolean on = b.enabled().getAsBoolean();
			boolean hover = on && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
			float fade = on ? 1f : 0.35f;
			switch (b.style()) {
				case PRIMARY -> {
					Paint.round(g, x, y, w, h, h / 2f, Paint.ink((hover ? 1f : 0.88f) * a * fade));
					Type.STRONG.draw(g, b.label(), x + 13, y + 6, Paint.shade(0.9f * a));
				}
				case SECONDARY -> {
					Paint.round(g, x, y, w, h, h / 2f, Paint.ink((hover ? 0.16f : 0.1f) * a * fade));
					Type.STRONG.draw(g, b.label(), x + 13, y + 6, Paint.ink(0.92f * a * fade));
				}
				case PLAIN -> {
					if (hover) Paint.round(g, x, y, w, h, h / 2f, Paint.ink(0.06f * a));
					Type.STRONG.draw(g, b.label(), x + 7, y + 6, Paint.ink(0.75f * a * fade));
				}
			}
			if (on) hits.add(new Hit(x, y, w, h, b.run()));
			x += w + gap;
		}
	}

	private void click(int index) {
		long now = System.currentTimeMillis();
		if (index == lastIndex && now - lastClick < 350) {
			activate(index);
		}
		selected = index;
		lastIndex = index;
		lastClick = now;
	}

	@Override
	protected boolean pressed(double x, double y, int button) {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
		for (Hit h : List.copyOf(hits)) {
			if (x >= h.x && x < h.x + h.w && y >= h.y && y < h.y + h.h) {
				h.run.run();
				return true;
			}
		}
		selected = -1;
		return false;
	}

	@Override
	protected boolean scrolled(double x, double y, double amount) {
		int max = Math.max(0, count() * rowHeight() - (height - BAR - TOP - 8));
		scroll.to((float) Math.max(0, Math.min(max, scroll.target() - amount * 30)));
		return true;
	}

	@Override
	protected boolean key(int key, int scancode, int modifiers) {
		switch (key) {
			case GLFW.GLFW_KEY_ESCAPE -> close();
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				if (selected >= 0) activate(selected);
			}
			case GLFW.GLFW_KEY_DOWN -> selected = Math.min(count() - 1, selected + 1);
			case GLFW.GLFW_KEY_UP -> selected = Math.max(0, selected - 1);
			case GLFW.GLFW_KEY_DELETE -> {
				if (selected >= 0) delete(selected);
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	static String ago(long millis) {
		if (millis <= 0) return "Never played";
		long days = (System.currentTimeMillis() - millis) / 86_400_000L;
		if (days <= 0) return "Today";
		if (days == 1) return "Yesterday";
		if (days < 7) return days + " days ago";
		return java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")
				.format(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()));
	}
}
