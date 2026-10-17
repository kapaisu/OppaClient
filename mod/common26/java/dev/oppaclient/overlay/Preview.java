package dev.oppaclient.overlay;

import dev.oppaclient.platform.Gfx;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

public final class Preview {
	public record Shape(String name, EdgeSet edges) {
	}

	public static final Shape[] SHAPES = {
			new Shape("Block", EdgeSet.CUBE),
			new Shape("Slab", EdgeSet.of(Block.box(0, 0, 0, 16, 8, 16))),
			new Shape("Post", EdgeSet.of(Block.box(6, 0, 6, 10, 16, 10))),
			new Shape("Plate", EdgeSet.of(Block.box(1, 0, 1, 15, 1, 15)))
	};

	private static final float PITCH = (float) Math.toRadians(27);
	private static final float EYE_DISTANCE = 3.1f;

	private final NeonMesher mesher = new NeonMesher();
	private final Look look = new Look();
	private final Discharge discharge = new Discharge(0x9E3779B9L);
	private final Spring[] box = new Spring[6];
	private int shape;
	private int previousShape = -1;
	private float crossfade = 1f;

	private float yaw = (float) Math.toRadians(35);
	private float spin = 0.32f;
	private boolean dragging;

	public Preview() {
		AABB b = SHAPES[0].edges().bounds;
		double[] values = {b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ};
		for (int i = 0; i < 6; i++) {
			box[i] = new Spring((float) values[i]);
		}
	}

	public int shape() {
		return shape;
	}

	public void select(int index, double now, OverlayConfig config) {
		if (index == shape) {
			return;
		}
		previousShape = shape;
		shape = index;
		crossfade = 0f;
		AABB b = SHAPES[index].edges().bounds;
		double[] values = {b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ};
		for (int i = 0; i < 6; i++) {
			if (config.motion == Motion.SNAP) {
				box[i].snap((float) values[i]);
			} else {
				box[i].target = (float) values[i];
			}
		}
		if (config.strike == Strike.ALWAYS) {
			discharge.strike(now, config.strikeTime);
		}
	}

	public void strike(double now, OverlayConfig config) {
		discharge.strike(now, Math.max(config.strikeTime, 0.2f));
	}

	public void drag(double dx) {
		yaw += (float) dx * 0.012f;
		spin = (float) dx * 0.6f;
		dragging = true;
	}

	public void release() {
		dragging = false;
	}

	public void draw(Gfx context, OverlayConfig config, int x, int y, int w, int h,
					 double now, float dt, float fade) {
		float frequency = config.motion == Motion.SNAP ? 30f : config.response;
		float damping = config.motion == Motion.SPRING ? 1f - config.bounce : 1f;
		for (Spring spring : box) {
			spring.step(dt, frequency, damping);
		}
		crossfade = Math.min(1f, crossfade + dt / 0.12f);
		if (!dragging) {
			spin += (0.32f - spin) * Math.min(1f, dt * 2.5f);
		}
		yaw += spin * dt;

		float cx = x + w * 0.5f;
		float cy = y + h * 0.53f;
		float focal = Math.min(w, h) * 1.45f;
		AABB display = new AABB(box[0].value, box[1].value, box[2].value, box[3].value, box[4].value, box[5].value);

		double ex = Math.sin(yaw) * Math.cos(PITCH) * EYE_DISTANCE + 0.5;
		double ey = Math.sin(PITCH) * EYE_DISTANCE + 0.42;
		double ez = Math.cos(yaw) * Math.cos(PITCH) * EYE_DISTANCE + 0.5;
		Projector projector = new Projector(ex, ey, ez, 0.5, 0.42, 0.5, cx, cy, focal);

		boolean additive = config.blend == Blend.ADDITIVE;
		Canvas solid = Canvas.flat().alpha(fade);
		Canvas light = Canvas.of(additive).alpha(fade);

		floor(light, projector, config, display, now);
		faces(solid, projector, display);

		float level = discharge.level(now, config) * (config.enabled ? 1f : 0.15f);
		shapeLook(config, now);
		NeonMesher.QuadSink sink = projector.sink(light);

		drawShape(SHAPES[shape].edges(), display, ex, ey, ez, level * crossfade, config, sink);
		if (previousShape >= 0 && crossfade < 1f) {
			drawShape(SHAPES[previousShape].edges(), display, ex, ey, ez, level * (1f - crossfade), config, sink);
		}

		context.canvas(solid, x, y, w, h);
		context.canvas(light, x, y, w, h);
	}

	private void shapeLook(OverlayConfig config, double now) {
		look.shape(config.thickness, config.haloRadius, config.haloStrength, config.falloff, config.coreHeat, config.brightness);
		look.colors(config, now);
		if (config.colorMode == ColorMode.BLOCK) {
			look.solid(0x8FD16A);
		}
	}

	private void drawShape(EdgeSet edges, AABB display, double ex, double ey, double ez, float envelope,
						   OverlayConfig config, NeonMesher.QuadSink sink) {
		if (envelope <= 0.002f) {
			return;
		}
		NeonMesher.Placement at = new NeonMesher.Placement();
		at.eyeX = ex;
		at.eyeY = ey;
		at.eyeZ = ez;
		at.envelope = envelope;
		at.inflate = config.inflate;
		at.width = 1f;
		at.pull = 0f;
		at.map(edges.bounds, display.minX, display.minY, display.minZ, display.maxX, display.maxY, display.maxZ);
		mesher.fill(edges, look, at, config.fill, sink);
		mesher.spill(edges, look, at, config.spill, config.spillReach, sink);
		if (config.throughWalls > 0f) {
			at.envelope = envelope * config.throughWalls;
			mesher.tubes(edges, look, at, NeonMesher.Edges.BACK, sink);
			at.envelope = envelope;
		}
		mesher.tubes(edges, look, at, NeonMesher.Edges.FRONT, sink);
	}

	private void faces(Canvas canvas, Projector p, AABB b) {
		float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
		float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
		int top = 0xFF1C1E23;
		int light = 0xFF15171B;
		int dark = 0xFF101114;
		if (p.ey > y1) {
			p.face(canvas, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, top);
		}
		if (p.ez > z1) {
			p.face(canvas, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, light);
		}
		if (p.ez < z0) {
			p.face(canvas, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, dark);
		}
		if (p.ex > x1) {
			p.face(canvas, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, dark);
		}
		if (p.ex < x0) {
			p.face(canvas, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, light);
		}
	}

	private void floor(Canvas light, Projector p, OverlayConfig config, AABB b, double now) {
		if (config.spill <= 0f) {
			return;
		}
		int color = config.colorMode == ColorMode.GAS ? config.gas.tube : config.primary;
		if (config.colorMode == ColorMode.SPECTRUM) {
			color = Colors.hsv((float) (-now * config.flowSpeed), 0.8f, 1f);
		}
		float strength = Math.min(1f, config.spill * config.brightness * 0.55f) * (config.enabled ? 1f : 0.15f);
		float mx = (float) ((b.minX + b.maxX) * 0.5);
		float mz = (float) ((b.minZ + b.maxZ) * 0.5);
		float rx = (float) (b.maxX - b.minX) * 0.5f + 0.45f;
		float rz = (float) (b.maxZ - b.minZ) * 0.5f + 0.45f;
		int rings = 6;
		int segments = 36;
		for (int ring = 0; ring < rings; ring++) {
			float t0 = (float) ring / rings;
			float t1 = (float) (ring + 1) / rings;
			int c0 = Colors.argb(strength * falloff(t0), color);
			int c1 = Colors.argb(strength * falloff(t1), color);
			for (int s = 0; s < segments; s++) {
				double a0 = Math.PI * 2 * s / segments;
				double a1 = Math.PI * 2 * (s + 1) / segments;
				float[] p00 = p.project(mx + (float) Math.cos(a0) * rx * t0, 0f, mz + (float) Math.sin(a0) * rz * t0);
				float[] p01 = p.project(mx + (float) Math.cos(a1) * rx * t0, 0f, mz + (float) Math.sin(a1) * rz * t0);
				float[] p11 = p.project(mx + (float) Math.cos(a1) * rx * t1, 0f, mz + (float) Math.sin(a1) * rz * t1);
				float[] p10 = p.project(mx + (float) Math.cos(a0) * rx * t1, 0f, mz + (float) Math.sin(a0) * rz * t1);
				if (p00 == null || p01 == null || p11 == null || p10 == null) {
					continue;
				}
				light.quad(p00[0], p00[1], c0, p01[0], p01[1], c0, p11[0], p11[1], c1, p10[0], p10[1], c1);
			}
		}
	}

	private static float falloff(float t) {
		float w = 1f - t * t;
		return w * w * (float) Math.exp(-2.2f * t * t);
	}

	static final class Projector {
		final double ex, ey, ez;
		private final float rx, ry, rz, ux, uy, uz, fx, fy, fz;
		private final float cx, cy, focal;

		Projector(double ex, double ey, double ez, double tx, double ty, double tz, float cx, float cy, float focal) {
			this.ex = ex;
			this.ey = ey;
			this.ez = ez;
			float fx = (float) (tx - ex), fy = (float) (ty - ey), fz = (float) (tz - ez);
			float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
			this.fx = fx / fl;
			this.fy = fy / fl;
			this.fz = fz / fl;
			float rx = -this.fz, rz = this.fx;
			float rl = (float) Math.sqrt(rx * rx + rz * rz);
			this.rx = rx / rl;
			this.ry = 0f;
			this.rz = rz / rl;
			this.ux = this.ry * this.fz - this.rz * this.fy;
			this.uy = this.rz * this.fx - this.rx * this.fz;
			this.uz = this.rx * this.fy - this.ry * this.fx;
			this.cx = cx;
			this.cy = cy;
			this.focal = focal;
		}

		float[] projectRelative(float x, float y, float z) {
			float depth = x * fx + y * fy + z * fz;
			if (depth < 0.05f) {
				return null;
			}
			float sx = x * rx + y * ry + z * rz;
			float sy = x * ux + y * uy + z * uz;
			return new float[]{cx + focal * sx / depth, cy - focal * sy / depth};
		}

		float[] project(float x, float y, float z) {
			return projectRelative((float) (x - ex), (float) (y - ey), (float) (z - ez));
		}

		void face(Canvas canvas, float ax, float ay, float az, float bx, float by, float bz,
				  float cx, float cy, float cz, float dx, float dy, float dz, int color) {
			float[] a = project(ax, ay, az);
			float[] b = project(bx, by, bz);
			float[] c = project(cx, cy, cz);
			float[] d = project(dx, dy, dz);
			if (a == null || b == null || c == null || d == null) {
				return;
			}
			canvas.quad(a[0], a[1], color, b[0], b[1], color, c[0], c[1], color, d[0], d[1], color);
		}

		NeonMesher.QuadSink sink(Canvas canvas) {
			float[] xs = new float[4];
			float[] ys = new float[4];
			int[] cs = new int[4];
			boolean[] ok = new boolean[4];
			int[] n = {0};
			return (x, y, z, argb) -> {
				int i = n[0];
				float[] s = projectRelative(x, y, z);
				ok[i] = s != null;
				if (s != null) {
					xs[i] = s[0];
					ys[i] = s[1];
				}
				cs[i] = argb;
				if (++n[0] == 4) {
					n[0] = 0;
					if (ok[0] && ok[1] && ok[2] && ok[3]) {
						canvas.quad(xs[0], ys[0], cs[0], xs[1], ys[1], cs[1], xs[2], ys[2], cs[2], xs[3], ys[3], cs[3]);
					}
				}
			};
		}
	}
}
