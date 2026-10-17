package dev.oppaclient.platform;

import dev.oppaclient.overlay.Canvas;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;

import java.util.Arrays;

public final class GfxImpl implements Gfx {
	private final DrawContext ctx;

	public GfxImpl(DrawContext ctx) {
		this.ctx = ctx;
	}

	@Override
	public void fill(int x0, int y0, int x1, int y1, int argb) {
		ctx.fill(x0, y0, x1, y1, argb);
	}

	@Override
	public void push() {
		ctx.getMatrices().pushMatrix();
	}

	@Override
	public void pop() {
		ctx.getMatrices().popMatrix();
	}

	@Override
	public void translate(float x, float y) {
		ctx.getMatrices().translate(x, y);
	}

	@Override
	public void scale(float x, float y) {
		ctx.getMatrices().scale(x, y);
	}

	@Override
	public void blit(Identifier texture, int x, int y, int w, int h, float u, float v, int textureW, int textureH, int argb) {
		ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, w, h, textureW, textureH, argb);
	}

	@Override
	public void text(Text text, int x, int y, int argb, boolean shadow) {
		ctx.drawText(MinecraftClient.getInstance().textRenderer, text, x, y, argb, shadow);
	}

	@Override
	public void wrapped(Text text, int x, int y, int width, int maxLines, int lineHeight, int argb) {
		var font = MinecraftClient.getInstance().textRenderer;
		var lines = font.wrapLines(text, width);
		for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
			ctx.drawText(font, lines.get(i), x, y + i * lineHeight, argb, false);
		}
	}

	@Override
	public void item(ItemStack stack, int x, int y) {
		ctx.drawItem(stack, x, y);
	}

	@Override
	public void effect(StatusEffectInstance effect, int x, int y, int size) {
		ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, InGameHud.getEffectTexture(effect.getEffectType()), x, y, size, size);
	}

	@Override
	public void scissor(int x0, int y0, int x1, int y1) {
		ctx.enableScissor(x0, y0, x1, y1);
	}

	@Override
	public void endScissor() {
		ctx.disableScissor();
	}

	@Override
	public void canvas(Canvas canvas, int x, int y, int w, int h) {
		int count = canvas.count();
		if (count == 0) return;
		ScreenRect bounds = new ScreenRect(x, y, Math.max(1, w), Math.max(1, h));
		ScreenRect scissor = ctx.scissorStack.peekLast();
		ScreenRect onScreen = bounds.transformEachVertex(ctx.getMatrices());
		ScreenRect clipped = scissor != null ? scissor.intersection(onScreen) : onScreen;
		if (clipped != null) {
			ctx.state.addSimpleElement(new Element(canvas.additive() ? NeonPipelines.GUI_LIGHT : NeonPipelines.GUI_FLAT,
					new Matrix3x2f(ctx.getMatrices()), Arrays.copyOf(canvas.xy(), count * 2),
					Arrays.copyOf(canvas.colors(), count), scissor, clipped));
		}
		canvas.clear();
	}

	private record Element(com.mojang.blaze3d.pipeline.RenderPipeline pipeline, Matrix3x2f pose, float[] xy, int[] colors,
						   ScreenRect scissorArea, ScreenRect bounds) implements SimpleGuiElementRenderState {
		@Override
		public void setupVertices(VertexConsumer vertices) {
			for (int i = 0; i < colors.length; i++) {
				vertices.vertex(pose, xy[i * 2], xy[i * 2 + 1]).color(colors[i]);
			}
		}

		@Override
		public TextureSetup textureSetup() {
			return TextureSetup.empty();
		}
	}
}
