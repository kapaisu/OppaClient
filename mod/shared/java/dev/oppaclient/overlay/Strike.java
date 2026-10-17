package dev.oppaclient.overlay;

public enum Strike implements Labeled {
	OFF("Off"),
	ON_ACQUIRE("First"),
	ALWAYS("Every");

	private final String label;

	Strike(String label) {
		this.label = label;
	}

	@Override
	public String label() {
		return label;
	}
}
