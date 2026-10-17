package dev.oppaclient.overlay;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.List;

public final class EdgeSet {
	private static final double PROBE = 1e-4;

	public final float[] vertices;
	public final int[] edges;
	public final byte[] faces;
	public final float[] faceReach;
	public final Box bounds;
	public final List<Box> boxes;

	private EdgeSet(float[] vertices, int[] edges, byte[] faces, float[] faceReach, Box bounds, List<Box> boxes) {
		this.vertices = vertices;
		this.edges = edges;
		this.faces = faces;
		this.faceReach = faceReach;
		this.bounds = bounds;
		this.boxes = boxes;
	}

	public int edgeCount() {
		return edges.length / 2;
	}

	public int vertexCount() {
		return vertices.length / 3;
	}

	public static final EdgeSet CUBE = of(VoxelShapes.fullCube());

	private static final EdgeSet[] CUBE_SUBSETS = new EdgeSet[1 << 12];

	public static EdgeSet cubeEdges(int mask) {
		EdgeSet cached = CUBE_SUBSETS[mask];
		if (cached != null) {
			return cached;
		}
		int count = Integer.bitCount(mask);
		int[] edges = new int[count * 2];
		byte[] faces = new byte[count * 4];
		float[] reach = new float[count * 2];
		int n = 0;
		for (int e = 0; e < CUBE.edgeCount(); e++) {
			if ((mask & (1 << e)) == 0) {
				continue;
			}
			edges[n * 2] = CUBE.edges[e * 2];
			edges[n * 2 + 1] = CUBE.edges[e * 2 + 1];
			System.arraycopy(CUBE.faces, e * 4, faces, n * 4, 4);
			reach[n * 2] = CUBE.faceReach[e * 2];
			reach[n * 2 + 1] = CUBE.faceReach[e * 2 + 1];
			n++;
		}
		EdgeSet subset = new EdgeSet(CUBE.vertices, edges, faces, reach, CUBE.bounds, CUBE.boxes);
		CUBE_SUBSETS[mask] = subset;
		return subset;
	}

	public static Direction cubeFace(int edge, int which) {
		return Direction.values()[CUBE.faces[edge * 4 + which * 2]];
	}

	public static EdgeSet of(VoxelShape shape) {
		if (shape.isEmpty()) {
			return CUBE;
		}
		List<Box> boxes = shape.getBoundingBoxes();
		FloatArrayList vertices = new FloatArrayList();
		IntArrayList edges = new IntArrayList();
		FloatArrayList reach = new FloatArrayList();
		ByteArrayList faces = new ByteArrayList();
		Long2IntOpenHashMap index = new Long2IntOpenHashMap();
		index.defaultReturnValue(-1);

		shape.forEachEdge((x1, y1, z1, x2, y2, z2) -> {
			Direction.Axis axis;
			if (x1 != x2) {
				axis = Direction.Axis.X;
			} else if (y1 != y2) {
				axis = Direction.Axis.Y;
			} else if (z1 != z2) {
				axis = Direction.Axis.Z;
			} else {
				return;
			}
			double mx = (x1 + x2) * 0.5;
			double my = (y1 + y2) * 0.5;
			double mz = (z1 + z2) * 0.5;
			Direction.Axis u = next(axis);
			Direction.Axis v = next(u);

			boolean[] inside = new boolean[4];
			int count = 0;
			for (int q = 0; q < 4; q++) {
				int a = (q & 1) == 0 ? -1 : 1;
				int b = (q & 2) == 0 ? -1 : 1;
				double px = mx + offset(Direction.Axis.X, u, v, a, b);
				double py = my + offset(Direction.Axis.Y, u, v, a, b);
				double pz = mz + offset(Direction.Axis.Z, u, v, a, b);
				inside[q] = contains(boxes, px, py, pz);
				if (inside[q]) {
					count++;
				}
			}

			Direction faceU;
			Direction spreadU;
			Direction faceV;
			Direction spreadV;
			if (count == 1 || count == 3) {
				int q = 0;
				while (inside[q] != (count == 1)) {
					q++;
				}
				int a = (q & 1) == 0 ? -1 : 1;
				int b = (q & 2) == 0 ? -1 : 1;
				int sign = count == 1 ? -1 : 1;
				faceU = direction(u, sign * a);
				spreadU = direction(v, b);
				faceV = direction(v, sign * b);
				spreadV = direction(u, a);
			} else if (count == 2 && inside[0] == inside[3]) {
				int a = inside[0] ? -1 : 1;
				faceU = direction(u, -a);
				spreadU = direction(v, a);
				faceV = direction(v, -a);
				spreadV = direction(u, a);
			} else {
				return;
			}

			int i1 = vertex(index, vertices, x1, y1, z1);
			int i2 = vertex(index, vertices, x2, y2, z2);
			edges.add(i1);
			edges.add(i2);
			faces.add((byte) faceU.ordinal());
			faces.add((byte) spreadU.ordinal());
			faces.add((byte) faceV.ordinal());
			faces.add((byte) spreadV.ordinal());
			reach.add((float) reach(boxes, mx, my, mz, faceU, spreadU));
			reach.add((float) reach(boxes, mx, my, mz, faceV, spreadV));
		});

		if (edges.isEmpty()) {
			return CUBE;
		}
		return new EdgeSet(vertices.toFloatArray(), edges.toIntArray(), faces.toByteArray(), reach.toFloatArray(),
				shape.getBoundingBox(), List.copyOf(boxes));
	}

	private static Direction.Axis next(Direction.Axis axis) {
		return switch (axis) {
			case X -> Direction.Axis.Y;
			case Y -> Direction.Axis.Z;
			case Z -> Direction.Axis.X;
		};
	}

	private static double offset(Direction.Axis component, Direction.Axis u, Direction.Axis v, int a, int b) {
		if (component == u) {
			return a * PROBE;
		}
		if (component == v) {
			return b * PROBE;
		}
		return 0.0;
	}

	private static Direction direction(Direction.Axis axis, int sign) {
		return Direction.from(axis, sign > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
	}

	private static boolean contains(List<Box> boxes, double x, double y, double z) {
		for (Box box : boxes) {
			if (x > box.minX && x < box.maxX && y > box.minY && y < box.maxY && z > box.minZ && z < box.maxZ) {
				return true;
			}
		}
		return false;
	}

	private static double reach(List<Box> boxes, double x, double y, double z, Direction face, Direction spread) {
		double step = 1.0 / 64.0;
		double limit = 0.5;
		double nx = face.getOffsetX() * PROBE;
		double ny = face.getOffsetY() * PROBE;
		double nz = face.getOffsetZ() * PROBE;
		double t = step;
		while (t <= limit) {
			double px = x + spread.getOffsetX() * t;
			double py = y + spread.getOffsetY() * t;
			double pz = z + spread.getOffsetZ() * t;
			boolean solidBehind = contains(boxes, px - nx, py - ny, pz - nz);
			boolean openInFront = !contains(boxes, px + nx, py + ny, pz + nz);
			if (!solidBehind || !openInFront) {
				return t - step;
			}
			t += step;
		}
		return limit;
	}

	private static int vertex(Long2IntOpenHashMap index, FloatArrayList vertices, double x, double y, double z) {
		long key = (Math.round(x * 4096.0) & 0x1FFFFFL)
				| ((Math.round(y * 4096.0) & 0x1FFFFFL) << 21)
				| ((Math.round(z * 4096.0) & 0x1FFFFFL) << 42);
		int existing = index.get(key);
		if (existing >= 0) {
			return existing;
		}
		int id = vertices.size() / 3;
		vertices.add((float) x);
		vertices.add((float) y);
		vertices.add((float) z);
		index.put(key, id);
		return id;
	}
}
