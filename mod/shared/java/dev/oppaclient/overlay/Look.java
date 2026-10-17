package dev.oppaclient.overlay;

public final class Look {
	public static final int MAX_ROWS = 8;

	public final float[] rows = new float[MAX_ROWS];
	public final float[] light = new float[MAX_ROWS];
	public final float[] heat = new float[MAX_ROWS];
	public int rowCount;

	public float tube;
	public float halo;
	public float output;

	public ColorMode mode = ColorMode.SOLID;
	public int primary;
	public int secondary;
	public int core;
	public float flowSpeed;
	public float flowScale;
	public double time;

	private float lightAtCenter = 1f;

	public Look shape(float tube, float halo, float haloStrength, float falloff, float coreHeat, float output) {
		return shape(tube, halo, haloStrength, falloff, coreHeat, output, false);
	}

	public Look shape(float tube, float halo, float haloStrength, float falloff, float coreHeat, float output, boolean lite) {
		this.tube = Math.max(tube, 1e-5f);
		this.halo = Math.max(halo, this.tube * 1.8f);
		this.output = output;

		float t = this.tube;
		float w = this.halo;
		float span = w - t;
		float[] offsets = lite
				? new float[]{0f, t, t + span * 0.3f, w}
				: new float[]{0f, t * 0.55f, t, t * 1.6f, t + span * 0.16f, t + span * 0.36f, t + span * 0.62f, w};
		rowCount = 0;
		float last = -1f;
		for (float offset : offsets) {
			if (offset <= last + 1e-6f) {
				continue;
			}
			rows[rowCount] = offset;
			light[rowCount] = glass(offset) + haloStrength * scatter(offset, falloff);
			heat[rowCount] = coreHeat * (float) Math.exp(-sq(offset / (t * 0.6f)));
			last = offset;
			rowCount++;
		}
		light[rowCount - 1] = 0f;
		heat[rowCount - 1] = 0f;
		lightAtCenter = light[0];
		return this;
	}

	private float glass(float r) {
		float x = r / tube;
		float x2 = x * x;
		return 1f / (1f + x2 * x2 * x2 * x2);
	}

	private float scatter(float r, float falloff) {
		float x = r / halo;
		if (x >= 1f) {
			return 0f;
		}
		float window = 1f - x * x;
		return (float) Math.exp(-falloff * x * x) * window * window;
	}

	public float profile(float offset) {
		if (offset <= 0f) {
			return 1f;
		}
		for (int i = 1; i < rowCount; i++) {
			if (offset <= rows[i]) {
				float f = (offset - rows[i - 1]) / (rows[i] - rows[i - 1]);
				return (light[i - 1] + (light[i] - light[i - 1]) * f) / lightAtCenter;
			}
		}
		return 0f;
	}

	public Look colors(OverlayConfig config, double time) {
		this.mode = config.colorMode;
		this.time = time;
		this.flowSpeed = config.flowSpeed;
		this.flowScale = config.flowScale;
		this.primary = config.primary;
		this.secondary = config.secondary;
		if (mode == ColorMode.GAS) {
			Gas gas = config.gas;
			this.primary = gas.tube;
			this.core = gas.core;
		} else {
			this.core = Colors.mix(primary, 0xFFFFFF, 0.82f);
		}
		return this;
	}

	public Look solid(int rgb) {
		this.mode = ColorMode.SOLID;
		this.primary = rgb;
		this.core = Colors.mix(rgb, 0xFFFFFF, 0.82f);
		return this;
	}

	public boolean flows() {
		return mode == ColorMode.GRADIENT || mode == ColorMode.SPECTRUM;
	}

	public int tubeColor(double x, double y, double z) {
		return switch (mode) {
			case GRADIENT -> {
				double phase = (x + y + z) * flowScale / 3.0 - time * flowSpeed;
				float t = (float) (0.5 - 0.5 * Math.cos(phase * Math.PI * 2.0));
				yield Colors.mix(primary, secondary, t);
			}
			case SPECTRUM -> {
				double phase = (x + y + z) * flowScale / 3.0 - time * flowSpeed;
				yield Colors.hsv((float) (phase - Math.floor(phase)), 0.82f, 1f);
			}
			default -> primary;
		};
	}

	public int coreColor(int tubeColor) {
		return mode == ColorMode.SOLID || mode == ColorMode.GAS || mode == ColorMode.BLOCK
				? core
				: Colors.mix(tubeColor, 0xFFFFFF, 0.82f);
	}

	private static float sq(float v) {
		return v * v;
	}
}
