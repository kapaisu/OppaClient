package dev.oppaclient.hud;

import dev.oppaclient.Type;
import dev.oppaclient.platform.Gfx;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class Readouts {
	private Readouts() {}

	private abstract static class Line extends Element {
		Line(String id, String label, boolean on, float ax, float ay, int ox, int oy) {
			super(id, label, on, ax, ay, ox, oy);
		}

		abstract String value(Minecraft mc);

		abstract String unit();

		@Override
		public int width(Minecraft mc, boolean preview) {
			String u = unit();
			return Type.STRONG.width(value(mc)) + (u.isEmpty() ? 0 : 3 + Type.UI.width(u));
		}

		@Override
		public int height(Minecraft mc, boolean preview) {
			return 9;
		}

		@Override
		public void draw(Gfx ctx, Minecraft mc, boolean preview) {
			String v = value(mc);
			text(ctx, Type.STRONG, v, 0, 0, 0.95f);
			if (!unit().isEmpty()) {
				text(ctx, Type.UI, unit(), Type.STRONG.width(v) + 3, 0, 0.55f);
			}
		}
	}

	static final class Fps extends Line {
		Fps() {
			super("fps", "FPS", true, 0, 0, 6, 6);
		}

		@Override
		public List<Option> options() {
			return List.of(new Toggle("unit", "Show unit", true));
		}

		String value(Minecraft mc) {
			return Integer.toString(mc.getFps());
		}

		String unit() {
			return on("unit") ? "fps" : "";
		}
	}

	static final class Cps extends Line {
		Cps() {
			super("cps", "CPS", false, 0, 0, 6, 48);
		}

		@Override
		public List<Option> options() {
			return List.of(new Toggle("right", "Right clicks", true));
		}

		String value(Minecraft mc) {
			return on("right") ? Clicks.left() + "  " + Clicks.right() : Integer.toString(Clicks.left());
		}

		String unit() {
			return "cps";
		}
	}

	static final class Ping extends Line {
		Ping() {
			super("ping", "Ping", true, 1, 0, -6, 20);
		}

		@Override
		public boolean idle(Minecraft mc) {
			return entry(mc) == null || mc.isLocalServer();
		}

		private static PlayerInfo entry(Minecraft mc) {
			if (mc.player == null || mc.getConnection() == null) return null;
			return mc.getConnection().getPlayerInfo(mc.player.getUUID());
		}

		String value(Minecraft mc) {
			PlayerInfo e = entry(mc);
			return e == null || mc.isLocalServer() ? "0" : Integer.toString(e.getLatency());
		}

		String unit() {
			return "ms";
		}
	}

	static final class Clock extends Line {
		private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm");
		private static final DateTimeFormatter H24S = DateTimeFormatter.ofPattern("HH:mm:ss");
		private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm");
		private static final DateTimeFormatter H12S = DateTimeFormatter.ofPattern("h:mm:ss");
		private static final DateTimeFormatter AMPM = DateTimeFormatter.ofPattern("a");

		Clock() {
			super("clock", "Clock", true, 1, 0, -6, 6);
		}

		@Override
		public List<Option> options() {
			return List.of(new Toggle("24h", "24-hour", true), new Toggle("seconds", "Seconds", false));
		}

		String value(Minecraft mc) {
			boolean s = on("seconds");
			return LocalTime.now().format(on("24h") ? (s ? H24S : H24) : (s ? H12S : H12));
		}

		String unit() {
			return on("24h") ? "" : LocalTime.now().format(AMPM).toLowerCase();
		}
	}

	static final class Speed extends Line {
		Speed() {
			super("speed", "Speed", false, 0, 1, 6, -82);
		}

		@Override
		public List<Option> options() {
			return List.of(new Choice("unit", "Unit", List.of("b/s", "km/h")), new Toggle("vertical", "Count falling", false));
		}

		String value(Minecraft mc) {
			if (mc.player == null) return "0.0";
			Vec3 v = mc.player.getDeltaMovement();
			double perTick = on("vertical") ? v.length() : Math.hypot(v.x, v.z);
			double bps = perTick * 20;
			return String.format("%.1f", pick("unit").equals("km/h") ? bps * 3.6 : bps);
		}

		String unit() {
			return pick("unit");
		}
	}

	static final class Memory extends Line {
		Memory() {
			super("memory", "Memory", false, 1, 0, -6, 34);
		}

		@Override
		public List<Option> options() {
			return List.of(new Choice("show", "Show", List.of("Percent", "Amount")));
		}

		String value(Minecraft mc) {
			Runtime rt = Runtime.getRuntime();
			long used = rt.totalMemory() - rt.freeMemory();
			if (pick("show").equals("Percent")) return used * 100 / rt.maxMemory() + "%";
			return String.format("%.1f", used / 1073741824.0);
		}

		String unit() {
			if (pick("show").equals("Percent")) return "memory";
			return String.format("/ %.1f GB", Runtime.getRuntime().maxMemory() / 1073741824.0);
		}
	}

	static final class Server extends Line {
		Server() {
			super("server", "Server", false, 0.5f, 0, 0, 6);
		}

		@Override
		public boolean idle(Minecraft mc) {
			return mc.getCurrentServer() == null;
		}

		String value(Minecraft mc) {
			ServerData s = mc.getCurrentServer();
			return s == null ? "play.example.net" : s.ip;
		}

		String unit() {
			return "";
		}
	}

	static final class Biome extends Line {
		Biome() {
			super("biome", "Biome", false, 0, 0, 6, 62);
		}

		String value(Minecraft mc) {
			if (mc.player == null || mc.level == null) return "Plains";
			return mc.level.getBiome(mc.player.blockPosition()).unwrapKey()
					.map(k -> {
						Identifier id = k.identifier();
						return Component.translatable("biome." + id.getNamespace() + "." + id.getPath()).getString();
					})
					.orElse("Unknown");
		}

		String unit() {
			return "";
		}
	}

	static final class Day extends Line {
		Day() {
			super("day", "Day", false, 0, 0, 6, 76);
		}

		@Override
		public List<Option> options() {
			return List.of(new Toggle("time", "Time of day", true));
		}

		String value(Minecraft mc) {
			long t = mc.level == null ? 6000 : mc.level.getOverworldClockTime();
			return Long.toString(t / 24000 + 1);
		}

		String unit() {
			if (!on("time")) return "";
			long t = Minecraft.getInstance().level == null ? 6000 : Minecraft.getInstance().level.getOverworldClockTime();
			long minutes = ((t + 6000) % 24000) * 1440 / 24000;
			return String.format("%02d:%02d", minutes / 60, minutes % 60);
		}

		@Override
		public void draw(Gfx ctx, Minecraft mc, boolean preview) {
			text(ctx, Type.UI, "Day", 0, 0, 0.55f);
			int x = Type.UI.width("Day") + 3;
			String v = value(mc);
			text(ctx, Type.STRONG, v, x, 0, 0.95f);
			if (!unit().isEmpty()) text(ctx, Type.UI, unit(), x + Type.STRONG.width(v) + 6, 0, 0.55f);
		}

		@Override
		public int width(Minecraft mc, boolean preview) {
			int w = Type.UI.width("Day") + 3 + Type.STRONG.width(value(mc));
			return unit().isEmpty() ? w : w + 6 + Type.UI.width(unit());
		}
	}

	static final class Coords extends Element {
		Coords() {
			super("coords", "Coordinates", false, 0, 0, 6, 20);
		}

		@Override
		public List<Option> options() {
			return List.of(new Toggle("facing", "Direction", true), new Toggle("other", "Other dimension", false));
		}

		private static int[] pos(Minecraft mc) {
			if (mc.player == null) return new int[] {0, 64, 0};
			return new int[] {mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()};
		}

		private static String facing(Minecraft mc) {
			if (mc.player == null) return "North";
			String d = mc.player.getDirection().getSerializedName();
			return Character.toUpperCase(d.charAt(0)) + d.substring(1);
		}

		private static String other(Minecraft mc) {
			int[] p = pos(mc);
			if (mc.level == null || mc.level.dimension() == Level.OVERWORLD) {
				return "Nether  " + Math.floorDiv(p[0], 8) + "  " + Math.floorDiv(p[2], 8);
			}
			if (mc.level.dimension() == Level.NETHER) {
				return "Overworld  " + p[0] * 8 + "  " + p[2] * 8;
			}
			return null;
		}

		private String footer;
		private long footerFrame = -1;

		private String footer(Minecraft mc) {
			if (footerFrame == Hud.frame() && footer != null) return footer;
			footerFrame = Hud.frame();
			footer = buildFooter(mc);
			return footer;
		}

		private String buildFooter(Minecraft mc) {
			StringBuilder b = new StringBuilder();
			if (on("facing")) b.append(facing(mc));
			String o = on("other") ? other(mc) : null;
			if (o != null) b.append(b.isEmpty() ? "" : "    ").append(o);
			return b.toString();
		}

		@Override
		public int width(Minecraft mc, boolean preview) {
			int[] p = pos(mc);
			int w = 0;
			for (int i = 0; i < 3; i++) {
				w += Type.UI.width("XYZ".substring(i, i + 1)) + 3 + Type.STRONG.width(Integer.toString(p[i])) + (i < 2 ? 8 : 0);
			}
			return Math.max(w, Type.SMALL.width(footer(mc)));
		}

		@Override
		public int height(Minecraft mc, boolean preview) {
			return footer(mc).isEmpty() ? 9 : 9 + 3 + 8;
		}

		@Override
		public void draw(Gfx ctx, Minecraft mc, boolean preview) {
			int[] p = pos(mc);
			int x = 0;
			for (int i = 0; i < 3; i++) {
				String axis = "XYZ".substring(i, i + 1);
				text(ctx, Type.UI, axis, x, 0, 0.5f);
				x += Type.UI.width(axis) + 3;
				String n = Integer.toString(p[i]);
				text(ctx, Type.STRONG, n, x, 0, 0.95f);
				x += Type.STRONG.width(n) + 8;
			}
			String f = footer(mc);
			if (!f.isEmpty()) text(ctx, Type.SMALL, f, 0, 12, 0.55f);
		}
	}

	static final class Reach extends Line {
		Reach() {
			super("reach", "Reach", false, 0, 0, 6, 90);
		}

		@Override
		public boolean idle(Minecraft mc) {
			return !Combat.recentReach();
		}

		String value(Minecraft mc) {
			return String.format("%.2f", Combat.recentReach() ? Combat.reach() : 3.0);
		}

		String unit() {
			return "blocks";
		}
	}

	static final class Combo extends Line {
		Combo() {
			super("combo", "Combo", false, 0, 0, 6, 104);
		}

		@Override
		public boolean idle(Minecraft mc) {
			return Combat.combo() < 2;
		}

		String value(Minecraft mc) {
			return Integer.toString(Math.max(2, Combat.combo()));
		}

		String unit() {
			return "combo";
		}
	}

	static final class Items extends Element {
		private static final List<String> KINDS = List.of("Totems", "Arrows", "Pearls", "Golden apples");
		private long frame = -1;
		private int count;

		Items() {
			super("items", "Item counter", false, 1, 1, -6, -40);
		}

		@Override
		public List<Option> options() {
			return List.of(new Choice("kind", "Count", KINDS));
		}

		private net.minecraft.world.item.Item[] kinds() {
			return switch (pick("kind")) {
				case "Arrows" -> new net.minecraft.world.item.Item[] {net.minecraft.world.item.Items.ARROW, net.minecraft.world.item.Items.SPECTRAL_ARROW, net.minecraft.world.item.Items.TIPPED_ARROW};
				case "Pearls" -> new net.minecraft.world.item.Item[] {net.minecraft.world.item.Items.ENDER_PEARL};
				case "Golden apples" -> new net.minecraft.world.item.Item[] {net.minecraft.world.item.Items.GOLDEN_APPLE, net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE};
				default -> new net.minecraft.world.item.Item[] {net.minecraft.world.item.Items.TOTEM_OF_UNDYING};
			};
		}

		private int count(Minecraft mc) {
			if (frame == Hud.frame()) return count;
			frame = Hud.frame();
			count = 0;
			if (mc.player == null) return count;
			net.minecraft.world.item.Item[] kinds = kinds();
			net.minecraft.world.entity.player.Inventory inv = mc.player.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				net.minecraft.world.item.ItemStack s = inv.getItem(i);
				for (net.minecraft.world.item.Item k : kinds) if (s.is(k)) count += s.getCount();
			}
			return count;
		}

		@Override
		public int width(Minecraft mc, boolean preview) {
			return 20 + Type.STRONG.width(Integer.toString(preview && count(mc) == 0 ? 2 : count(mc)));
		}

		@Override
		public int height(Minecraft mc, boolean preview) {
			return 16;
		}

		@Override
		public void draw(Gfx ctx, Minecraft mc, boolean preview) {
			int n = preview && count(mc) == 0 ? 2 : count(mc);
			ctx.item(new net.minecraft.world.item.ItemStack(kinds()[0]), 0, 0);
			text(ctx, Type.STRONG, Integer.toString(n), 20, 4, n == 0 ? 0.5f : 0.95f);
		}
	}
}
