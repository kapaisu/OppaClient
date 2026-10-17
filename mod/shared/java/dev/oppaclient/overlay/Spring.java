package dev.oppaclient.overlay;

public final class Spring {
	private static final float MAX_STEP = 1f / 480f;

	public float value;
	public float velocity;
	public float target;

	public Spring() {
	}

	public Spring(float value) {
		this.value = value;
		this.target = value;
	}

	public void snap(float to) {
		value = to;
		target = to;
		velocity = 0f;
	}

	public void step(float dt, float frequency, float damping) {
		if (dt <= 0f) {
			return;
		}
		float omega = (float) (Math.PI * 2.0) * frequency;
		float k = omega * omega;
		float c = 2f * damping * omega;
		int steps = Math.max(1, (int) Math.ceil(dt / MAX_STEP));
		float h = dt / steps;
		for (int i = 0; i < steps; i++) {
			float accel = -k * (value - target) - c * velocity;
			velocity += accel * h;
			value += velocity * h;
		}
	}

	public boolean settled(float epsilon) {
		return Math.abs(value - target) < epsilon && Math.abs(velocity) < epsilon * 10f;
	}
}
