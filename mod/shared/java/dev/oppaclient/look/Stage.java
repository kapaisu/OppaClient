package dev.oppaclient.look;

import dev.oppaclient.overlay.Canvas;
import dev.oppaclient.platform.Gfx;

import java.util.Arrays;

public final class Stage {
	private final Mesh body = new Mesh();
	private final Mesh cape = new Mesh();
	private final Mesh extras = new Mesh();
	private Images.Image skin, capeImage;
	private boolean slim, bodyBuilt, capeBuilt;

	private float yaw = (float) Math.toRadians(205), yawTarget = yaw, pitch = -0.18f;
	private long[] order = new long[4096];
	private final Canvas flat = Canvas.flat(), light = Canvas.light();

	public void skin(Images.Image img, boolean slim) {
		if (img == skin && slim == this.slim) return;
		skin = img;
		this.slim = slim;
		bodyBuilt = false;
	}

	public void cape(Images.Image img) {
		if (img == capeImage) return;
		capeImage = img;
		capeBuilt = false;
	}

	public void face(float radians) {
		yawTarget = radians;
	}

	public void turn(float dx) {
		yawTarget += dx * 0.02f;
	}

	private void buildBody() {
		body.clear();
		bodyBuilt = true;
		if (skin == null) return;
		Images.Image s = skin.height() == 32 ? legacy(skin) : skin;
		int arm = slim ? 3 : 4;
		part(body, s, 1, 0, 0, 8, 8, 8, -4, -8, -4, 0);
		part(body, s, 1, 32, 0, 8, 8, 8, -4, -8, -4, 0.5f);
		part(body, s, 1, 16, 16, 8, 12, 4, -4, 0, -2, 0);
		part(body, s, 1, 16, 32, 8, 12, 4, -4, 0, -2, 0.25f);
		body.push();
		body.translate(-5, slim ? 2.5f : 2, 0);
		body.rotateZ(0.07f);
		part(body, s, 1, 40, 16, arm, 12, 4, slim ? -2 : -3, -2, -2, 0);
		part(body, s, 1, 40, 32, arm, 12, 4, slim ? -2 : -3, -2, -2, 0.25f);
		body.pop();
		body.push();
		body.translate(5, slim ? 2.5f : 2, 0);
		body.rotateZ(-0.07f);
		part(body, s, 1, 32, 48, arm, 12, 4, -1, -2, -2, 0);
		part(body, s, 1, 48, 48, arm, 12, 4, -1, -2, -2, 0.25f);
		body.pop();
		body.push();
		body.translate(-1.9f, 12, 0);
		part(body, s, 1, 0, 16, 4, 12, 4, -2, 0, -2, 0);
		part(body, s, 1, 0, 32, 4, 12, 4, -2, 0, -2, 0.25f);
		body.pop();
		body.push();
		body.translate(1.9f, 12, 0);
		part(body, s, 1, 16, 48, 4, 12, 4, -2, 0, -2, 0);
		part(body, s, 1, 0, 48, 4, 12, 4, -2, 0, -2, 0.25f);
		body.pop();
	}

	private static Images.Image legacy(Images.Image old) {
		int[] px = new int[64 * 64];
		for (int y = 0; y < 32; y++) System.arraycopy(old.argb(), y * 64, px, y * 64, 64);
		for (int y = 16; y < 32; y++) {
			for (int x = 0; x < 16; x++) {
				px[(y + 32) * 64 + 16 + x] = old.argb()[y * 64 + x];
				px[(y + 32) * 64 + 32 + x] = old.argb()[y * 64 + 40 + x];
			}
		}
		return new Images.Image(64, 64, px);
	}

	private void buildCape() {
		cape.clear();
		capeBuilt = true;
		if (capeImage == null) return;
		int scale = capeImage.width() / 64;
		cape.translate(0, 0, 2);
		cape.rotateX(0.16f);
		cape.rotateY((float) Math.PI);
		part(cape, capeImage, scale, 0, 0, 10, 16, 1, -5, 0, -1, 0);
	}

	private static void part(Mesh m, Images.Image tex, int scale, int u, int v, int w, int h, int d,
			float x0, float y0, float z0, float inflate) {
		float sx = (w + inflate * 2) / w, sy = (h + inflate * 2) / h, sz = (d + inflate * 2) / d;
		float ox = x0 - inflate, oy = y0 - inflate, oz = z0 - inflate;
		float x1 = ox + w * sx, y1 = oy + h * sy, z1 = oz + d * sz;
		int res = scale >= 4 ? 2 : 1;
		float step = 1f / res;
		boolean overlay = inflate > 0;
		for (int j = 0; j < h * res; j++) {
			for (int i = 0; i < w * res; i++) {
				float fi = i * step, fj = j * step;
				int front = sample(tex, scale, u + d + fi, v + d + fj, step);
				if (visible(front, overlay)) {
					float xa = ox + fi * sx, xb = xa + step * sx, ya = oy + fj * sy, yb = ya + step * sy;
					m.quad(xa, ya, oz, xa, yb, oz, xb, yb, oz, xb, ya, oz, front);
				}
				int back = sample(tex, scale, u + d + w + d + fi, v + d + fj, step);
				if (visible(back, overlay)) {
					float xa = x1 - (fi + step) * sx, xb = x1 - fi * sx, ya = oy + fj * sy, yb = ya + step * sy;
					m.quad(xb, ya, z1, xb, yb, z1, xa, yb, z1, xa, ya, z1, back);
				}
			}
		}
		for (int j = 0; j < h * res; j++) {
			for (int i = 0; i < d * res; i++) {
				float fi = i * step, fj = j * step;
				int right = sample(tex, scale, u + fi, v + d + fj, step);
				if (visible(right, overlay)) {
					float za = z1 - (fi + step) * sz, zb = z1 - fi * sz, ya = oy + fj * sy, yb = ya + step * sy;
					m.quad(ox, ya, zb, ox, yb, zb, ox, yb, za, ox, ya, za, right);
				}
				int left = sample(tex, scale, u + d + w + fi, v + d + fj, step);
				if (visible(left, overlay)) {
					float za = oz + fi * sz, zb = za + step * sz, ya = oy + fj * sy, yb = ya + step * sy;
					m.quad(x1, ya, za, x1, yb, za, x1, yb, zb, x1, ya, zb, left);
				}
			}
		}
		for (int j = 0; j < d * res; j++) {
			for (int i = 0; i < w * res; i++) {
				float fi = i * step, fj = j * step;
				int top = sample(tex, scale, u + d + fi, v + fj, step);
				if (visible(top, overlay)) {
					float xa = ox + fi * sx, xb = xa + step * sx, za = z1 - (fj + step) * sz, zb = z1 - fj * sz;
					m.quad(xa, oy, za, xb, oy, za, xb, oy, zb, xa, oy, zb, top);
				}
				int bottom = sample(tex, scale, u + d + w + fi, v + fj, step);
				if (visible(bottom, overlay)) {
					float xa = ox + fi * sx, xb = xa + step * sx, za = z1 - (fj + step) * sz, zb = z1 - fj * sz;
					m.quad(xa, y1, zb, xb, y1, zb, xb, y1, za, xa, y1, za, bottom);
				}
			}
		}
	}

	private static boolean visible(int c, boolean overlay) {
		return !overlay || (c >>> 24) > 24;
	}

	private static int sample(Images.Image tex, int scale, float u, float v, float step) {
		return tex.at((int) ((u + step * 0.5f) * scale), (int) ((v + step * 0.5f) * scale));
	}

	public void draw(Gfx g, int x, int y, int w, int h, Look hat, Look wings, Look aura, boolean showCape, float time, float alpha) {
		if (!bodyBuilt) buildBody();
		if (!capeBuilt) buildCape();
		yaw += (yawTarget - yaw) * 0.18f;
		cyw = (float) Math.cos(yaw);
		syw = (float) Math.sin(yaw);
		cp = (float) Math.cos(pitch);
		sp = (float) Math.sin(pitch);
		extras.clear();
		Hats.build(extras, hat, time);
		Wings.build(extras, wings, time, time * 1.6f, 0);
		Auras.build(extras, aura, time);

		float scale = h / 52f;
		float cx = x + w / 2f, cy = y + h * 0.5f - 4 * scale;
		int count = project(body, 0, 0);
		if (showCape) count = project(cape, 1, count);
		count = project(extras, 2, count);
		Arrays.sort(order, 0, count);

		flat.alpha(alpha);
		light.alpha(alpha);
		shadow(cx, cy + 24 * scale, scale, alpha);
		for (int k = count - 1; k >= 0; k--) {
			int tag = (int) order[k];
			emit(slots[tag >>> 28], tag & 0x0FFFFFFF, cx, cy, scale);
		}
		g.canvas(flat, x, y, w, h);
		g.canvas(light, x, y, w, h);
	}

	private final Mesh[] slots = {body, cape, extras};
	private float cyw, syw, cp, sp;
	private final float[] sx = new float[4], sy = new float[4];

	private int project(Mesh m, int slot, int count) {
		int n = m.quads();
		if (count + n > order.length) order = Arrays.copyOf(order, (count + n) * 2);
		float[] pos = m.positions();
		for (int i = 0; i < n; i++) {
			float depth = 0;
			for (int v = 0; v < 4; v++) {
				float px = pos[i * 12 + v * 3], py = pos[i * 12 + v * 3 + 1], pz = pos[i * 12 + v * 3 + 2];
				float rz = -px * syw + pz * cyw;
				depth += py * sp + rz * cp;
			}
			order[count + i] = (long) Float.floatToIntBits(depth / 4 + 1000f) << 32 | (long) slot << 28 | i;
		}
		return count + n;
	}

	private void emit(Mesh m, int i, float cx, float cy, float scale) {
		float[] pos = m.positions();
		float[] nrm = m.normals();
		int[] col = m.colors();
		byte kind = m.kinds()[i];
		if (kind == Mesh.HALO) {
			halo(pos[i * 12], pos[i * 12 + 1], pos[i * 12 + 2], nrm[i * 3], col[i * 4], cx, cy, scale);
			return;
		}
		float nx = nrm[i * 3], ny = nrm[i * 3 + 1], nz = nrm[i * 3 + 2];
		float rnx = nx * cyw + nz * syw, rnz = -nx * syw + nz * cyw;
		float rny = ny * cp - rnz * sp;
		rnz = ny * sp + rnz * cp;
		float lit = Math.abs(rnx * -0.42f + rny * -0.72f + rnz * -0.55f);
		float bright = kind == Mesh.GLOW || kind == Mesh.BRIGHT ? 1f : 0.5f + 0.55f * lit;
		Canvas target = kind == Mesh.GLOW ? light : flat;
		for (int v = 0; v < 4; v++) {
			float px = pos[i * 12 + v * 3], py = pos[i * 12 + v * 3 + 1], pz = pos[i * 12 + v * 3 + 2];
			float rx = px * cyw + pz * syw, rz = -px * syw + pz * cyw;
			float ry = py * cp - rz * sp;
			rz = py * sp + rz * cp;
			float persp = 1f / (1f + rz * 0.006f);
			sx[v] = cx + rx * scale * persp;
			sy[v] = cy + ry * scale * persp;
		}
		target.quad(sx[0], sy[0], tint(col[i * 4], bright), sx[1], sy[1], tint(col[i * 4 + 1], bright),
				sx[2], sy[2], tint(col[i * 4 + 2], bright), sx[3], sy[3], tint(col[i * 4 + 3], bright));
	}

	private void halo(float px, float py, float pz, float r, int c, float cx, float cy, float scale) {
		float rx = px * cyw + pz * syw, rz = -px * syw + pz * cyw;
		float ry = py * cp - rz * sp;
		rz = py * sp + rz * cp;
		float persp = 1f / (1f + rz * 0.006f);
		float x = cx + rx * scale * persp, y = cy + ry * scale * persp, size = r * scale * persp;
		int a = c >>> 24, core = Mesh.alpha(c, a / 255f), mid = Mesh.alpha(c, a / 255f * 0.4f), edge = Mesh.alpha(c, 0);
		int seg = 16;
		for (int k = 0; k < seg; k++) {
			double a0 = Math.PI * 2 * k / seg, a1 = Math.PI * 2 * (k + 1) / seg;
			float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
			float in = size * 0.35f;
			light.quad(x, y, core, x + c0 * in, y + s0 * in, mid, x + c1 * in, y + s1 * in, mid, x, y, core);
			light.quad(x + c0 * in, y + s0 * in, mid, x + c0 * size, y + s0 * size, edge, x + c1 * size, y + s1 * size, edge, x + c1 * in, y + s1 * in, mid);
		}
	}

	private static int tint(int c, float f) {
		return Mesh.shade(c, f);
	}

	private void shadow(float cx, float cy, float scale, float alpha) {
		int rings = 6;
		for (int r = rings; r >= 1; r--) {
			float rx = 9 * scale * r / rings, ry = 2.6f * scale * r / rings;
			int c = Mesh.alpha(0x000000, 0.08f * alpha);
			int seg = 24;
			for (int i = 0; i < seg; i += 2) {
				double a = Math.PI * 2 * i / seg, b = Math.PI * 2 * (i + 1) / seg, d = Math.PI * 2 * (i + 2) / seg;
				flat.quad(cx, cy, c, cx + (float) Math.cos(a) * rx, cy + (float) Math.sin(a) * ry, c,
						cx + (float) Math.cos(b) * rx, cy + (float) Math.sin(b) * ry, c, cx + (float) Math.cos(d) * rx, cy + (float) Math.sin(d) * ry, c);
			}
		}
	}
}
