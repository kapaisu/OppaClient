package dev.oppaclient.overlay;

public final class Colors {
	private Colors() {
	}

	public static float r(int rgb) {
		return ((rgb >> 16) & 0xFF) / 255f;
	}

	public static float g(int rgb) {
		return ((rgb >> 8) & 0xFF) / 255f;
	}

	public static float b(int rgb) {
		return (rgb & 0xFF) / 255f;
	}

	public static int rgb(float r, float g, float b) {
		return (channel(r) << 16) | (channel(g) << 8) | channel(b);
	}

	public static int argb(float a, int rgb) {
		return (channel(a) << 24) | (rgb & 0xFFFFFF);
	}

	public static int argb(int a, int rgb) {
		return (Math.clamp(a, 0, 255) << 24) | (rgb & 0xFFFFFF);
	}

	public static int channel(float v) {
		return Math.clamp(Math.round(v * 255f), 0, 255);
	}

	public static int mix(int a, int b, float t) {
		return rgb(mixChannel(r(a), r(b), t), mixChannel(g(a), g(b), t), mixChannel(b(a), b(b), t));
	}

	private static float mixChannel(float a, float b, float t) {
		float la = a * a;
		float lb = b * b;
		return (float) Math.sqrt(la + (lb - la) * t);
	}

	public static int hsv(float h, float s, float v) {
		h = h - (float) Math.floor(h);
		float c = v * s;
		float x = c * (1f - Math.abs((h * 6f) % 2f - 1f));
		float m = v - c;
		float r;
		float g;
		float b;
		switch ((int) (h * 6f)) {
			case 0 -> { r = c; g = x; b = 0; }
			case 1 -> { r = x; g = c; b = 0; }
			case 2 -> { r = 0; g = c; b = x; }
			case 3 -> { r = 0; g = x; b = c; }
			case 4 -> { r = x; g = 0; b = c; }
			default -> { r = c; g = 0; b = x; }
		}
		return rgb(r + m, g + m, b + m);
	}

	public static float[] toHsv(int rgb) {
		float r = r(rgb);
		float g = g(rgb);
		float b = b(rgb);
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float d = max - min;
		float h;
		if (d == 0f) {
			h = 0f;
		} else if (max == r) {
			h = ((g - b) / d) / 6f;
		} else if (max == g) {
			h = ((b - r) / d + 2f) / 6f;
		} else {
			h = ((r - g) / d + 4f) / 6f;
		}
		if (h < 0f) {
			h += 1f;
		}
		return new float[]{h, max == 0f ? 0f : d / max, max};
	}

	public static int asLight(int rgb) {
		float[] hsv = toHsv(rgb);
		if (hsv[1] < 0.08f) {
			return hsv(0f, 0f, 0.95f);
		}
		return hsv(hsv[0], Math.max(hsv[1], 0.62f), 1f);
	}
}
