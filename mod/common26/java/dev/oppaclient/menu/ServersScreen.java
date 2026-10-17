package dev.oppaclient.menu;

import dev.oppaclient.Icons;
import dev.oppaclient.Paint;
import dev.oppaclient.Pixels;
import dev.oppaclient.Type;
import dev.oppaclient.platform.Gfx;
import dev.oppaclient.platform.Platform;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

public final class ServersScreen extends ListScreen {
	private static final ExecutorService PINGS = Executors.newFixedThreadPool(4, r -> {
		Thread t = new Thread(r, "Oppa server ping");
		t.setDaemon(true);
		return t;
	});

	private final ServerList list;
	private final ServerStatusPinger pinger = new ServerStatusPinger();
	private final Map<ServerData, byte[]> faviconBytes = new HashMap<>();
	private final Map<ServerData, Pixels> favicons = new HashMap<>();

	public ServersScreen(Screen parent) {
		super(parent, "Multiplayer");
		list = new ServerList(net.minecraft.client.Minecraft.getInstance());
		list.load();
		refresh();
	}

	private void refresh() {
		for (int i = 0; i < list.size(); i++) ping(list.get(i));
	}

	private void ping(ServerData info) {
		info.motd = Component.literal("Pinging");
		info.ping = -2;
		info.status = Component.empty();
		PINGS.submit(() -> {
			try {
				Platform.ping(pinger, info, () -> net.minecraft.client.Minecraft.getInstance().execute(list::save), () -> {});
			} catch (Exception e) {
				info.ping = -1;
				info.motd = Component.literal("Can't reach this server");
			}
		});
	}

	@Override
	public void tick() {
		pinger.tick();
	}

	@Override
	public void removed() {
		pinger.removeAll();
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

	private Pixels favicon(ServerData info) {
		byte[] bytes = info.getIconBytes();
		if (bytes == null) return null;
		if (!Arrays.equals(bytes, faviconBytes.get(info))) {
			faviconBytes.put(info, bytes);
			favicons.put(info, Pixels.read(bytes));
			Icons.forget("server:" + info.ip);
		}
		return favicons.get(info);
	}

	@Override
	void row(Gfx g, int index, int x, int y, int w, float alpha) {
		ServerData info = list.get(index);
		Pixels icon = favicon(info);
		if (icon != null) {
			Icons.draw(g, "server:" + info.ip, icon, x, y + 6, 32, alpha);
		} else {
			Paint.round(g, x, y + 6, 32, 32, 6.4f, Paint.ink(0.08f * alpha));
		}
		int right = x + w;
		String ping = info.ping >= 0 ? info.ping + " ms" : "";
		String players = info.status == null ? "" : info.status.getString();
		if (!ping.isEmpty()) Type.SMALL.drawRight(g, ping, right, y + 6, Paint.ink(0.45f * alpha));
		int playersRight = ping.isEmpty() ? right : right - Type.SMALL.width(ping) - 10;
		if (!players.isEmpty()) Type.SMALL.drawRight(g, players, playersRight, y + 6, Paint.ink(0.6f * alpha));
		Type.STRONG.draw(g, info.name, x + 42, y + 5, Paint.ink(0.95f * alpha));
		if (info.motd != null) {
			g.wrapped(info.motd, x + 42, y + 18, w - 42, 2, 10, Paint.ink(0.6f * alpha));
		}
	}

	private ServerData current() {
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
				new Button("Refresh", Style.PLAIN, () -> true, () -> Platform.show(new ServersScreen(parent))),
				new Button("Back", Style.PLAIN, () -> true, this::onClose));
	}

	private void add() {
		ServerData info = new ServerData("Minecraft Server", "", ServerData.Type.OTHER);
		Platform.show(Platform.serverForm(this, ok -> {
			if (ok) {
				list.add(info, false);
				list.save();
			}
			Platform.show(new ServersScreen(parent));
		}, info, true));
	}

	private void edit() {
		ServerData info = current();
		if (info == null) return;
		Platform.show(Platform.serverForm(this, ok -> {
			if (ok) list.save();
			Platform.show(new ServersScreen(parent));
		}, info, false));
	}

	private void direct() {
		ServerData info = new ServerData("Minecraft Server", "", ServerData.Type.OTHER);
		Platform.show(new DirectJoinServerScreen(this, ok -> {
			if (ok) connect(info);
			else Platform.show(this);
		}, info));
	}

	private void connect(ServerData info) {
		ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(info.ip), info, false, null);
	}

	@Override
	void activate(int index) {
		if (index < 0 || index >= list.size()) return;
		connect(list.get(index));
	}

	@Override
	void delete(int index) {
		ServerData info = index >= 0 && index < list.size() ? list.get(index) : null;
		if (info == null) return;
		Platform.show(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				list.remove(info);
				list.save();
				selected = -1;
			}
			Platform.show(this);
		}, Component.literal("Remove \"" + info.name + "\"?"), Component.literal("It will be taken off your server list.")));
	}
}
