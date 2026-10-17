package dev.oppaclient.overlay;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.Arrays;

public final class NeonMesher {
	public interface QuadSink {
		void vertex(float x, float y, float z, int argb);
	}

	public static final class Placement {
		public double baseX, baseY, baseZ;
		public float scaleX = 1f, scaleY = 1f, scaleZ = 1f;
		public double eyeX, eyeY, eyeZ;
		public float envelope = 1f;
		public float width = 1f;
		public float inflate;
		public float pull;

		public Placement map(Box bounds, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
			scaleX = safeScale(maxX - minX, bounds.getLengthX());
			scaleY = safeScale(maxY - minY, bounds.getLengthY());
			scaleZ = safeScale(maxZ - minZ, bounds.getLengthZ());
			baseX = minX - bounds.minX * scaleX - eyeX;
			baseY = minY - bounds.minY * scaleY - eyeY;
			baseZ = minZ - bounds.minZ * scaleZ - eyeZ;
			return this;
		}

		private static float safeScale(double target, double source) {
			return source < 1e-6 ? 1f : (float) (target / source);
		}
	}

	private static final Direction[] DIRECTIONS = Direction.values();
	private static final int MAX_ALONG = 96;

	private int[] valence = new int[64];
	private boolean[] visible = new boolean[64];
	private final float[] along = new float[MAX_ALONG];
	private final float[] alongFactor = new float[MAX_ALONG];
	private final int[] alongTube = new int[MAX_ALONG];
	private final int[] alongCore = new int[MAX_ALONG];
	private final int[] rowColor = new int[MAX_ALONG * Look.MAX_ROWS];
	private final float[] rowAlpha = new float[MAX_ALONG * Look.MAX_ROWS];

	public enum Edges {
		FRONT,
		BACK,
		ALL
	}

	public void tubes(EdgeSet set, Look look, Placement at, Edges which, QuadSink sink) {
		int edgeCount = set.edgeCount();
		ensure(set.vertexCount(), edgeCount);
		Arrays.fill(valence, 0, set.vertexCount(), 0);

		for (int e = 0; e < edgeCount; e++) {
			boolean show = switch (which) {
				case FRONT -> facesEye(set, e, at);
				case BACK -> !facesEye(set, e, at);
				case ALL -> true;
			};
			visible[e] = show;
			if (show) {
				valence[set.edges[e * 2]]++;
				valence[set.edges[e * 2 + 1]]++;
			}
		}

		float[] p0 = new float[3];
		float[] p1 = new float[3];
		for (int e = 0; e < edgeCount; e++) {
			if (!visible[e]) {
				continue;
			}
			int a = set.edges[e * 2];
			int b = set.edges[e * 2 + 1];
			float nx = normalX(set, e) * at.inflate;
			float ny = normalY(set, e) * at.inflate;
			float nz = normalZ(set, e) * at.inflate;
			point(set, a, at, p0);
			point(set, b, at, p1);
			p0[0] += nx; p0[1] += ny; p0[2] += nz;
			p1[0] += nx; p1[1] += ny; p1[2] += nz;
			ribbon(p0, p1, valence[a], valence[b], look, at, sink);
		}
	}

	private void ribbon(float[] p0, float[] p1, int k0, int k1, Look look, Placement at, QuadSink sink) {
		float dx = p1[0] - p0[0];
		float dy = p1[1] - p0[1];
		float dz = p1[2] - p0[2];
		float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length < 1e-5f) {
			return;
		}
		dx /= length;
		dy /= length;
		dz /= length;

		float mx = (p0[0] + p1[0]) * 0.5f;
		float my = (p0[1] + p1[1]) * 0.5f;
		float mz = (p0[2] + p1[2]) * 0.5f;
		float sx = dy * mz - dz * my;
		float sy = dz * mx - dx * mz;
		float sz = dx * my - dy * mx;
		float sideLength = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (sideLength < 1e-6f) {
			return;
		}
		sx /= sideLength;
		sy /= sideLength;
		sz /= sideLength;

		int rows = look.rowCount;
		float width = at.width;
		float inv0 = 1f - 1f / Math.max(1, k0);
		float inv1 = 1f - 1f / Math.max(1, k1);

		int n = 0;
		boolean coarse = rows <= 4;
		for (int r = rows - 1; r >= 1; r--) {
			if (coarse && r != rows - 1) {
				continue;
			}
			n = push(n, -look.rows[r] * width);
			n = push(n, length + look.rows[r] * width);
		}
		for (int r = 0; r < rows; r++) {
			if (coarse && r != 0 && r != rows - 1) {
				continue;
			}
			float o = look.rows[r] * width;
			if (o < length) {
				n = push(n, o);
				n = push(n, length - o);
			}
		}
		n = push(n, 0f);
		n = push(n, length);
		if (look.flows()) {
			float step = Math.min(0.25f, 0.25f / Math.max(look.flowScale, 0.01f));
			for (float s = step; s < length && n < MAX_ALONG - 1; s += step) {
				n = push(n, s);
			}
		}
		Arrays.sort(along, 0, n);
		n = dedupe(n);

		for (int i = 0; i < n; i++) {
			float s = along[i];
			float factor;
			if (s < 0f) {
				factor = look.profile(-s / width) / Math.max(1, k0);
			} else if (s > length) {
				factor = look.profile((s - length) / width) / Math.max(1, k1);
			} else {
				factor = (1f - inv0 * look.profile(s / width)) * (1f - inv1 * look.profile((length - s) / width));
			}
			alongFactor[i] = factor;
			double wx = p0[0] + dx * s + at.eyeX;
			double wy = p0[1] + dy * s + at.eyeY;
			double wz = p0[2] + dz * s + at.eyeZ;
			int tube = look.tubeColor(wx, wy, wz);
			alongTube[i] = tube;
			alongCore[i] = look.coreColor(tube);
			for (int r = 0; r < rows; r++) {
				int idx = i * Look.MAX_ROWS + r;
				rowAlpha[idx] = look.light[r] * factor * look.output * at.envelope;
				float heat = look.heat[r];
				rowColor[idx] = heat <= 0.002f ? tube : Colors.mix(tube, alongCore[i], Math.min(1f, heat));
			}
		}

		float pull = at.pull;
		for (int i = 0; i + 1 < n; i++) {
			float sa = along[i];
			float sb = along[i + 1];
			float ax = p0[0] + dx * sa, ay = p0[1] + dy * sa, az = p0[2] + dz * sa;
			float bx = p0[0] + dx * sb, by = p0[1] + dy * sb, bz = p0[2] + dz * sb;
			for (int side = -1; side <= 1; side += 2) {
				for (int r = 0; r + 1 < rows; r++) {
					int ia0 = i * Look.MAX_ROWS + r;
					int ia1 = ia0 + 1;
					int ib0 = (i + 1) * Look.MAX_ROWS + r;
					int ib1 = ib0 + 1;
					float a0 = rowAlpha[ia0], a1 = rowAlpha[ia1], b0 = rowAlpha[ib0], b1 = rowAlpha[ib1];
					if (a0 + a1 + b0 + b1 < 0.002f) {
						continue;
					}
					float o0 = look.rows[r] * width * side;
					float o1 = look.rows[r + 1] * width * side;
					emit(sink, ax + sx * o0, ay + sy * o0, az + sz * o0, a0, rowColor[ia0], pull);
					emit(sink, bx + sx * o0, by + sy * o0, bz + sz * o0, b0, rowColor[ib0], pull);
					emit(sink, bx + sx * o1, by + sy * o1, bz + sz * o1, b1, rowColor[ib1], pull);
					emit(sink, ax + sx * o1, ay + sy * o1, az + sz * o1, a1, rowColor[ia1], pull);
				}
			}
		}
	}

	public void fastTubes(EdgeSet set, int color, float halfWidth, float alpha, Placement at, Edges which, QuadSink sink) {
		int edgeCount = set.edgeCount();
		ensure(set.vertexCount(), edgeCount);
		int core = Colors.argb(Math.min(1f, alpha), color);
		int clear = color & 0xFFFFFF;
		float[] p0 = new float[3];
		float[] p1 = new float[3];
		for (int e = 0; e < edgeCount; e++) {
			boolean show = switch (which) {
				case FRONT -> facesEye(set, e, at);
				case BACK -> !facesEye(set, e, at);
				case ALL -> true;
			};
			if (!show) {
				continue;
			}
			point(set, set.edges[e * 2], at, p0);
			point(set, set.edges[e * 2 + 1], at, p1);
			float dx = p1[0] - p0[0], dy = p1[1] - p0[1], dz = p1[2] - p0[2];
			float mx = (p0[0] + p1[0]) * 0.5f, my = (p0[1] + p1[1]) * 0.5f, mz = (p0[2] + p1[2]) * 0.5f;
			float sx = dy * mz - dz * my;
			float sy = dz * mx - dx * mz;
			float sz = dx * my - dy * mx;
			float l = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
			if (l < 1e-6f) {
				continue;
			}
			float w = halfWidth * Math.max(1f, (float) Math.sqrt(mx * mx + my * my + mz * mz) / 6f) / l;
			sx *= w;
			sy *= w;
			sz *= w;
			for (int side = -1; side <= 1; side += 2) {
				sink.vertex(p0[0], p0[1], p0[2], core);
				sink.vertex(p1[0], p1[1], p1[2], core);
				sink.vertex(p1[0] + sx * side, p1[1] + sy * side, p1[2] + sz * side, clear);
				sink.vertex(p0[0] + sx * side, p0[1] + sy * side, p0[2] + sz * side, clear);
			}
		}
	}

	public void spill(EdgeSet set, Look look, Placement at, float strength, float reach, QuadSink sink) {
		if (strength <= 0f || reach <= 0f) {
			return;
		}
		int edgeCount = set.edgeCount();
		float[] p0 = new float[3];
		float[] p1 = new float[3];
		float[] bands = {0f, 0.1f, 0.25f, 0.45f, 0.7f, 1f};
		float lift = at.inflate * 0.5f + 0.0015f;
		float base = strength * look.output * at.envelope * 0.85f;

		for (int e = 0; e < edgeCount; e++) {
			point(set, set.edges[e * 2], at, p0);
			point(set, set.edges[e * 2 + 1], at, p1);
			float mx = (p0[0] + p1[0]) * 0.5f;
			float my = (p0[1] + p1[1]) * 0.5f;
			float mz = (p0[2] + p1[2]) * 0.5f;
			for (int f = 0; f < 2; f++) {
				Direction normal = DIRECTIONS[set.faces[e * 4 + f * 2]];
				Direction spread = DIRECTIONS[set.faces[e * 4 + f * 2 + 1]];
				if (normal.getOffsetX() * mx + normal.getOffsetY() * my + normal.getOffsetZ() * mz >= 0f) {
					continue;
				}
				float axisScale = scaleAlong(at, spread.getAxis());
				float distance = Math.min(reach, set.faceReach[e * 2 + f] * axisScale);
				if (distance <= 1e-4f) {
					continue;
				}
				float ux = spread.getOffsetX() * distance;
				float uy = spread.getOffsetY() * distance;
				float uz = spread.getOffsetZ() * distance;
				float lx = normal.getOffsetX() * lift;
				float ly = normal.getOffsetY() * lift;
				float lz = normal.getOffsetZ() * lift;
				int c0 = look.tubeColor(p0[0] + at.eyeX, p0[1] + at.eyeY, p0[2] + at.eyeZ);
				int c1 = look.tubeColor(p1[0] + at.eyeX, p1[1] + at.eyeY, p1[2] + at.eyeZ);
				for (int i = 0; i + 1 < bands.length; i++) {
					float t0 = bands[i];
					float t1 = bands[i + 1];
					float a0 = base * (1f - t0) * (1f - t0);
					float a1 = base * (1f - t1) * (1f - t1);
					emit(sink, p0[0] + ux * t0 + lx, p0[1] + uy * t0 + ly, p0[2] + uz * t0 + lz, a0, c0, 0f);
					emit(sink, p1[0] + ux * t0 + lx, p1[1] + uy * t0 + ly, p1[2] + uz * t0 + lz, a0, c1, 0f);
					emit(sink, p1[0] + ux * t1 + lx, p1[1] + uy * t1 + ly, p1[2] + uz * t1 + lz, a1, c1, 0f);
					emit(sink, p0[0] + ux * t1 + lx, p0[1] + uy * t1 + ly, p0[2] + uz * t1 + lz, a1, c0, 0f);
				}
			}
		}
	}

	public void fill(EdgeSet set, Look look, Placement at, float strength, QuadSink sink) {
		if (strength <= 0f) {
			return;
		}
		float alpha = strength * look.output * at.envelope;
		float lift = at.inflate * 0.5f + 0.001f;
		for (Box box : set.boxes) {
			float x0 = (float) (at.baseX + box.minX * at.scaleX);
			float y0 = (float) (at.baseY + box.minY * at.scaleY);
			float z0 = (float) (at.baseZ + box.minZ * at.scaleZ);
			float x1 = (float) (at.baseX + box.maxX * at.scaleX);
			float y1 = (float) (at.baseY + box.maxY * at.scaleY);
			float z1 = (float) (at.baseZ + box.maxZ * at.scaleZ);
			int color = look.tubeColor((x0 + x1) * 0.5 + at.eyeX, (y0 + y1) * 0.5 + at.eyeY, (z0 + z1) * 0.5 + at.eyeZ);
			if (y0 > 0f) {
				quad(sink, x0, y0 - lift, z0, x1, y0 - lift, z0, x1, y0 - lift, z1, x0, y0 - lift, z1, alpha, color);
			}
			if (y1 < 0f) {
				quad(sink, x0, y1 + lift, z0, x0, y1 + lift, z1, x1, y1 + lift, z1, x1, y1 + lift, z0, alpha, color);
			}
			if (z0 > 0f) {
				quad(sink, x0, y0, z0 - lift, x0, y1, z0 - lift, x1, y1, z0 - lift, x1, y0, z0 - lift, alpha, color);
			}
			if (z1 < 0f) {
				quad(sink, x0, y0, z1 + lift, x1, y0, z1 + lift, x1, y1, z1 + lift, x0, y1, z1 + lift, alpha, color);
			}
			if (x0 > 0f) {
				quad(sink, x0 - lift, y0, z0, x0 - lift, y0, z1, x0 - lift, y1, z1, x0 - lift, y1, z0, alpha, color);
			}
			if (x1 < 0f) {
				quad(sink, x1 + lift, y0, z0, x1 + lift, y1, z0, x1 + lift, y1, z1, x1 + lift, y0, z1, alpha, color);
			}
		}
	}

	private static void quad(QuadSink sink, float ax, float ay, float az, float bx, float by, float bz,
							 float cx, float cy, float cz, float dx, float dy, float dz, float alpha, int color) {
		int argb = Colors.argb(Math.min(alpha, 1f), color);
		sink.vertex(ax, ay, az, argb);
		sink.vertex(bx, by, bz, argb);
		sink.vertex(cx, cy, cz, argb);
		sink.vertex(dx, dy, dz, argb);
	}

	private static void emit(QuadSink sink, float x, float y, float z, float alpha, int color, float pull) {
		if (pull > 0f) {
			float d = (float) Math.sqrt(x * x + y * y + z * z);
			float k = d > pull * 2f ? 1f - pull / d : 0.5f;
			x *= k;
			y *= k;
			z *= k;
		}
		sink.vertex(x, y, z, Colors.argb(Math.min(alpha, 1f), color));
	}

	private static boolean facesEye(EdgeSet set, int e, Placement at) {
		int a = set.edges[e * 2];
		int b = set.edges[e * 2 + 1];
		float mx = (float) (at.baseX + (set.vertices[a * 3] + set.vertices[b * 3]) * 0.5f * at.scaleX);
		float my = (float) (at.baseY + (set.vertices[a * 3 + 1] + set.vertices[b * 3 + 1]) * 0.5f * at.scaleY);
		float mz = (float) (at.baseZ + (set.vertices[a * 3 + 2] + set.vertices[b * 3 + 2]) * 0.5f * at.scaleZ);
		for (int f = 0; f < 2; f++) {
			Direction normal = DIRECTIONS[set.faces[e * 4 + f * 2]];
			if (normal.getOffsetX() * mx + normal.getOffsetY() * my + normal.getOffsetZ() * mz < 0f) {
				return true;
			}
		}
		return false;
	}

	private static float normalX(EdgeSet set, int e) {
		return DIRECTIONS[set.faces[e * 4]].getOffsetX() + DIRECTIONS[set.faces[e * 4 + 2]].getOffsetX();
	}

	private static float normalY(EdgeSet set, int e) {
		return DIRECTIONS[set.faces[e * 4]].getOffsetY() + DIRECTIONS[set.faces[e * 4 + 2]].getOffsetY();
	}

	private static float normalZ(EdgeSet set, int e) {
		return DIRECTIONS[set.faces[e * 4]].getOffsetZ() + DIRECTIONS[set.faces[e * 4 + 2]].getOffsetZ();
	}

	private static float scaleAlong(Placement at, Direction.Axis axis) {
		return switch (axis) {
			case X -> at.scaleX;
			case Y -> at.scaleY;
			case Z -> at.scaleZ;
		};
	}

	private static void point(EdgeSet set, int v, Placement at, float[] out) {
		out[0] = (float) (at.baseX + set.vertices[v * 3] * at.scaleX);
		out[1] = (float) (at.baseY + set.vertices[v * 3 + 1] * at.scaleY);
		out[2] = (float) (at.baseZ + set.vertices[v * 3 + 2] * at.scaleZ);
	}

	private int push(int n, float value) {
		if (n < MAX_ALONG) {
			along[n] = value;
			return n + 1;
		}
		return n;
	}

	private int dedupe(int n) {
		if (n == 0) {
			return 0;
		}
		int out = 1;
		for (int i = 1; i < n; i++) {
			if (along[i] - along[out - 1] > 1e-5f) {
				along[out++] = along[i];
			}
		}
		return out;
	}

	private void ensure(int vertices, int edges) {
		if (valence.length < vertices) {
			valence = new int[vertices * 2];
		}
		if (visible.length < edges) {
			visible = new boolean[edges * 2];
		}
	}
}
