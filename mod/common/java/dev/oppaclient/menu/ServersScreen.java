package dev.oppaclient.menu;

import dev.oppaclient.Icons;
import dev.oppaclient.Paint;
import dev.oppaclient.Pixels;
import dev.oppaclient.Type;
import dev.oppaclient.platform.Gfx;
import dev.oppaclient.platform.Platform;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.DirectConnectScreen;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ServersScreen extends ListScreen {
	private static final ExecutorService PINGS = Executors.newFixedThreadPool(4, r -> {
		Thread t = new Thread(r, "Oppa server ping");
		t.setDaemon(true);
		return t;
	});

	private final ServerList list;
	private final MultiplayerServerListPinger pinger = new MultiplayerServerListPinger();
	private final Map<ServerInfo, byte[]> faviconBytes = new HashMap<>();
	private final Map<ServerInfo, Pixels> favicons = new HashMap<>();

	public ServersScreen(Screen parent) {
		super(parent, "Multiplayer");
		list = new ServerList(net.minecraft.client.MinecraftClient.getInstance());
		list.loadFile();
		refresh();
	}

	private void refresh() {
		for (int i = 0; i < list.size(); i++) ping(list.get(i));
	}

	private void ping(ServerInfo info) {
		info.label = Text.literal("Pinging");
		info.ping = -2;
		info.playerCountLabel = Text.empty();
		PINGS.submit(() -> {
			try {
				Platform.ping(pinger, info, () -> net.minecraft.client.MinecraftClient.getInstance().execute(list::saveFile), () -> {});
			} catch (Exception e) {
				info.ping = -1;
				info.label = Text.literal("Can't reach this server");
			}
		});
	}

	@Override
	public void tick() {
		pinger.tick();
	}

	@Override
	public void removed() {
		pinger.cancel();
	}

	@Override
	int count() {
		return list.size();
	}

	@Override
	int rowHeight() {
		return 46;
	}

	@Override
	String empty() {
		return "No servers yet. Add one or connect directly.";
	}

	private Pixels favicon(ServerInfo info) {
		byte[] bytes = info.getFavicon();
		if (bytes == null) return null;
		if (!Arrays.equals(bytes, faviconBytes.get(info))) {
			faviconBytes.put(info, bytes);
			favicons.put(info, Pixels.read(bytes));
			Icons.forget("server:" + info.address);
		}
		return favicons.get(info);
	}

	@Override
	void row(Gfx g, int index, int x, int y, int w, float alpha) {
		ServerInfo info = list.get(index);
		Pixels icon = favicon(info);
		if (icon != null) {
			Icons.draw(g, "server:" + info.address, icon, x, y + 6, 32, alpha);
		} else {
			Paint.round(g, x, y + 6, 32, 32, 6.4f, Paint.ink(0.08f * alpha));
		}
		int right = x + w;
		String ping = info.ping >= 0 ? info.ping + " ms" : "";
		String players = info.playerCountLabel == null ? "" : info.playerCountLabel.getString();
		if (!ping.isEmpty()) Type.SMALL.drawRight(g, ping, right, y + 6, Paint.ink(0.45f * alpha));
		int playersRight = ping.isEmpty() ? right : right - Type.SMALL.width(ping) - 10;
		if (!players.isEmpty()) Type.SMALL.drawRight(g, players, playersRight, y + 6, Paint.ink(0.6f * alpha));
		Type.STRONG.draw(g, info.name, x + 42, y + 5, Paint.ink(0.95f * alpha));
		if (info.label != null) {
			g.wrapped(info.label, x + 42, y + 18, w - 42, 2, 10, Paint.ink(0.6f * alpha));
		}
	}

	private ServerInfo current() {
		return selected >= 0 && selected < list.size() ? list.get(selected) : null;
	}

	@Override
	List<Button> buttons() {
		return List.of(
				new Button("Join", Style.PRIMARY, () -> current() != null, () -> activate(selected)),
				new Button("Add Server", Style.SECONDARY, () -> true, this::add),
				new Button("Direct Connect", Style.SECONDARY, () -> true, this::direct),
				new Button("Edit", Style.PLAIN, () -> current() != null, this::edit),
				new Button("Delete", Style.PLAIN, () -> current() != null, () -> delete(selected)),
				new Button("Refresh", Style.PLAIN, () -> true, () -> client.setScreen(new ServersScreen(parent))),
				new Button("Back", Style.PLAIN, () -> true, this::close));
	}

	private void add() {
		ServerInfo info = new ServerInfo("Minecraft Server", "", ServerInfo.ServerType.OTHER);
		client.setScreen(Platform.serverForm(this, ok -> {
			if (ok) {
				list.add(info, false);
				list.saveFile();
			}
			client.setScreen(new ServersScreen(parent));
		}, info, true));
	}

	private void edit() {
		ServerInfo info = current();
		if (info == null) return;
		client.setScreen(Platform.serverForm(this, ok -> {
			if (ok) list.saveFile();
			client.setScreen(new ServersScreen(parent));
		}, info, false));
	}

	private void direct() {
		ServerInfo info = new ServerInfo("Minecraft Server", "", ServerInfo.ServerType.OTHER);
		client.setScreen(new DirectConnectScreen(this, ok -> {
			if (ok) connect(info);
			else client.setScreen(this);
		}, info));
	}

	private void connect(ServerInfo info) {
		ConnectScreen.connect(this, client, ServerAddress.parse(info.address), info, false, null);
	}

	@Override
	void activate(int index) {
		if (index < 0 || index >= list.size()) return;
		connect(list.get(index));
	}

	@Override
	void delete(int index) {
		ServerInfo info = index >= 0 && index < list.size() ? list.get(index) : null;
		if (info == null) return;
		client.setScreen(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				list.remove(info);
				list.saveFile();
				selected = -1;
			}
			client.setScreen(this);
		}, Text.literal("Remove \"" + info.name + "\"?"), Text.literal("It will be taken off your server list.")));
	}
}
