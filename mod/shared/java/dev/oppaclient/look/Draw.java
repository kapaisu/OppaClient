package dev.oppaclient.look;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Draw {
	public interface Sink {
		void vertex(float x, float y, float z, int argb, float u, float v, int overlay, int light, float nx, float ny, float nz);
	}

	private static final Vector3f p = new Vector3f(), n = new Vector3f(), right = new Vector3f(), up = new Vector3f();
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final long FRAME = 3_000_000L;
	private static final Map<UUID, Worn> worn = new HashMap<>();
	private static long swept;

	private static final class Part {
		final Mesh mesh = new Mesh();
		Look look;
		long built;

		boolean fresh(Look l, long now) {
			return l.equals(look) && now - built < FRAME;
		}

		Mesh done(Look l, long now) {
			look = l;
			built = now;
			return mesh;
		}
	}

	private static final class Worn {
		final Part hat = new Part(), wings = new Part(), aura = new Part();
		long seen, moved;
		double x, z;
		float speed, pace, phase;
		boolean placed;
	}

	private Draw() {}

	public static float time(UUID id) {
		return (System.nanoTime() % 1_000_000_000_000L) / 1e9f + (id.hashCode() & 1023) * 0.37f;
	}

	private static Worn of(UUID id, long now) {
		if (now - swept > 5_000_000_000L) {
			swept = now;
			worn.values().removeIf(w -> now - w.seen > 10_000_000_000L);
		}
		Worn w = worn.computeIfAbsent(id, k -> new Worn());
		w.seen = now;
		return w;
	}

	public static Mesh hat(UUID id, Look look, float time) {
		long now = System.nanoTime();
		Part part = of(id, now).hat;
		if (part.fresh(look, now)) return part.mesh;
		part.mesh.clear();
		Hats.build(part.mesh, look, time);
		return part.done(look, now);
	}

	public static Mesh aura(UUID id, Look look, float time) {
		long now = System.nanoTime();
		Part part = of(id, now).aura;
		if (part.fresh(look, now)) return part.mesh;
		part.mesh.clear();
		Auras.build(part.mesh, look, time);
		return part.done(look, now);
	}

	public static Mesh wings(UUID id, Look look, float time, double x, double z, float air) {
		long now = System.nanoTime();
		Worn w = of(id, now);
		move(w, now, x, z, air);
		if (w.wings.fresh(look, now)) return w.wings.mesh;
		w.wings.mesh.clear();
		Wings.build(w.wings.mesh, look, time, w.phase, w.pace);
		return w.wings.done(look, now);
	}

	private static void move(Worn w, long now, double x, double z, float air) {
		if (!w.placed) {
			w.placed = true;
			w.moved = now;
			w.x = x;
			w.z = z;
			return;
		}
		float dt = Math.min(0.1f, (now - w.moved) / 1e9f);
		if (dt <= 0) return;
		w.moved = now;
		float step = (float) Math.min(80, Math.hypot(x - w.x, z - w.z) / dt);
		w.x = x;
		w.z = z;
		w.speed += (step - w.speed) * (1 - (float) Math.exp(-dt / 0.3f));
		float target = Math.min(1, Math.min(0.6f, w.speed / 8f) + air);
		w.pace += (target - w.pace) * (1 - (float) Math.exp(-dt / 0.35f));
		w.phase = (w.phase + dt * (1.6f + 5f * w.pace)) % 6283.1853f;
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
			if (kinds[q] != Mesh.GLOW) continue;
			for (int v = 3; v >= 0; v--) {
				int at = q * 12 + v * 3;
				pose.transformPosition(p.set(pos[at] / 16f, pos[at + 1] / 16f, pos[at + 2] / 16f));
				sink.vertex(p.x, p.y, p.z, col[q * 4 + v], 0.5f, 0.5f, overlay, lit, -n.x, -n.y, -n.z);
			}
		}
	}

	public static void halos(Mesh m, Matrix4f pose, int overlay, Sink sink) {
		float[] pos = m.positions(), nrm = m.normals();
		int[] col = m.colors();
		byte[] kinds = m.kinds();
		float scale = pose.transformDirection(n.set(1, 0, 0)).length() / 16f;
		for (int q = 0; q < m.quads(); q++) {
			if (kinds[q] != Mesh.HALO) continue;
			pose.transformPosition(p.set(pos[q * 12] / 16f, pos[q * 12 + 1] / 16f, pos[q * 12 + 2] / 16f));
			float r = nrm[q * 3] * scale;
			if (p.lengthSquared() < 1e-8f) n.set(0, 0, 1);
			else p.normalize(n);
			n.cross(0, 1, 0, right);
			if (right.lengthSquared() < 1e-6f) right.set(1, 0, 0);
			right.normalize(r);
			right.cross(n, up).normalize(r);
			int c = col[q * 4], a = c >>> 24;
			int lit = 0xFF000000 | ((c >> 16 & 255) * a / 255) << 16 | ((c >> 8 & 255) * a / 255) << 8 | (c & 255) * a / 255;
			sink.vertex(p.x - right.x - up.x, p.y - right.y - up.y, p.z - right.z - up.z, lit, 0, 1, overlay, FULL_BRIGHT, 0, 1, 0);
			sink.vertex(p.x + right.x - up.x, p.y + right.y - up.y, p.z + right.z - up.z, lit, 1, 1, overlay, FULL_BRIGHT, 0, 1, 0);
			sink.vertex(p.x + right.x + up.x, p.y + right.y + up.y, p.z + right.z + up.z, lit, 1, 0, overlay, FULL_BRIGHT, 0, 1, 0);
			sink.vertex(p.x - right.x + up.x, p.y - right.y + up.y, p.z - right.z + up.z, lit, 0, 0, overlay, FULL_BRIGHT, 0, 1, 0);
		}
	}
}
