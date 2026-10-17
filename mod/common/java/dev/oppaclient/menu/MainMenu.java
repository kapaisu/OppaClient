package dev.oppaclient.menu;

import dev.oppaclient.Logo;
import dev.oppaclient.Paint;
import dev.oppaclient.Type;
import dev.oppaclient.platform.BaseScreen;
import dev.oppaclient.platform.Gfx;
import dev.oppaclient.platform.Platform;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class MainMenu extends BaseScreen {
	private static final int LINE = 25;
	private static boolean seen;

	private record Item(String label, Runnable action, Paint.Smooth hover) {}

	private final List<Item> items = new ArrayList<>();
	private final Paint.Smooth open = new Paint.Smooth(seen ? 1 : 0, 6).to(1);

	public MainMenu() {
		super(Text.translatable("narrator.screen.title"));
		seen = true;
	}

	@Override
	protected void init() {
		items.clear();
		add("Singleplayer", () -> client.setScreen(new WorldsScreen(this)));
		add("Multiplayer", () -> client.setScreen(new ServersScreen(this)));
		if (FabricLoader.getInstance().isModLoaded("modmenu")) {
			add("Mods", this::openModMenu);
		}
		add("Oppa", () -> client.setScreen(new MenuScreen()));
		add("Options", () -> client.setScreen(new OptionsScreen(this, client.options)));
		add("Quit", client::scheduleStop);
	}

	private void add(String label, Runnable action) {
		items.add(new Item(label, action, new Paint.Smooth(0, 18)));
	}

	private void openModMenu() {
		try {
			Class<?> screen = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
			client.setScreen((Screen) screen.getConstructor(Screen.class).newInstance(this));
		} catch (ReflectiveOperationException | LinkageError e) {
			dev.oppaclient.OppaClient.LOG.warn("Mod Menu is installed but its screen couldn't be opened", e);
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	protected void drawBackground(Gfx ctx) {
		Backdrop.draw(ctx);
		ctx.fill(0, 0, width, height, Paint.shade(0.30f));
	}

	private int top() {
		return height - 28 - items.size() * LINE;
	}

	@Override
	protected void draw(Gfx ctx, int mouseX, int mouseY) {
		float a = open.get();
		int x = 28;
		int lift = Math.round((1 - a) * 10);
		int logoH = Math.min(46, height / 6);
		int y = top() - logoH - 22 + lift;
		Logo.draw(ctx, x - 2, y, logoH, a);

		y = top() + lift;
		for (Item item : items) {
			boolean hover = inside(mouseX, mouseY, x - 6, y - 4, Type.TITLE.width(item.label) + 12, LINE - 2);
			float h = item.hover.to(hover ? 1 : 0).get();
			Type.TITLE.draw(ctx, item.label, x + Math.round(h * 5), y, Paint.ink((0.62f + 0.36f * h) * a));
			y += LINE;
		}

		String version = "Minecraft " + Platform.gameVersion();
		Type.SMALL.drawRight(ctx, version, width - 14, height - 14, Paint.ink(0.45f * a));
		Type.SMALL.drawRight(ctx, client.getSession().getUsername(), width - 14, height - 26, Paint.ink(0.7f * a));
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	@Override
	protected boolean pressed(double cx, double cy, int button) {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
		int y = top();
		for (Item item : items) {
			if (inside(cx, cy, 22, y - 4, Type.TITLE.width(item.label) + 12, LINE - 2)) {
				item.action.run();
				return true;
			}
			y += LINE;
		}
		return false;
	}
}
