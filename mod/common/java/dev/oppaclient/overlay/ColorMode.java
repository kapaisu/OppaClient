package dev.oppaclient.overlay;

public enum ColorMode implements Labeled {
	SOLID("Solid"),
	GRADIENT("Gradient"),
	SPECTRUM("Spectrum"),
	GAS("Gas"),
	BLOCK("Block");

	private final String label;

	ColorMode(String label) {
		this.label = label;
	}

	@Override
	public String label() {
		return label;
	}
}
