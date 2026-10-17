package dev.oppaclient.overlay;

public enum Gas implements Labeled {
	NEON("Neon", 0xFF3A1A, 0xFFC08A),
	ARGON("Argon", 0x3B7CFF, 0xC9DDFF),
	HELIUM("Helium", 0xFFAE8C, 0xFFF0E4),
	KRYPTON("Krypton", 0xCFC4FF, 0xFFFFFF),
	XENON("Xenon", 0x7A6BFF, 0xDCD6FF),
	HYDROGEN("Hydrogen", 0xE04BFF, 0xFFC9F4),
	MERCURY("Mercury", 0x35E0D0, 0xD8FFF9),
	SODIUM("Sodium", 0xFFA51F, 0xFFE7B0);

	private final String label;
	public final int tube;
	public final int core;

	Gas(String label, int tube, int core) {
		this.label = label;
		this.tube = tube;
		this.core = core;
	}

	@Override
	public String label() {
		return label;
	}
}
