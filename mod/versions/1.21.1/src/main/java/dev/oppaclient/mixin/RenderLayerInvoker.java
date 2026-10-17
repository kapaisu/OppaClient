package dev.oppaclient.mixin;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderLayer.class)
public interface RenderLayerInvoker {
	@Invoker("of")
	static RenderLayer.MultiPhase oppaclient$of(String name, VertexFormat format, VertexFormat.DrawMode mode, int size,
			RenderLayer.MultiPhaseParameters parameters) {
		throw new AssertionError();
	}
}
