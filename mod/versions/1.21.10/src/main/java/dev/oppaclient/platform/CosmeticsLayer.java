package dev.oppaclient.platform;

import dev.oppaclient.Cosmetics;
import dev.oppaclient.look.Draw;
import dev.oppaclient.look.Looks;
import dev.oppaclient.look.Mesh;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;

import java.util.UUID;
import java.util.function.Supplier;

public final class CosmeticsLayer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
	private static final RenderLayer SOLID = RenderLayer.getEntityCutoutNoCull(Cosmetics.WHITE);
	private static final RenderLayer CLEAR = RenderLayer.getEntityTranslucent(Cosmetics.WHITE);
	private static final RenderLayer GLOW = RenderLayer.getEntityTranslucentEmissive(Cosmetics.WHITE);
	private static final RenderLayer HALO = RenderLayer.getEyes(Cosmetics.GLOW);

	public CosmeticsLayer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
		if (state.invisible) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || !(mc.world.getEntityById(state.id) instanceof AbstractClientPlayerEntity player)) return;
		Looks.Worn worn = Cosmetics.worn(player);
		if (worn.empty()) return;
		UUID id = player.getUuid();
		float time = Draw.time(id);
		float air = Cosmetics.air(player);
		if (!worn.aura().none()) submit(matrices, queue, light, () -> Draw.aura(id, worn.aura(), time));
		if (!worn.wings().none() && !Cosmetics.elytra(player)) {
			matrices.push();
			getContextModel().body.applyTransform(matrices);
			submit(matrices, queue, light, () -> Draw.wings(id, worn.wings(), time, player.getX(), player.getZ(), air));
			matrices.pop();
		}
		if (!worn.hat().none()) {
			matrices.push();
			getContextModel().head.applyTransform(matrices);
			if (Cosmetics.helmet(player)) {
				matrices.translate(0, -0.26f, 0);
				matrices.scale(Cosmetics.HELMET, Cosmetics.HELMET, Cosmetics.HELMET);
				matrices.translate(0, 0.25f, 0);
			}
			submit(matrices, queue, light, () -> Draw.hat(id, worn.hat(), time));
			matrices.pop();
		}
	}

	private static void submit(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, Supplier<Mesh> mesh) {
		Mesh probe = mesh.get();
		if (Draw.has(probe, Mesh.SOLID)) queue.submitCustom(matrices, SOLID, (e, c) -> Draw.mesh(mesh.get(), Mesh.SOLID, e.getPositionMatrix(), e.getNormalMatrix(), light, OverlayTexture.DEFAULT_UV, c::vertex));
		if (Draw.has(probe, Mesh.CLEAR)) queue.submitCustom(matrices, CLEAR, (e, c) -> Draw.mesh(mesh.get(), Mesh.CLEAR, e.getPositionMatrix(), e.getNormalMatrix(), light, OverlayTexture.DEFAULT_UV, c::vertex));
		if (Draw.has(probe, Mesh.GLOW)) queue.submitCustom(matrices, GLOW, (e, c) -> Draw.mesh(mesh.get(), Mesh.GLOW, e.getPositionMatrix(), e.getNormalMatrix(), light, OverlayTexture.DEFAULT_UV, c::vertex));
		if (Draw.has(probe, Mesh.HALO)) queue.submitCustom(matrices, HALO, (e, c) -> Draw.halos(mesh.get(), e.getPositionMatrix(), OverlayTexture.DEFAULT_UV, c::vertex));
	}
}
