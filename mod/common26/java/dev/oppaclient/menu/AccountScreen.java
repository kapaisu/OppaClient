package dev.oppaclient.menu;

import dev.oppaclient.Paint;
import dev.oppaclient.account.AccountPage;
import dev.oppaclient.platform.BaseScreen;
import dev.oppaclient.platform.Gfx;
import dev.oppaclient.platform.Platform;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class AccountScreen extends BaseScreen {
	private final Screen parent;
	private final AccountPage page;

	public AccountScreen(Screen parent, int tab) {
		super(Component.literal("Account"));
		this.parent = parent;
		this.page = new AccountPage(new AccountPage.Host() {
			public int width() {
				return width;
			}

			public int height() {
				return height;
			}

			public void close() {
				AccountScreen.this.onClose();
			}

			public String uuid() {
				UUID id = minecraft.getUser().getProfileId();
				return id == null ? null : id.toString();
			}

			public String name() {
				return minecraft.getUser().getName();
			}

			public String accessToken() {
				String t = minecraft.getUser().getAccessToken();
				return t == null || t.isBlank() || t.length() < 20 ? null : t;
			}

			public String server() {
				ServerData info = minecraft.level == null || minecraft.hasSingleplayerServer() ? null : minecraft.getCurrentServer();
				return info == null ? null : info.ip.trim();
			}

			public void pick(Consumer<Path> done) {
				Platform.pickImage("Choose a skin", done);
			}

			public void onGameThread(Runnable r) {
				minecraft.execute(r);
			}
		}, tab);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		Platform.show(parent);
	}

	@Override
	protected void drawBackground(Gfx ctx) {
		if (minecraft.level == null) Backdrop.draw(ctx);
		ctx.fill(0, 0, width, height, Paint.shade(0.55f));
	}

	@Override
	protected void draw(Gfx ctx, int mouseX, int mouseY) {
		page.draw(ctx, mouseX, mouseY);
	}

	@Override
	protected boolean pressed(double x, double y, int button) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT && button != InputConstants.MOUSE_BUTTON_RIGHT) return false;
		return page.press(x, y, button == InputConstants.MOUSE_BUTTON_RIGHT);
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
