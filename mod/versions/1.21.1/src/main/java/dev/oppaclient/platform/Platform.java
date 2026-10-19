package dev.oppaclient.platform;

import dev.oppaclient.OppaClient;
import dev.oppaclient.hud.Hud;
import dev.oppaclient.overlay.IrisCompat;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.multiplayer.AddServerScreen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public final class Platform {
	private static final String CATEGORY = "key.category.oppaclient.oppa";

	private Platform() {}

	public static void init() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof PlayerEntityRenderer player) helper.register(new CosmeticsLayer(player));
		});
		IrisCompat.init();
		WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, hit) -> !OppaClient.OUTLINE.hidesVanillaOutline());
		WorldRenderEvents.LAST.register(context -> {
			if (context.consumers() == null) return;
			OppaClient.OUTLINE.render(new Quads(context.consumers(), context.camera().getPos(), new Matrix4f(context.positionMatrix())));
		});
		HudRenderCallback.EVENT.register((ctx, tick) -> Hud.render(new GfxImpl(ctx)));
	}

	public static boolean gliding(net.minecraft.entity.player.PlayerEntity player) {
		return player.isFallFlying();
	}

	public static void leave() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) return;
		boolean single = mc.isInSingleplayer();
		mc.world.disconnect();
		if (single) mc.disconnect(new net.minecraft.client.gui.screen.MessageScreen(Text.translatable("menu.savingLevel")));
		else mc.disconnect();
		mc.setScreen(new net.minecraft.client.gui.screen.TitleScreen());
	}

	public static void join(String address) {
		MinecraftClient mc = MinecraftClient.getInstance();
		leave();
		net.minecraft.client.network.ServerInfo info = new net.minecraft.client.network.ServerInfo(address, address, net.minecraft.client.network.ServerInfo.ServerType.OTHER);
		net.minecraft.client.gui.screen.multiplayer.ConnectScreen.connect(new net.minecraft.client.gui.screen.TitleScreen(), mc,
				net.minecraft.client.network.ServerAddress.parse(address), info, false, null);
	}

	public static void showCape() {
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.execute(() -> {
			if (mc.options.isPlayerModelPartEnabled(net.minecraft.entity.player.PlayerModelPart.CAPE)) return;
			mc.options.togglePlayerModelPart(net.minecraft.entity.player.PlayerModelPart.CAPE, true);
			mc.options.write();
			mc.options.sendClientSettings();
		});
	}

	public static float guiScale() {
		return (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
	}

	public static Style font(Identifier id) {
		return Style.EMPTY.withFont(id);
	}

	public static void upload(Identifier id, int w, int h, int[] argb) {
		NativeImage img = new NativeImage(w, h, true);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int c = argb[y * w + x];
				img.setColor(x, y, (c & 0xFF00FF00) | (c & 0xFF) << 16 | (c >> 16) & 0xFF);
			}
		}
		MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(img));
	}

	public static KeyBinding key(String name, int code) {
		return new KeyBinding(name, InputUtil.Type.KEYSYM, code, CATEGORY);
	}

	public static boolean matches(KeyBinding binding, int key, int scancode) {
		return binding.matchesKey(key, scancode);
	}

	public static void bind(KeyBinding binding, int key, int scancode) {
		binding.setBoundKey(InputUtil.fromKeyCode(key, scancode));
	}

	public static String gameVersion() {
		return SharedConstants.getGameVersion().getName();
	}

	public static float tickProgress() {
		return MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true);
	}

	public static Vec3d entityPos(Entity entity) {
		return entity.getPos();
	}

	public static void createWorld(net.minecraft.client.gui.screen.Screen parent) {
		CreateWorldScreen.create(MinecraftClient.getInstance(), parent);
	}

	public static void ping(net.minecraft.client.network.MultiplayerServerListPinger pinger, net.minecraft.client.network.ServerInfo info,
			Runnable save, Runnable done) throws java.net.UnknownHostException {
		pinger.add(info, save, done);
	}

	public static net.minecraft.client.gui.screen.Screen serverForm(net.minecraft.client.gui.screen.Screen parent,
			it.unimi.dsi.fastutil.booleans.BooleanConsumer done, net.minecraft.client.network.ServerInfo info, boolean adding) {
		return new AddServerScreen(parent, done, info);
	}
}
