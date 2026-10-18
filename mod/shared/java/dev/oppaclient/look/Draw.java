package dev.oppaclient.look;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.UUID;

public final class Draw {
	public interface Sink {
		void vertex(float x, float y, float z, int argb, float u, float v, int overlay, int light, float nx, float ny, float nz);
	}

	private static final Mesh scratch = new Mesh();
	private static final Vector3f p = new Vector3f(), n = new Vector3f();
	private static final int FULL_BRIGHT = 0xF000F0;

	private Draw() {}

	public static float time(UUID id) {
		return (System.nanoTime() % 1_000_000_000_000L) / 1e9f + (id.hashCode() & 1023) * 0.37f;
	}

	public static Mesh hat(Look look, float time) {
		scratch.clear();
		Hats.build(scratch, look, time);
		return scratch;
	}

	public static Mesh wings(Look look, float time, float pace) {
		scratch.clear();
		Wings.build(scratch, look, time, pace);
		return scratch;
	}

	public static Mesh aura(Look look, float time) {
		scratch.clear();
		Auras.build(scratch, look, time);
		return scratch;
	}

	private static boolean matches(byte quad, byte kind) {
		return quad == kind || kind == Mesh.SOLID && quad == Mesh.BRIGHT;
	}

	public static boolean has(Mesh m, byte kind) {
		byte[] k = m.kinds();
		for (int i = 0; i < m.quads(); i++) if (matches(k[i], kind)) return true;
		return false;
	}

	public static void mesh(Mesh m, byte kind, Matrix4f pose, Matrix3f normal, int light, int overlay, Sink sink) {
		float[] pos = m.positions(), nrm = m.normals();
		int[] col = m.colors();
		byte[] kinds = m.kinds();
		for (int q = 0; q < m.quads(); q++) {
			if (!matches(kinds[q], kind)) continue;
			int lit = kinds[q] == Mesh.GLOW || kinds[q] == Mesh.BRIGHT ? FULL_BRIGHT : light;
			if (kinds[q] == Mesh.BRIGHT) normal.transform(n.set(0, -1, 0));
			else normal.transform(n.set(nrm[q * 3], nrm[q * 3 + 1], nrm[q * 3 + 2]));
			for (int v = 0; v < 4; v++) {
				int at = q * 12 + v * 3;
				pose.transformPosition(p.set(pos[at] / 16f, pos[at + 1] / 16f, pos[at + 2] / 16f));
				sink.vertex(p.x, p.y, p.z, col[q * 4 + v], 0.5f, 0.5f, overlay, lit, n.x, n.y, n.z);
			}
		}
	}
}
