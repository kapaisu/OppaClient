package dev.oppaclient.menu;

import dev.oppaclient.Paint;
import dev.oppaclient.Picker;
import dev.oppaclient.account.AccountPage;
import dev.oppaclient.platform.BaseScreen;
import dev.oppaclient.platform.Gfx;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class AccountScreen extends BaseScreen {
	private final Screen parent;
	private final AccountPage page;

	public AccountScreen(Screen parent, int tab) {
		super(Text.literal("Account"));
		this.parent = parent;
		this.page = new AccountPage(new AccountPage.Host() {
			public int width() {
				return width;
			}

			public int height() {
				return height;
			}

			public void close() {
				AccountScreen.this.close();
			}

			public String uuid() {
				UUID id = client.getSession().getUuidOrNull();
				return id == null ? null : id.toString();
			}

			public String name() {
				return client.getSession().getUsername();
			}

			public String accessToken() {
				String t = client.getSession().getAccessToken();
				return t == null || t.isBlank() || t.length() < 20 ? null : t;
			}

			public String server() {
				ServerInfo info = client.world == null || client.isInSingleplayer() ? null : client.getCurrentServerEntry();
				return info == null ? null : info.address.trim();
			}

			public void pick(Consumer<Path> done) {
				Picker.image("Choose a skin", done);
			}

			public void onGameThread(Runnable r) {
				client.execute(r);
			}
		}, tab);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	@Override
	protected void drawBackground(Gfx ctx) {
		if (client.world == null) Backdrop.draw(ctx);
		ctx.fill(0, 0, width, height, Paint.shade(0.55f));
	}

	@Override
	protected void draw(Gfx ctx, int mouseX, int mouseY) {
		page.draw(ctx, mouseX, mouseY);
	}

	@Override
	protected boolean pressed(double x, double y, int button) {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;
		return page.press(x, y, button == GLFW.GLFW_MOUSE_BUTTON_RIGHT);
	}

	@Override
	protected boolean dragged(double x, double y, int button) {
		return page.drag(x);
	}

	@Override
	protected boolean released(double x, double y, int button) {
		page.release();
		return false;
	}

	@Override
	protected void dropped(List<Path> paths) {
		page.drop(paths);
	}
}
