package dev.oppaclient.platform;

import dev.oppaclient.overlay.NeonMesher;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

final class Quads implements WorldQuads {
	private final VertexConsumerProvider consumers;
	private final Vec3d eye;
	private final Matrix4f matrix;

	Quads(VertexConsumerProvider consumers, Vec3d eye, Matrix4f matrix) {
		this.consumers = consumers;
		this.eye = eye;
		this.matrix = matrix;
	}

	@Override
	public Vec3d eye() {
		return eye;
	}

	@Override
	public NeonMesher.QuadSink sink(boolean additive, boolean occluded) {
		RenderLayer layer = additive
				? occluded ? Phases.LIGHT_OCCLUDED : Phases.LIGHT
				: occluded ? Phases.PAINT_OCCLUDED : Phases.PAINT;
		VertexConsumer buffer = consumers.getBuffer(layer);
		return (x, y, z, argb) -> buffer.vertex(matrix, x, y, z).color(argb);
	}

	@Override
	public void flush() {
		if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw();
		}
	}
}
