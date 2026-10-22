package dev.oppaclient.perf;

import dev.oppaclient.Config;

import java.util.List;

public final class Limits {
	public static final List<String> LEVELS = List.of("All", "Reduced", "Minimal");
	public static final int DROP = 1, DECOR = 2;

	private static int spawned, hits;

	private Limits() {}

	public static void tick() {
		spawned = 0;
	}

	public static boolean particle(boolean hit, double distSq) {
		String level = Config.get().particles;
		if (!level.equals("Reduced") && !level.equals("Minimal")) return true;
		boolean min = level.equals("Minimal");
		double reach = min ? 16 : 40;
		if (distSq > reach * reach) return false;
		if (hit && ++hits % (min ? 4 : 2) != 0) return false;
		if (spawned >= (min ? 60 : 250)) return false;
		spawned++;
		return true;
	}

	public static boolean hide(int kind, double distSq) {
		String level = Config.get().entities;
		if (kind == 0 || !level.equals("Reduced") && !level.equals("Minimal")) return false;
		boolean min = level.equals("Minimal");
		double reach = kind == DROP ? (min ? 16 : 32) : (min ? 24 : 48);
		return distSq > reach * reach;
	}
}
