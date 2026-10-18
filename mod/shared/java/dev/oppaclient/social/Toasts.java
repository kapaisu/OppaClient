package dev.oppaclient.social;

import java.util.ArrayList;
import java.util.List;

public final class Toasts {
	public enum Kind { INFO, INVITE, REQUEST, SHOT }

	public record Toast(String title, String detail, Kind kind, long born) {}

	private static final List<Toast> live = new ArrayList<>();
	private static final long LIFE = 7000;

	private Toasts() {}

	public static synchronized void show(String title, String detail, Kind kind) {
		live.removeIf(t -> t.title().equals(title));
		live.add(new Toast(title, detail == null ? "" : detail, kind, System.currentTimeMillis()));
		if (live.size() > 4) live.removeFirst();
	}

	public static synchronized void dismiss(String title) {
		live.removeIf(t -> t.title().equals(title));
	}

	public static synchronized List<Toast> current() {
		long now = System.currentTimeMillis();
		live.removeIf(t -> now - t.born() > LIFE + 400);
		return List.copyOf(live);
	}

	public static synchronized boolean has(Kind kind) {
		long now = System.currentTimeMillis();
		return live.stream().anyMatch(t -> t.kind() == kind && now - t.born() < LIFE);
	}

	public static float fade(Toast t) {
		long age = System.currentTimeMillis() - t.born();
		if (age < 220) return age / 220f;
		if (age > LIFE) return Math.max(0, 1 - (age - LIFE) / 400f);
		return 1;
	}
}
