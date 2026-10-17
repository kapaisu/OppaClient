package dev.oppaclient.platform;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.oppaclient.OppaClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormats;

public final class NeonPipelines {
	private static RenderPipeline world(String name, BlendFunction blend, DepthTestFunction depth) {
		return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_SNIPPET)
				.withLocation(OppaClient.id("pipeline/" + name))
				.withVertexShader("core/rendertype_lightning")
				.withFragmentShader("core/rendertype_lightning")
				.withBlend(blend)
				.withDepthTestFunction(depth)
				.withDepthWrite(false)
				.withCull(false)
				.withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
				.build());
	}

	public static final RenderPipeline LIGHT = world("light", BlendFunction.LIGHTNING, DepthTestFunction.LEQUAL_DEPTH_TEST);
	public static final RenderPipeline LIGHT_OCCLUDED = world("light_occluded", BlendFunction.LIGHTNING, DepthTestFunction.GREATER_DEPTH_TEST);
	public static final RenderPipeline PAINT = world("paint", BlendFunction.TRANSLUCENT, DepthTestFunction.LEQUAL_DEPTH_TEST);
	public static final RenderPipeline PAINT_OCCLUDED = world("paint_occluded", BlendFunction.TRANSLUCENT, DepthTestFunction.GREATER_DEPTH_TEST);
	public static final RenderPipeline LIGHT_XRAY = world("light_xray", BlendFunction.LIGHTNING, DepthTestFunction.NO_DEPTH_TEST);

	public static final RenderLayer LIGHT_LAYER = layer("oppaclient_light", LIGHT);
	public static final RenderLayer LIGHT_OCCLUDED_LAYER = layer("oppaclient_light_occluded", LIGHT_OCCLUDED);
	public static final RenderLayer PAINT_LAYER = layer("oppaclient_paint", PAINT);
	public static final RenderLayer PAINT_OCCLUDED_LAYER = layer("oppaclient_paint_occluded", PAINT_OCCLUDED);
	public static final RenderLayer LIGHT_XRAY_LAYER = layer("oppaclient_light_xray", LIGHT_XRAY);

	public static final RenderPipeline GUI_FLAT = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
			.withLocation(OppaClient.id("pipeline/gui_flat"))
			.withCull(false)
			.build());

	public static final RenderPipeline GUI_LIGHT = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
			.withLocation(OppaClient.id("pipeline/gui_light"))
			.withBlend(BlendFunction.LIGHTNING)
			.withCull(false)
			.build());

	private static RenderLayer layer(String name, RenderPipeline pipeline) {
		return RenderLayer.of(name, 1 << 16, pipeline, RenderLayer.MultiPhaseParameters.builder().build(false));
	}

	public static RenderPipeline[] world() {
		return new RenderPipeline[]{LIGHT, LIGHT_OCCLUDED, PAINT, PAINT_OCCLUDED, LIGHT_XRAY};
	}

	public static void bootstrap() {
	}

	private NeonPipelines() {
	}
}
