package dev.oppaclient.menu;

import dev.oppaclient.Config;
import dev.oppaclient.Paint;
import dev.oppaclient.Type;
import dev.oppaclient.hud.Element;
import dev.oppaclient.hud.Hud;
import dev.oppaclient.platform.BaseScreen;
import dev.oppaclient.platform.Gfx;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class LayoutScreen extends BaseScreen {
	private static final int MARGIN = 6, SNAP = 4;

	private final Screen parent;
	private final Paint.Smooth open = new Paint.Smooth(0, 14).to(1);
	private Element dragging;
	private double grabX, grabY;
	private boolean snapX, snapY;
	private long lastResize;

	public LayoutScreen(Screen parent) {
		super(Text.literal("Layout"));
		this.parent = parent;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	protected void drawBackground(Gfx ctx) {
		if (client.world == null) Backdrop.draw(ctx);
		ctx.fill(0, 0, width, height, Paint.shade(0.25f * open.get()));
	}

	@Override
	protected void draw(Gfx ctx, int mouseX, int mouseY) {
		Hud.nextFrame();
		float a = open.get();
		if (dragging != null) {
			if (snapX) Paint.hairline(ctx, width / 2f, 0, width / 2f, height, Paint.ink(0.35f));
			if (snapY) Paint.hairline(ctx, 0, height / 2f, width, height / 2f, Paint.ink(0.35f));
		}

		Element hovered = dragging != null ? dragging : at(mouseX, mouseY);
		for (Element e : Hud.ELEMENTS) {
			if (!e.placement().enabled) continue;
			Hud.Rect r = Hud.rect(e, client, true);
			Hud.draw(ctx, client, e, r, true);
			float edge = e == dragging ? 0.85f : e == hovered ? 0.5f : 0.14f;
			outline(ctx, r, Paint.ink(edge * a));
			if (e == hovered) {
				int ly = r.y() > 14 ? r.y() - 11 : r.y() + r.h() + 4;
				String size = Math.round(e.placement().scale * 100) + "%";
				Type.SMALL.draw(ctx, e.label, r.x(), ly, Paint.ink(0.75f * a));
				boolean resizing = System.currentTimeMillis() - lastResize < 1200;
				Type.SMALL.draw(ctx, size, r.x() + Type.SMALL.width(e.label) + 6, ly, Paint.ink((resizing ? 0.95f : 0.4f) * a));
			}
		}

		String hint = "Drag to move   Scroll to resize   Esc when done";
		int w = Type.SMALL.width(hint);
		Type.SMALL.draw(ctx, hint, (width - w) / 2, height / 2 - 4 + Math.round((1 - a) * 4), Paint.ink(dragging == null ? 0.5f * a : 0));
	}

	private static void outline(Gfx ctx, Hud.Rect r, int color) {
		float x0 = r.x() - 2, y0 = r.y() - 2, x1 = r.x() + r.w() + 2, y1 = r.y() + r.h() + 2;
		Paint.hairline(ctx, x0, y0, x1, y0, color);
		Paint.hairline(ctx, x0, y1, x1, y1, color);
		Paint.hairline(ctx, x0, y0, x0, y1, color);
		Paint.hairline(ctx, x1, y0, x1, y1 + 0.3f, color);
	}

	private Element at(double x, double y) {
		for (int i = Hud.ELEMENTS.size() - 1; i >= 0; i--) {
			Element e = Hud.ELEMENTS.get(i);
			if (e.placement().enabled && Hud.rect(e, client, true).contains(x, y)) return e;
		}
		return null;
	}

	@Override
	protected boolean pressed(double cx, double cy, int button) {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
		dragging = at(cx, cy);
		if (dragging != null) {
			Hud.Rect r = Hud.rect(dragging, client, true);
			grabX = cx - r.x();
			grabY = cy - r.y();
		}
		return dragging != null;
	}

	@Override
	protected boolean dragged(double cx, double cy, int button) {
		if (dragging == null) return false;
		Hud.Rect r = Hud.rect(dragging, client, true);
		int x = (int) Math.round(cx - grabX);
		int y = (int) Math.round(cy - grabY);
		float fit = Hud.fit();
		int margin = Math.round(MARGIN * fit);

		snapX = Math.abs(x + r.w() / 2 - width / 2) <= SNAP;
		snapY = Math.abs(y + r.h() / 2 - height / 2) <= SNAP;
		if (snapX) x = width / 2 - r.w() / 2;
		if (snapY) y = height / 2 - r.h() / 2;
		if (Math.abs(x - margin) <= SNAP) x = margin;
		if (Math.abs(x + r.w() - (width - margin)) <= SNAP) x = width - margin - r.w();
		if (Math.abs(y - margin) <= SNAP) y = margin;
		if (Math.abs(y + r.h() - (height - margin)) <= SNAP) y = height - margin - r.h();
		x = Math.max(0, Math.min(width - r.w(), x));
		y = Math.max(0, Math.min(height - r.h(), y));

		Config.Placement p = dragging.placement();
		p.ax = third(x + r.w() / 2f, width);
		p.ay = third(y + r.h() / 2f, height);
		p.ox = Math.round((x - Math.round(p.ax * (width - r.w()))) / fit);
		p.oy = Math.round((y - Math.round(p.ay * (height - r.h()))) / fit);
		return true;
	}

	private static float third(float centre, int size) {
		if (centre < size / 3f) return 0;
		if (centre > size * 2 / 3f) return 1;
		return 0.5f;
	}

	@Override
	protected boolean scrolled(double mouseX, double mouseY, double vertical) {
		Element e = dragging != null ? dragging : at(mouseX, mouseY);
		if (e == null || vertical == 0) return false;
		Config.Placement p = e.placement();
		p.scale = Math.round(Math.max(0.5f, Math.min(3f, p.scale + (vertical > 0 ? 0.05f : -0.05f))) * 20) / 20f;
		lastResize = System.currentTimeMillis();
		return true;
	}

	@Override
	protected boolean released(double cx, double cy, int button) {
		dragging = null;
		snapX = snapY = false;
		return false;
	}

	@Override
	protected boolean key(int key, int scancode, int modifiers) {
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			close();
			return true;
		}
		return false;
	}

	@Override
	public void close() {
		Config.get().save();
		client.setScreen(parent);
	}
}
