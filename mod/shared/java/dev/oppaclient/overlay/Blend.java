package dev.oppaclient.overlay;

public enum Blend implements Labeled {
	ADDITIVE("Light"),
	ALPHA("Paint");

	private final String label;

	Blend(String label) {
		this.label = label;
	}

	@Override
	public String label() {
		return label;
	}
}
