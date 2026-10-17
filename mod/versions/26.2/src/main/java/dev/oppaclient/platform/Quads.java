package dev.oppaclient.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.oppaclient.overlay.NeonMesher;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;

final class Quads implements WorldQuads {
	private static final RenderType[] LAYERS = {
			NeonPipelines.PAINT_LAYER, NeonPipelines.PAINT_OCCLUDED_LAYER, NeonPipelines.LIGHT_LAYER, NeonPipelines.LIGHT_OCCLUDED_LAYER};

	private final SubmitNodeCollector submits;
	private final PoseStack pose;
	private final Vec3 eye;
	private final FloatArrayList[] xyz = new FloatArrayList[LAYERS.length];
	private final IntArrayList[] colors = new IntArrayList[LAYERS.length];

	Quads(SubmitNodeCollector submits, PoseStack pose, Vec3 eye) {
		this.submits = submits;
		this.pose = pose;
		this.eye = eye;
		for (int i = 0; i < LAYERS.length; i++) {
			xyz[i] = new FloatArrayList();
			colors[i] = new IntArrayList();
		}
	}

	@Override
	public Vec3 eye() {
		return eye;
	}

	@Override
	public NeonMesher.QuadSink sink(boolean additive, boolean occluded) {
		int i = (additive ? 2 : 0) + (occluded ? 1 : 0);
		FloatArrayList points = xyz[i];
		IntArrayList argb = colors[i];
		return (x, y, z, color) -> {
			points.add(x);
			points.add(y);
			points.add(z);
			argb.add(color);
		};
	}

	@Override
	public void flush() {
		for (int i = 0; i < LAYERS.length; i++) {
			int n = colors[i].size();
			if (n == 0) continue;
			float[] points = xyz[i].toFloatArray();
			int[] argb = colors[i].toIntArray();
			submits.submitCustomGeometry(pose, LAYERS[i], (at, buffer) -> {
				for (int v = 0; v < n; v++) {
					buffer.addVertex(at.pose(), points[v * 3], points[v * 3 + 1], points[v * 3 + 2]).setColor(argb[v]);
				}
			});
			xyz[i].clear();
			colors[i].clear();
		}
	}
}
