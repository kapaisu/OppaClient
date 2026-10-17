package dev.oppaclient.menu;

import dev.oppaclient.Icons;
import dev.oppaclient.OppaClient;
import dev.oppaclient.Paint;
import dev.oppaclient.Pixels;
import dev.oppaclient.Type;
import dev.oppaclient.platform.Gfx;
import dev.oppaclient.platform.Platform;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WorldsScreen extends ListScreen {
	private List<LevelSummary> worlds = new ArrayList<>();
	private final Map<String, Pixels> icons = new HashMap<>();
	private boolean loading = true;

	public WorldsScreen(Screen parent) {
		super(parent, "Singleplayer");
		load();
	}

	private void load() {
		loading = true;
		try {
			var storage = net.minecraft.client.Minecraft.getInstance().getLevelSource();
			storage.loadLevelSummaries(storage.findLevelCandidates()).thenAccept(list -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
				worlds = new ArrayList<>(list);
				worlds.sort((a, b) -> Long.compare(b.getLastPlayed(), a.getLastPlayed()));
				icons.clear();
				for (LevelSummary w : worlds) {
					try {
						var path = w.getIcon();
						if (path != null && Files.isRegularFile(path)) {
							Pixels p = Pixels.read(Files.readAllBytes(path));
							if (p != null) icons.put(w.getLevelId(), p);
						}
					} catch (Exception ignored) {
					}
				}
				loading = false;
				selected = worlds.isEmpty() ? -1 : Math.min(Math.max(selected, 0), worlds.size() - 1);
			}));
		} catch (Exception e) {
			OppaClient.LOG.warn("couldn't list worlds", e);
			loading = false;
		}
	}

	@Override
	int count() {
		return worlds.size();
	}

	@Override
	int rowHeight() {
		return 40;
	}

	@Override
	String empty() {
		return loading ? "Loading worlds" : "No worlds yet. Create one to start.";
	}

	private static String mode(LevelSummary w) {
		if (w.isHardcore()) return "Hardcore";
		return switch (w.getGameMode()) {
			case CREATIVE -> "Creative";
			case ADVENTURE -> "Adventure";
			case SPECTATOR -> "Spectator";
			default -> "Survival";
		};
	}

	@Override
	void row(Gfx g, int index, int x, int y, int w, float alpha) {
		LevelSummary world = worlds.get(index);
		Pixels icon = icons.get(world.getLevelId());
		if (icon != null) {
			Icons.draw(g, "world:" + world.getLevelId() + ":" + world.getLastPlayed(), icon, x, y + 4, 32, alpha);
		} else {
			Paint.round(g, x, y + 4, 32, 32, 6.4f, Paint.ink(0.08f * alpha));
			String letter = world.getLevelName().isEmpty() ? "?" : world.getLevelName().substring(0, 1).toUpperCase();
			Type.STRONG.drawCentered(g, letter, x + 16, y + 16, Paint.ink(0.5f * alpha));
		}
		Type.STRONG.draw(g, world.getLevelName(), x + 42, y + 8, Paint.ink(0.95f * alpha));
		String detail = mode(world) + "   " + ago(world.getLastPlayed());
		Type.SMALL.draw(g, detail, x + 42, y + 21, Paint.ink(0.5f * alpha));
		Type.SMALL.drawRight(g, world.getWorldVersionName().getString(), x + w, y + 8, Paint.ink(0.4f * alpha));
	}

	@Override
	List<Button> buttons() {
		return List.of(
				new Button("Play", Style.PRIMARY, () -> selected >= 0, () -> activate(selected)),
				new Button("New World", Style.SECONDARY, () -> true, () -> Platform.createWorld(this)),
				new Button("Delete", Style.PLAIN, () -> selected >= 0, () -> delete(selected)),
				new Button("Back", Style.PLAIN, () -> true, this::onClose));
	}

	@Override
	void activate(int index) {
		if (index < 0 || index >= worlds.size()) return;
		minecraft.createWorldOpenFlows().openWorld(worlds.get(index).getLevelId(), () -> Platform.show(this));
	}

	@Override
	void delete(int index) {
		if (index < 0 || index >= worlds.size()) return;
		LevelSummary world = worlds.get(index);
		Platform.show(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				try (var session = minecraft.getLevelSource().createAccess(world.getLevelId())) {
					session.deleteLevel();
				} catch (Exception e) {
					OppaClient.LOG.error("couldn't delete {}", world.getLevelId(), e);
				}
				Icons.forget("world:" + world.getLevelId());
				selected = -1;
				load();
			}
			Platform.show(this);
		}, Component.literal("Delete \"" + world.getLevelName() + "\"?"), Component.literal("This world will be gone for good.")));
	}
}
