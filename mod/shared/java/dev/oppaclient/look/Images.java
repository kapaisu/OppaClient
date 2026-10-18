package dev.oppaclient.look;

import org.lwjgl.stb.STBIWriteCallback;
import org.lwjgl.stb.STBImage;
import org.lwjgl.stb.STBImageWrite;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

public final class Images {
	public record Image(int width, int height, int[] argb) {
		public int at(int x, int y) {
			x = Math.max(0, Math.min(width - 1, x));
			y = Math.max(0, Math.min(height - 1, y));
			return argb[y * width + x];
		}
	}

	private Images() {}

	public static Image decode(byte[] data) {
		ByteBuffer in = MemoryUtil.memAlloc(data.length);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			in.put(data).flip();
			IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), c = stack.mallocInt(1);
			ByteBuffer px = STBImage.stbi_load_from_memory(in, w, h, c, 4);
			if (px == null) throw new IllegalArgumentException("That file isn't an image Minecraft can read");
			try {
				int width = w.get(0), height = h.get(0);
				if (width > 8192 || height > 8192) throw new IllegalArgumentException("That image is too big");
				int[] out = new int[width * height];
				for (int i = 0; i < out.length; i++) {
					int r = px.get(i * 4) & 255, g = px.get(i * 4 + 1) & 255, b = px.get(i * 4 + 2) & 255, a = px.get(i * 4 + 3) & 255;
					out[i] = a << 24 | r << 16 | g << 8 | b;
				}
				return new Image(width, height, out);
			} finally {
				STBImage.stbi_image_free(px);
			}
		} finally {
			MemoryUtil.memFree(in);
		}
	}

	public static byte[] jpeg(Image img, int quality) {
		int w = img.width(), h = img.height();
		ByteBuffer rgb = MemoryUtil.memAlloc(w * h * 3);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		STBIWriteCallback sink = STBIWriteCallback.create((context, data, size) -> {
			byte[] chunk = new byte[size];
			MemoryUtil.memByteBuffer(data, size).get(chunk);
			out.writeBytes(chunk);
		});
		try {
			for (int c : img.argb()) {
				rgb.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c);
			}
			rgb.flip();
			if (STBImageWrite.stbi_write_jpg_to_func(sink, 0, w, h, 3, rgb, quality) == 0) throw new IllegalStateException("Couldn't encode the image");
		} finally {
			sink.free();
			MemoryUtil.memFree(rgb);
		}
		return out.toByteArray();
	}

	public static byte[] png(Image img) {
		int w = img.width(), h = img.height();
		byte[] raw = new byte[h * (1 + w * 4)];
		int p = 0;
		for (int y = 0; y < h; y++) {
			raw[p++] = 0;
			for (int x = 0; x < w; x++) {
				int c = img.argb()[y * w + x];
				raw[p++] = (byte) (c >> 16);
				raw[p++] = (byte) (c >> 8);
				raw[p++] = (byte) c;
				raw[p++] = (byte) (c >>> 24);
			}
		}
		Deflater deflater = new Deflater(9);
		deflater.setInput(raw);
		deflater.finish();
		ByteArrayOutputStream z = new ByteArrayOutputStream();
		byte[] buf = new byte[65536];
		while (!deflater.finished()) z.write(buf, 0, deflater.deflate(buf));
		deflater.end();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
		byte[] ihdr = new byte[13];
		put32(ihdr, 0, w);
		put32(ihdr, 4, h);
		ihdr[8] = 8;
		ihdr[9] = 6;
		chunk(out, "IHDR", ihdr);
		chunk(out, "IDAT", z.toByteArray());
		chunk(out, "IEND", new byte[0]);
		return out.toByteArray();
	}

	private static void put32(byte[] b, int at, int v) {
		b[at] = (byte) (v >>> 24);
		b[at + 1] = (byte) (v >>> 16);
		b[at + 2] = (byte) (v >>> 8);
		b[at + 3] = (byte) v;
	}

	private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
		byte[] len = new byte[4];
		put32(len, 0, data.length);
		out.writeBytes(len);
		byte[] t = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		out.writeBytes(t);
		out.writeBytes(data);
		CRC32 crc = new CRC32();
		crc.update(t);
		crc.update(data);
		byte[] c = new byte[4];
		put32(c, 0, (int) crc.getValue());
		out.writeBytes(c);
	}

	public static Image resample(Image src, int sx, int sy, int sw, int sh, int w, int h) {
		int[] out = new int[w * h];
		for (int y = 0; y < h; y++) {
			float y0 = sy + (float) y * sh / h, y1 = sy + (float) (y + 1) * sh / h;
			for (int x = 0; x < w; x++) {
				float x0 = sx + (float) x * sw / w, x1 = sx + (float) (x + 1) * sw / w;
				out[y * w + x] = average(src, x0, y0, x1, y1);
			}
		}
		return new Image(w, h, out);
	}

	private static int average(Image src, float x0, float y0, float x1, float y1) {
		int ix0 = (int) Math.floor(x0), iy0 = (int) Math.floor(y0);
		int ix1 = Math.max(ix0 + 1, (int) Math.ceil(x1)), iy1 = Math.max(iy0 + 1, (int) Math.ceil(y1));
		int step = Math.max(1, Math.max(ix1 - ix0, iy1 - iy0) / 6);
		long a = 0, r = 0, g = 0, b = 0, n = 0;
		for (int y = iy0; y < iy1; y += step) {
			for (int x = ix0; x < ix1; x += step) {
				int c = src.at(x, y);
				int al = c >>> 24;
				a += al;
				r += (long) (c >> 16 & 255) * al;
				g += (long) (c >> 8 & 255) * al;
				b += (long) (c & 255) * al;
				n++;
			}
		}
		if (a == 0) return 0;
		return (int) (a / n) << 24 | (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a);
	}
}
