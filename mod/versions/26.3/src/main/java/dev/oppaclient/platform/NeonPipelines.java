package dev.oppaclient.platform;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.oppaclient.OppaClient;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class NeonPipelines {
	private static RenderPipeline world(String name, BlendFunction blend, CompareOp depth) {
		return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
				.withLocation(OppaClient.id("pipeline/" + name))
				.withVertexShader("core/rendertype_lightning")
				.withFragmentShader("core/rendertype_lightning")
				.withColorTargetState(new ColorTargetState(blend))
				.withDepthStencilState(new DepthStencilState(depth, false))
				.withCull(false)
				.withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
				.withPrimitiveTopology(PrimitiveTopology.QUADS)
				.build());
	}

	public static final RenderPipeline LIGHT = world("light", BlendFunction.LIGHTNING, CompareOp.GREATER_THAN_OR_EQUAL);
	public static final RenderPipeline LIGHT_OCCLUDED = world("light_occluded", BlendFunction.LIGHTNING, CompareOp.LESS_THAN);
	public static final RenderPipeline PAINT = world("paint", BlendFunction.TRANSLUCENT, CompareOp.GREATER_THAN_OR_EQUAL);
	public static final RenderPipeline PAINT_OCCLUDED = world("paint_occluded", BlendFunction.TRANSLUCENT, CompareOp.LESS_THAN);
	public static final RenderPipeline LIGHT_XRAY = world("light_xray", BlendFunction.LIGHTNING, CompareOp.ALWAYS_PASS);

	public static final RenderType LIGHT_LAYER = layer("oppaclient_light", LIGHT);
	public static final RenderType LIGHT_OCCLUDED_LAYER = layer("oppaclient_light_occluded", LIGHT_OCCLUDED);
	public static final RenderType PAINT_LAYER = layer("oppaclient_paint", PAINT);
	public static final RenderType PAINT_OCCLUDED_LAYER = layer("oppaclient_paint_occluded", PAINT_OCCLUDED);
	public static final RenderType LIGHT_XRAY_LAYER = layer("oppaclient_light_xray", LIGHT_XRAY);

	public static final RenderPipeline GUI_FLAT = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
			.withLocation(OppaClient.id("pipeline/gui_flat"))
			.withCull(false)
			.build());

	public static final RenderPipeline GUI_LIGHT = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
			.withLocation(OppaClient.id("pipeline/gui_light"))
			.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
			.withCull(false)
			.build());

	private static RenderType layer(String name, RenderPipeline pipeline) {
		return RenderType.create(name, RenderSetup.builder(pipeline).createRenderSetup());
	}

	public static RenderPipeline[] world() {
		return new RenderPipeline[]{LIGHT, LIGHT_OCCLUDED, PAINT, PAINT_OCCLUDED, LIGHT_XRAY};
	}

	public static void bootstrap() {
	}

	private NeonPipelines() {
	}
}
