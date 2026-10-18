package dev.oppaclient.platform;

import dev.oppaclient.Cosmetics;
import dev.oppaclient.look.Draw;
import dev.oppaclient.look.Looks;
import dev.oppaclient.look.Mesh;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;

public final class CosmeticsLayer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	public CosmeticsLayer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, AbstractClientPlayerEntity player,
			float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		if (player.isInvisible()) return;
		Looks.Worn worn = Cosmetics.worn(player);
		if (worn.empty()) return;
		float time = Draw.time(player.getUuid());
		if (!worn.aura().none()) emit(matrices, consumers, light, Draw.aura(worn.aura(), time));
		if (!worn.wings().none()) {
			matrices.push();
			getContextModel().body.rotate(matrices);
			emit(matrices, consumers, light, Draw.wings(worn.wings(), time, Cosmetics.pace(player)));
			matrices.pop();
		}
		if (!worn.hat().none()) {
			matrices.push();
			getContextModel().head.rotate(matrices);
			if (Cosmetics.helmet(player)) {
				matrices.translate(0, -0.26f, 0);
				matrices.scale(Cosmetics.HELMET, Cosmetics.HELMET, Cosmetics.HELMET);
				matrices.translate(0, 0.25f, 0);
			}
			emit(matrices, consumers, light, Draw.hat(worn.hat(), time));
			matrices.pop();
		}
	}

	static void emit(MatrixStack matrices, VertexConsumerProvider consumers, int light, Mesh mesh) {
		MatrixStack.Entry e = matrices.peek();
		if (Draw.has(mesh, Mesh.SOLID)) Draw.mesh(mesh, Mesh.SOLID, e.getPositionMatrix(), e.getNormalMatrix(), light, OverlayTexture.DEFAULT_UV, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(Cosmetics.WHITE))::vertex);
		if (Draw.has(mesh, Mesh.CLEAR)) Draw.mesh(mesh, Mesh.CLEAR, e.getPositionMatrix(), e.getNormalMatrix(), light, OverlayTexture.DEFAULT_UV, consumers.getBuffer(RenderLayer.getEntityTranslucent(Cosmetics.WHITE))::vertex);
		if (Draw.has(mesh, Mesh.GLOW)) Draw.mesh(mesh, Mesh.GLOW, e.getPositionMatrix(), e.getNormalMatrix(), light, OverlayTexture.DEFAULT_UV, consumers.getBuffer(RenderLayer.getEyes(Cosmetics.WHITE))::vertex);
	}
}
