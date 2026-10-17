package dev.oppaclient.platform;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.oppaclient.overlay.NeonMesher;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

final class Quads implements WorldQuads {
	private final MultiBufferSource consumers;
	private final Vec3 eye;
	private final Matrix4f matrix;

	Quads(MultiBufferSource consumers, Vec3 eye, Matrix4f matrix) {
		this.consumers = consumers;
		this.eye = eye;
		this.matrix = matrix;
	}

	@Override
	public Vec3 eye() {
		return eye;
	}

	@Override
	public NeonMesher.QuadSink sink(boolean additive, boolean occluded) {
		RenderType layer = additive
				? occluded ? NeonPipelines.LIGHT_OCCLUDED_LAYER : NeonPipelines.LIGHT_LAYER
				: occluded ? NeonPipelines.PAINT_OCCLUDED_LAYER : NeonPipelines.PAINT_LAYER;
		VertexConsumer buffer = consumers.getBuffer(layer);
		return (x, y, z, argb) -> buffer.addVertex(matrix, x, y, z).setColor(argb);
	}

	@Override
	public void flush() {
		if (consumers instanceof MultiBufferSource.BufferSource immediate) {
			immediate.endBatch();
		}
	}
}
