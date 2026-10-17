package dev.oppaclient.platform;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public abstract class BaseScreen extends Screen {
	protected BaseScreen(Text title) {
		super(title);
	}

	protected abstract void draw(Gfx g, int mouseX, int mouseY);

	protected void drawBackground(Gfx g) {}

	protected boolean pressed(double x, double y, int button) {
		return false;
	}

	protected boolean released(double x, double y, int button) {
		return false;
	}

	protected boolean dragged(double x, double y, int button) {
		return false;
	}

	protected boolean scrolled(double x, double y, double amount) {
		return false;
	}

	protected boolean key(int key, int scancode, int modifiers) {
		return false;
	}

	protected boolean typed(int codepoint) {
		return false;
	}

	@Override
	public final void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		GfxImpl g = new GfxImpl(ctx);
		drawBackground(g);
		draw(g, mouseX, mouseY);
	}

	@Override
	public final void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {}

	@Override
	public final boolean mouseClicked(double x, double y, int button) {
		return pressed(x, y, button);
	}

	@Override
	public final boolean mouseReleased(double x, double y, int button) {
		return released(x, y, button);
	}

	@Override
	public final boolean mouseDragged(double x, double y, int button, double dx, double dy) {
		return dragged(x, y, button);
	}

	@Override
	public final boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		return scrolled(x, y, vertical);
	}

	@Override
	public final boolean keyPressed(int key, int scancode, int modifiers) {
		return key(key, scancode, modifiers) || super.keyPressed(key, scancode, modifiers);
	}

	@Override
	public final boolean charTyped(char chr, int modifiers) {
		return typed(chr) || super.charTyped(chr, modifiers);
	}
}
