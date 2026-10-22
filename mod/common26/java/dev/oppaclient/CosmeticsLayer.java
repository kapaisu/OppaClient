package dev.oppaclient;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.oppaclient.look.Draw;
import dev.oppaclient.look.Looks;
import dev.oppaclient.look.Mesh;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.util.UUID;
import java.util.function.Supplier;

public final class CosmeticsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final RenderType SOLID = RenderTypes.entityCutout(Cosmetics.WHITE);
	private static final RenderType CLEAR = RenderTypes.entityTranslucent(Cosmetics.WHITE);
	private static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(Cosmetics.WHITE);
	private static final RenderType HALO = RenderTypes.eyes(Cosmetics.GLOW);

	public CosmeticsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) helper.register(new CosmeticsLayer(avatar));
		});
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !(mc.level.getEntity(state.id) instanceof AbstractClientPlayer player)) return;
		Looks.Worn worn = Cosmetics.worn(player);
		if (worn.empty()) return;
		UUID id = player.getUUID();
		float time = Draw.time(id);
		float air = Cosmetics.air(player);
		if (!worn.aura().none()) submit(pose, collector, light, () -> Draw.aura(id, worn.aura(), time));
		if (!worn.wings().none() && !Cosmetics.elytra(player)) {
			pose.pushPose();
			getParentModel().body.translateAndRotate(pose);
			submit(pose, collector, light, () -> Draw.wings(id, worn.wings(), time, player.getX(), player.getZ(), air));
			pose.popPose();
		}
		if (!worn.hat().none()) {
			pose.pushPose();
			getParentModel().head.translateAndRotate(pose);
			if (Cosmetics.helmet(player)) {
				pose.translate(0, -0.26f, 0);
				pose.scale(Cosmetics.HELMET, Cosmetics.HELMET, Cosmetics.HELMET);
				pose.translate(0, 0.25f, 0);
			}
			submit(pose, collector, light, () -> Draw.hat(id, worn.hat(), time));
			pose.popPose();
		}
	}

	private static void submit(PoseStack pose, SubmitNodeCollector collector, int light, Supplier<Mesh> mesh) {
		Mesh probe = mesh.get();
		if (Draw.has(probe, Mesh.SOLID)) collector.submitCustomGeometry(pose, SOLID, (p, c) -> Draw.mesh(mesh.get(), Mesh.SOLID, p.pose(), p.normal(), light, OverlayTexture.NO_OVERLAY, c::addVertex));
		if (Draw.has(probe, Mesh.CLEAR)) collector.submitCustomGeometry(pose, CLEAR, (p, c) -> Draw.mesh(mesh.get(), Mesh.CLEAR, p.pose(), p.normal(), light, OverlayTexture.NO_OVERLAY, c::addVertex));
		if (Draw.has(probe, Mesh.GLOW)) collector.submitCustomGeometry(pose, GLOW, (p, c) -> Draw.mesh(mesh.get(), Mesh.GLOW, p.pose(), p.normal(), light, OverlayTexture.NO_OVERLAY, c::addVertex));
		if (Draw.has(probe, Mesh.HALO)) collector.submitCustomGeometry(pose, HALO, (p, c) -> Draw.halos(mesh.get(), p.pose(), OverlayTexture.NO_OVERLAY, c::addVertex));
	}
}
