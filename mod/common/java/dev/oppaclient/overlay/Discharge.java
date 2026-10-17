package dev.oppaclient.overlay;

public final class Discharge {
	private static final int STRIKE_SLOTS = 9;

	private final long seed;
	private double struckAt = Double.NaN;
	private float strikeTime;

	public Discharge(long seed) {
		this.seed = seed;
	}

	public void strike(double now, float duration) {
		struckAt = now;
		strikeTime = Math.max(duration, 0.01f);
	}

	public boolean striking(double now) {
		return !Double.isNaN(struckAt) && now - struckAt < strikeTime;
	}

	public float level(double now, OverlayConfig config) {
		float level = 1f;

		if (!Double.isNaN(struckAt)) {
			double age = now - struckAt;
			if (age < strikeTime) {
				float progress = (float) (age / strikeTime);
				int slot = (int) (progress * STRIKE_SLOTS);
				float chance = 0.18f + 0.8f * progress;
				boolean lit = slot == STRIKE_SLOTS - 1 || Noise.hash(seed, slot) < chance;
				level = lit ? 0.35f + 0.65f * Noise.hash(seed + 7, slot) : 0.03f;
			} else {
				float settle = (float) ((age - strikeTime) / 0.11);
				level = 1f + 0.28f * (float) Math.exp(-settle);
			}
		}

		float flicker = config.flicker;
		if (flicker > 0f) {
			float wander = Noise.smooth(seed + 31, now * 11.0) * 0.6f + Noise.smooth(seed + 57, now * 29.0) * 0.4f;
			level *= 1f - flicker * 0.32f * wander;
			long slot = (long) Math.floor(now / 0.06);
			if (Noise.hash(seed + 101, slot) < flicker * flicker * 0.12f) {
				level *= 0.2f + 0.45f * Noise.hash(seed + 211, slot);
			}
		}

		float breathe = config.breathe;
		if (breathe > 0f) {
			double phase = now * config.breatheRate * Math.PI * 2.0 + (seed & 0xFF) * 0.0245;
			level *= 1f - breathe * 0.55f * (float) (0.5 - 0.5 * Math.cos(phase));
		}
		return level;
	}
}
