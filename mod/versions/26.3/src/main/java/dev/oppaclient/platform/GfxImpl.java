package dev.oppaclient.platform;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.oppaclient.overlay.Canvas;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

public final class GfxImpl implements Gfx {
	private final GuiGraphicsExtractor ctx;

	public GfxImpl(GuiGraphicsExtractor ctx) {
		this.ctx = ctx;
	}

	@Override
	public void fill(int x0, int y0, int x1, int y1, int argb) {
		ctx.fill(x0, y0, x1, y1, argb);
	}

	@Override
	public void push() {
		ctx.pose().pushMatrix();
	}

	@Override
	public void pop() {
		ctx.pose().popMatrix();
	}

	@Override
	public void translate(float x, float y) {
		ctx.pose().translate(x, y);
	}

	@Override
	public void scale(float x, float y) {
		ctx.pose().scale(x, y);
	}

	@Override
	public void blit(Identifier texture, int x, int y, int w, int h, float u, float v, int textureW, int textureH, int argb) {
		ctx.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, w, h, textureW, textureH, argb);
	}

	@Override
	public void text(Component text, int x, int y, int argb, boolean shadow) {
		ctx.text(Minecraft.getInstance().font, text, x, y, argb, shadow);
	}

	@Override
	public void wrapped(Component text, int x, int y, int width, int maxLines, int lineHeight, int argb) {
		var font = Minecraft.getInstance().font;
		var lines = font.split(text, width);
		for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
			ctx.text(font, lines.get(i), x, y + i * lineHeight, argb, false);
		}
	}

	@Override
	public void item(ItemStack stack, int x, int y) {
		ctx.item(stack, x, y);
	}

	@Override
	public void effect(MobEffectInstance effect, int x, int y, int size) {
		ctx.blitSprite(RenderPipelines.GUI_TEXTURED, net.minecraft.client.gui.Hud.getMobEffectSprite(effect.getEffect()), x, y, size, size);
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
		ScreenRectangle bounds = new ScreenRectangle(x, y, Math.max(1, w), Math.max(1, h));
		ScreenRectangle scissor = ctx.scissorStack.peek();
		ScreenRectangle onScreen = bounds.transformMaxBounds(ctx.pose());
		ScreenRectangle clipped = scissor != null ? scissor.intersection(onScreen) : onScreen;
		if (clipped != null) {
			ctx.guiRenderState.addGuiElement(new Element(canvas.additive() ? NeonPipelines.GUI_LIGHT : NeonPipelines.GUI_FLAT,
					new Matrix3x2f(ctx.pose()), Arrays.copyOf(canvas.xy(), count * 2),
					Arrays.copyOf(canvas.colors(), count), scissor, clipped));
		}
		canvas.clear();
	}

	private record Element(com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline, Matrix3x2fc pose, float[] xy, int[] colors,
						   @Nullable ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
		@Override
		public void buildVertices(VertexConsumer vertices) {
			for (int i = 0; i < colors.length; i++) {
				vertices.addVertexWith2DPose(pose, xy[i * 2], xy[i * 2 + 1]).setColor(colors[i]);
			}
		}

		@Override
		public TextureSetup textureSetup() {
			return TextureSetup.noTexture();
		}
	}
}
