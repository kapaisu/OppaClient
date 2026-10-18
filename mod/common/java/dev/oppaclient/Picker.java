package dev.oppaclient;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.function.Consumer;

public final class Picker {
	private static volatile boolean open;

	private Picker() {}

	public static void image(String title, Consumer<Path> done) {
		if (open) return;
		open = true;
		Thread t = new Thread(() -> {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				String[] kinds = {"*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"};
				PointerBuffer filters = stack.mallocPointer(kinds.length);
				for (String k : kinds) filters.put(stack.UTF8(k));
				filters.flip();
				String path = TinyFileDialogs.tinyfd_openFileDialog(title, null, filters, "Images", false);
				if (path != null) MinecraftClient.getInstance().execute(() -> done.accept(Path.of(path)));
			} finally {
				open = false;
			}
		}, "oppa-picker");
		t.setDaemon(true);
		t.start();
	}
}
