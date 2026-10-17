package dev.oppaclient.overlay;

public enum Motion implements Labeled {
	SNAP("Snap"),
	GLIDE("Glide"),
	SPRING("Spring");

	private final String label;

	Motion(String label) {
		this.label = label;
	}

	@Override
	public String label() {
		return label;
	}
}
