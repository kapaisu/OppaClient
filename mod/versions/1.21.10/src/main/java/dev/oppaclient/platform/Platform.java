package dev.oppaclient.platform;

import dev.oppaclient.OppaClient;
import dev.oppaclient.hud.Crosshair;
import dev.oppaclient.hud.Hud;
import dev.oppaclient.overlay.IrisCompat;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
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
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class Platform {
	private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(OppaClient.id("oppa"));

	private Platform() {}

	public static void init() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof PlayerEntityRenderer<?> player) helper.register(new CosmeticsLayer(player));
		});
		NeonPipelines.bootstrap();
		IrisCompat.init((Object[]) NeonPipelines.world());

		WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> !OppaClient.OUTLINE.hidesVanillaOutline());
		WorldRenderEvents.END_MAIN.register(context -> {
			if (context.consumers() == null) return;
			OppaClient.OUTLINE.render(new Quads(context.consumers(), context.worldState().cameraRenderState.pos,
					context.matrices().peek().getPositionMatrix()));
		});

		HudElementRegistry.addLast(OppaClient.id("hud"), (ctx, tick) -> Hud.render(new GfxImpl(ctx)));
		HudElementRegistry.replaceElement(VanillaHudElements.STATUS_EFFECTS, vanilla -> (ctx, tick) -> {
			if (!Hud.drawsEffects()) vanilla.render(ctx, tick);
		});
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (ctx, tick) -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (Hud.drawsCrosshair(mc)) Crosshair.draw(new GfxImpl(ctx), mc);
			else vanilla.render(ctx, tick);
		});
	}

	public static boolean gliding(net.minecraft.entity.player.PlayerEntity player) {
		return player.isGliding();
	}

	public static void join(String address) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world != null) {
			if (mc.isInSingleplayer()) mc.disconnectWithSavingScreen();
			else mc.disconnectWithProgressScreen();
		}
		net.minecraft.client.network.ServerInfo info = new net.minecraft.client.network.ServerInfo(address, address, net.minecraft.client.network.ServerInfo.ServerType.OTHER);
		net.minecraft.client.gui.screen.multiplayer.ConnectScreen.connect(new net.minecraft.client.gui.screen.TitleScreen(), mc,
				net.minecraft.client.network.ServerAddress.parse(address), info, false, null);
	}

	public static void showCape() {
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.execute(() -> {
			if (mc.options.isPlayerModelPartEnabled(net.minecraft.entity.player.PlayerModelPart.CAPE)) return;
			mc.options.setPlayerModelPart(net.minecraft.entity.player.PlayerModelPart.CAPE, true);
			mc.options.write();
			mc.options.sendClientSettings();
		});
	}

	public static float guiScale() {
		return MinecraftClient.getInstance().getWindow().getScaleFactor();
	}

	public static Style font(Identifier id) {
		return Style.EMPTY.withFont(new StyleSpriteSource.Font(id));
	}

	public static void upload(Identifier id, int w, int h, int[] argb) {
		NativeImage img = new NativeImage(w, h, true);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				img.setColorArgb(x, y, argb[y * w + x]);
			}
		}
		MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(id::toString, img));
	}

	public static KeyBinding key(String name, int code) {
		return new KeyBinding(name, InputUtil.Type.KEYSYM, code, CATEGORY);
	}

	public static boolean matches(KeyBinding binding, int key, int scancode) {
		return binding.matchesKey(new net.minecraft.client.input.KeyInput(key, scancode, 0));
	}

	public static void bind(KeyBinding binding, int key, int scancode) {
		binding.setBoundKey(InputUtil.fromKeyCode(new net.minecraft.client.input.KeyInput(key, scancode, 0)));
	}

	public static String gameVersion() {
		return SharedConstants.getGameVersion().name();
	}

	public static float tickProgress() {
		return MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(true);
	}

	public static Vec3d entityPos(Entity entity) {
		return entity.getEntityPos();
	}

	public static void createWorld(net.minecraft.client.gui.screen.Screen parent) {
		CreateWorldScreen.show(MinecraftClient.getInstance(), () -> MinecraftClient.getInstance().setScreen(parent));
	}

	public static void ping(net.minecraft.client.network.MultiplayerServerListPinger pinger, net.minecraft.client.network.ServerInfo info,
			Runnable save, Runnable done) throws java.net.UnknownHostException {
		pinger.add(info, save, done);
	}

	public static net.minecraft.client.gui.screen.Screen serverForm(net.minecraft.client.gui.screen.Screen parent,
			it.unimi.dsi.fastutil.booleans.BooleanConsumer done, net.minecraft.client.network.ServerInfo info, boolean adding) {
		return new AddServerScreen(parent, Text.translatable(adding ? "manageServer.add.title" : "manageServer.edit.title"), done, info);
	}
}
