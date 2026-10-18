package dev.oppaclient.platform;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public abstract class BaseScreen extends Screen {
	protected BaseScreen(Component title) {
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

	protected void dropped(java.util.List<java.nio.file.Path> paths) {}

	@Override
	public final void onFilesDrop(java.util.List<java.nio.file.Path> paths) {
		dropped(paths);
	}

	@Override
	public final void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		GfxImpl g = new GfxImpl(ctx);
		draw(g, mouseX, mouseY);
		dev.oppaclient.social.ToastView.draw(g, width, 36);
	}

	@Override
	public final void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		drawBackground(new GfxImpl(ctx));
	}

	@Override
	public final boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		return pressed(click.x(), click.y(), click.button());
	}

	@Override
	public final boolean mouseReleased(MouseButtonEvent click) {
		return released(click.x(), click.y(), click.button());
	}

	@Override
	public final boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
		return dragged(click.x(), click.y(), click.button());
	}

	@Override
	public final boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		return scrolled(x, y, vertical);
	}

	@Override
	public final boolean keyPressed(KeyEvent input) {
		return key(input.key(), input.keycode(), input.modifiers()) || super.keyPressed(input);
	}

	@Override
	public final boolean charTyped(CharacterEvent input) {
		return typed(input.codepoint()) || super.charTyped(input);
	}
}
