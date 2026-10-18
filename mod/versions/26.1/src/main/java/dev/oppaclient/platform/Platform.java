package dev.oppaclient.platform;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import dev.oppaclient.OppaClient;
import dev.oppaclient.hud.Crosshair;
import dev.oppaclient.hud.Hud;
import dev.oppaclient.overlay.IrisCompat;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class Platform {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(OppaClient.id("oppa"));

	private Platform() {}

	public static void init() {
		NeonPipelines.bootstrap();
		IrisCompat.init((Object[]) NeonPipelines.world());

		LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> !OppaClient.OUTLINE.hidesVanillaOutline());
		LevelRenderEvents.END_MAIN.register(context -> {
			if (context.bufferSource() == null) return;
			OppaClient.OUTLINE.render(new Quads(context.bufferSource(), context.levelState().cameraRenderState.pos,
					context.poseStack().last().pose()));
		});

		HudElementRegistry.addLast(OppaClient.id("hud"), (ctx, tick) -> Hud.render(new GfxImpl(ctx)));
		HudElementRegistry.replaceElement(VanillaHudElements.MOB_EFFECTS, vanilla -> (ctx, tick) -> {
			if (!Hud.drawsEffects()) vanilla.extractRenderState(ctx, tick);
		});
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (ctx, tick) -> {
			Minecraft mc = Minecraft.getInstance();
			if (Hud.drawsCrosshair(mc)) Crosshair.draw(new GfxImpl(ctx), mc);
			else vanilla.extractRenderState(ctx, tick);
		});
	}

	public static net.minecraft.client.gui.screens.Screen screen() {
		return Minecraft.getInstance().screen;
	}

	public static void show(net.minecraft.client.gui.screens.Screen screen) {
		Minecraft.getInstance().setScreen(screen);
	}

	public static boolean hudHidden() {
		return Minecraft.getInstance().options.hideGui;
	}

	public static net.minecraft.client.gui.screens.Screen options(net.minecraft.client.gui.screens.Screen parent) {
		return new net.minecraft.client.gui.screens.options.OptionsScreen(parent, Minecraft.getInstance().options, false);
	}

	private static volatile boolean picking;

	public static void pickImage(String title, java.util.function.Consumer<java.nio.file.Path> done) {
		if (picking) return;
		picking = true;
		Thread t = new Thread(() -> {
			try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
				String[] kinds = {"*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"};
				org.lwjgl.PointerBuffer filters = stack.mallocPointer(kinds.length);
				for (String k : kinds) filters.put(stack.UTF8(k));
				filters.flip();
				String path = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(title, null, filters, "Images", false);
				if (path != null) Minecraft.getInstance().execute(() -> done.accept(java.nio.file.Path.of(path)));
			} finally {
				picking = false;
			}
		}, "oppa-picker");
		t.setDaemon(true);
		t.start();
	}

	public static void join(String address) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) {
			if (mc.hasSingleplayerServer()) mc.disconnectWithSavingScreen();
			else mc.disconnectWithProgressScreen();
		}
		net.minecraft.client.multiplayer.ServerData info = new net.minecraft.client.multiplayer.ServerData(address, address, net.minecraft.client.multiplayer.ServerData.Type.OTHER);
		net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new net.minecraft.client.gui.screens.TitleScreen(), mc,
				net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address), info, false, null);
	}

	public static void showCape() {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (mc.options.isModelPartEnabled(net.minecraft.world.entity.player.PlayerModelPart.CAPE)) return;
			mc.options.setModelPart(net.minecraft.world.entity.player.PlayerModelPart.CAPE, true);
			mc.options.save();
			mc.options.broadcastOptions();
		});
	}

	public static float guiScale() {
		return Minecraft.getInstance().getWindow().getGuiScale();
	}

	public static Style font(Identifier id) {
		return Style.EMPTY.withFont(new FontDescription.Resource(id));
	}

	public static void upload(Identifier id, int w, int h, int[] argb) {
		NativeImage img = new NativeImage(w, h, true);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				img.setPixel(x, y, argb[y * w + x]);
			}
		}
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, img));
	}

	public static KeyMapping key(String name, int code) {
		return new KeyMapping(name, InputConstants.Type.KEYSYM, code, CATEGORY);
	}

	public static boolean matches(KeyMapping binding, int key, int scancode) {
		return binding.matches(new net.minecraft.client.input.KeyEvent(key, scancode, 0));
	}

	public static void bind(KeyMapping binding, int key, int scancode) {
		binding.setKey(InputConstants.getKey(new net.minecraft.client.input.KeyEvent(key, scancode, 0)));
	}

	public static String gameVersion() {
		return SharedConstants.getCurrentVersion().name();
	}

	public static float tickProgress() {
		return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
	}

	public static Vec3 entityPos(Entity entity) {
		return entity.position();
	}

	public static void createWorld(net.minecraft.client.gui.screens.Screen parent) {
		CreateWorldScreen.openFresh(Minecraft.getInstance(), () -> show(parent));
	}

	public static void ping(net.minecraft.client.multiplayer.ServerStatusPinger pinger, net.minecraft.client.multiplayer.ServerData info,
			Runnable save, Runnable done) throws java.net.UnknownHostException {
		pinger.pingServer(info, save, done, net.minecraft.server.network.EventLoopGroupHolder.remote(Minecraft.getInstance().options.useNativeTransport()));
	}

	public static net.minecraft.client.gui.screens.Screen serverForm(net.minecraft.client.gui.screens.Screen parent,
			it.unimi.dsi.fastutil.booleans.BooleanConsumer done, net.minecraft.client.multiplayer.ServerData info, boolean adding) {
		return new ManageServerScreen(parent, Component.translatable(adding ? "manageServer.add.title" : "manageServer.edit.title"), done, info);
	}
}
