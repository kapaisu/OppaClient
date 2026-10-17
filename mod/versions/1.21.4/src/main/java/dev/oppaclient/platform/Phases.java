package dev.oppaclient.platform;

import dev.oppaclient.mixin.RenderLayerInvoker;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.lwjgl.opengl.GL11;

final class Phases extends RenderPhase {
	private static final DepthTest GREATER = new DepthTest(">", GL11.GL_GREATER);

	static final RenderLayer LIGHT = world("oppaclient_light", LIGHTNING_TRANSPARENCY, LEQUAL_DEPTH_TEST);
	static final RenderLayer LIGHT_OCCLUDED = world("oppaclient_light_occluded", LIGHTNING_TRANSPARENCY, GREATER);
	static final RenderLayer PAINT = world("oppaclient_paint", TRANSLUCENT_TRANSPARENCY, LEQUAL_DEPTH_TEST);
	static final RenderLayer PAINT_OCCLUDED = world("oppaclient_paint_occluded", TRANSLUCENT_TRANSPARENCY, GREATER);

	static final RenderLayer GUI_LIGHT = gui("oppaclient_gui_light", LIGHTNING_TRANSPARENCY);
	static final RenderLayer GUI_FLAT = gui("oppaclient_gui_flat", TRANSLUCENT_TRANSPARENCY);

	private Phases() {
		super("oppaclient", () -> {}, () -> {});
	}

	private static RenderLayer gui(String name, Transparency transparency) {
		return RenderLayerInvoker.oppaclient$of(name, VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS, 1 << 12,
				RenderLayer.MultiPhaseParameters.builder()
						.program(POSITION_COLOR_PROGRAM)
						.transparency(transparency)
						.depthTest(LEQUAL_DEPTH_TEST)
						.cull(DISABLE_CULLING)
						.build(false));
	}

	private static RenderLayer world(String name, Transparency transparency, DepthTest depth) {
		return RenderLayerInvoker.oppaclient$of(name, VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS, 1 << 16,
				RenderLayer.MultiPhaseParameters.builder()
						.program(LIGHTNING_PROGRAM)
						.transparency(transparency)
						.depthTest(depth)
						.writeMaskState(COLOR_MASK)
						.cull(DISABLE_CULLING)
						.build(false));
	}
}
