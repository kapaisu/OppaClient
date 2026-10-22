package dev.oppaclient.look;

final class Sprite {
	final int w, h;
	final byte[] cells;

	Sprite(int w, int h) {
		this.w = w;
		this.h = h;
		cells = new byte[w * h];
	}

	int at(int x, int y) {
		return x < 0 || y < 0 || x >= w || y >= h ? 0 : cells[y * w + x];
	}

	void set(int x, int y, int v) {
		if (x >= 0 && y >= 0 && x < w && y < h) cells[y * w + x] = (byte) v;
	}

	interface Paint {
		int at(float along, float across);
	}

	void capsule(float x0, float y0, float x1, float y1, float r0, float r1, Paint paint) {
		float dx = x1 - x0, dy = y1 - y0, len2 = dx * dx + dy * dy;
		int minX = (int) Math.floor(Math.min(x0, x1) - Math.max(r0, r1)), maxX = (int) Math.ceil(Math.max(x0, x1) + Math.max(r0, r1));
		int minY = (int) Math.floor(Math.min(y0, y1) - Math.max(r0, r1)), maxY = (int) Math.ceil(Math.max(y0, y1) + Math.max(r0, r1));
		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				float px = x + 0.5f, py = y + 0.5f;
				float t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - x0) * dx + (py - y0) * dy) / len2));
				float cx = x0 + dx * t, cy = y0 + dy * t;
				float d = (float) Math.hypot(px - cx, py - cy), r = r0 + (r1 - r0) * t;
				if (d <= r) {
					int v = paint.at(t, r <= 0 ? 0 : d / r);
					if (v != 0) set(x, y, v);
				}
			}
		}
	}

	void line(float x0, float y0, float x1, float y1, float r, int v) {
		capsule(x0, y0, x1, y1, r, r, (t, d) -> v);
	}

	void triangle(float ax, float ay, float bx, float by, float cx, float cy, Paint paint) {
		int minX = (int) Math.floor(Math.min(ax, Math.min(bx, cx))), maxX = (int) Math.ceil(Math.max(ax, Math.max(bx, cx)));
		int minY = (int) Math.floor(Math.min(ay, Math.min(by, cy))), maxY = (int) Math.ceil(Math.max(ay, Math.max(by, cy)));
		float area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay);
		if (Math.abs(area) < 1e-4f) return;
		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				float px = x + 0.5f, py = y + 0.5f;
				float u = ((bx - px) * (cy - py) - (cx - px) * (by - py)) / area;
				float v = ((cx - px) * (ay - py) - (ax - px) * (cy - py)) / area;
				float k = 1 - u - v;
				if (u < 0 || v < 0 || k < 0) continue;
				int c = paint.at(u, k);
				if (c != 0) set(x, y, c);
			}
		}
	}

	void ellipse(float cx, float cy, float rx, float ry, float angle, Paint paint) {
		float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
		int reach = (int) Math.ceil(Math.max(rx, ry)) + 1;
		for (int y = (int) cy - reach; y <= (int) cy + reach; y++) {
			for (int x = (int) cx - reach; x <= (int) cx + reach; x++) {
				float px = x + 0.5f - cx, py = y + 0.5f - cy;
				float u = (px * c + py * s) / rx, v = (-px * s + py * c) / ry;
				float d = (float) Math.sqrt(u * u + v * v);
				if (d <= 1) {
					int col = paint.at(d, (float) Math.atan2(v, u));
					if (col != 0) set(x, y, col);
				}
			}
		}
	}

	void outline(int fill, int edge) {
		byte[] copy = cells.clone();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (copy[y * w + x] != fill) continue;
				boolean open = x == 0 || y == 0 || x == w - 1 || y == h - 1
						|| copy[y * w + x - 1] == 0 || copy[y * w + x + 1] == 0 || copy[(y - 1) * w + x] == 0 || copy[(y + 1) * w + x] == 0;
				if (open) cells[y * w + x] = (byte) edge;
			}
		}
	}

	void draw(Mesh m, int[] colors, byte[] pens, float cell, float depth, int fromX, int toX, float ox, float oy, int side) {
		float z0 = -depth / 2, z1 = depth / 2;
		for (int y = 0; y < h; y++) {
			int x = fromX;
			while (x < toX) {
				int v = at(x, y);
				if (v == 0) {
					x++;
					continue;
				}
				int end = x + 1;
				while (end < toX && at(end, y) == v) end++;
				float xa = ox + x * cell, xb = ox + end * cell, ya = oy + y * cell, yb = ya + cell;
				m.pen(pens[v]);
				face(m, side, xa, ya, z0, xa, yb, z0, xb, yb, z0, xb, ya, z0, colors[v]);
				face(m, side, xb, ya, z1, xb, yb, z1, xa, yb, z1, xa, ya, z1, colors[v]);
				x = end;
			}
		}
		for (int y = 0; y <= h; y++) {
			int x = fromX;
			while (x < toX) {
				int v = edgeY(x, y);
				if (v == 0) {
					x++;
					continue;
				}
				int end = x + 1;
				while (end < toX && edgeY(end, y) == v) end++;
				float xa = ox + x * cell, xb = ox + end * cell, ye = oy + y * cell;
				int c = Mesh.shade(colors[Math.abs(v)], 0.82f);
				m.pen(pens[Math.abs(v)]);
				if (v > 0) face(m, side, xa, ye, z1, xb, ye, z1, xb, ye, z0, xa, ye, z0, c);
				else face(m, side, xa, ye, z0, xb, ye, z0, xb, ye, z1, xa, ye, z1, c);
				x = end;
			}
		}
		for (int x = fromX; x <= toX; x++) {
			int y = 0;
			while (y < h) {
				int v = edgeX(x, y, fromX, toX);
				if (v == 0) {
					y++;
					continue;
				}
				int end = y + 1;
				while (end < h && edgeX(x, end, fromX, toX) == v) end++;
				float xe = ox + x * cell, ya = oy + y * cell, yb = oy + end * cell;
				int c = Mesh.shade(colors[Math.abs(v)], 0.82f);
				m.pen(pens[Math.abs(v)]);
				if (v > 0) face(m, side, xe, ya, z0, xe, yb, z0, xe, yb, z1, xe, ya, z1, c);
				else face(m, side, xe, ya, z1, xe, yb, z1, xe, yb, z0, xe, ya, z0, c);
				y = end;
			}
		}
		m.pen(Mesh.SOLID);
	}

	private int edgeY(int x, int y) {
		int up = at(x, y - 1), down = at(x, y);
		return up != 0 && down == 0 ? up : up == 0 && down != 0 ? -down : 0;
	}

	private int edgeX(int x, int y, int fromX, int toX) {
		int left = x > fromX ? at(x - 1, y) : 0, right = x < toX ? at(x, y) : 0;
		return left != 0 && right == 0 ? left : left == 0 && right != 0 ? -right : 0;
	}

	private static void face(Mesh m, int side, float x0, float y0, float z0, float x1, float y1, float z1,
			float x2, float y2, float z2, float x3, float y3, float z3, int c) {
		if (side > 0) m.quad(x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, c);
		else m.quad(-x3, y3, z3, -x2, y2, z2, -x1, y1, z1, -x0, y0, z0, c);
	}
}
