package dev.oppaclient.overlay;

public final class Noise {
	private Noise() {
	}

	public static float hash(long seed, long n) {
		long x = seed * 0x9E3779B97F4A7C15L + n * 0xC2B2AE3D27D4EB4FL;
		x ^= x >>> 31;
		x *= 0xBF58476D1CE4E5B9L;
		x ^= x >>> 29;
		x *= 0x94D049BB133111EBL;
		x ^= x >>> 32;
		return (x >>> 40) / (float) (1L << 24);
	}

	public static float smooth(long seed, double t) {
		long i = (long) Math.floor(t);
		float f = (float) (t - i);
		float u = f * f * (3f - 2f * f);
		float a = hash(seed, i);
		float b = hash(seed, i + 1);
		return a + (b - a) * u;
	}
}
