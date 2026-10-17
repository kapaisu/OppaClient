package dev.oppaclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

public record Pixels(int width, int height, int[] argb) {
	public static Pixels load(Identifier id) {
		try (InputStream in = MinecraftClient.getInstance().getResourceManager().open(id)) {
			BufferedImage img = ImageIO.read(in);
			int w = img.getWidth(), h = img.getHeight();
			return new Pixels(w, h, img.getRGB(0, 0, w, h, null, 0, w));
		} catch (IOException | RuntimeException e) {
			OppaClient.LOG.error("couldn't load {}", id, e);
			return null;
		}
	}

	public static Pixels read(byte[] png) {
		try {
			BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(png));
			if (img == null) return null;
			int w = img.getWidth(), h = img.getHeight();
			return new Pixels(w, h, img.getRGB(0, 0, w, h, null, 0, w));
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	public int at(int x, int y) {
		return argb[y * width + x];
	}
}
