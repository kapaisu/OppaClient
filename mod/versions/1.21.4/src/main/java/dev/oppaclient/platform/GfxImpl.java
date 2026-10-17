package dev.oppaclient.platform;

import dev.oppaclient.overlay.Canvas;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

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
		ctx.getMatrices().push();
	}

	@Override
	public void pop() {
		ctx.getMatrices().pop();
	}

	@Override
	public void translate(float x, float y) {
		ctx.getMatrices().translate(x, y, 0);
	}

	@Override
	public void scale(float x, float y) {
		ctx.getMatrices().scale(x, y, 1);
	}

	@Override
	public void blit(Identifier texture, int x, int y, int w, int h, float u, float v, int textureW, int textureH, int argb) {
		ctx.drawTexture(RenderLayer::getGuiTextured, texture, x, y, u, v, w, h, w, h, textureW, textureH, argb);
	}

	@Override
	public void text(Text text, int x, int y, int argb, boolean shadow) {
		ctx.drawText(MinecraftClient.getInstance().textRenderer, text, x, y, argb, shadow);
	}

	@Override
	public void item(ItemStack stack, int x, int y) {
		ctx.drawItem(stack, x, y);
	}

	@Override
	public void effect(StatusEffectInstance effect, int x, int y, int size) {
		Sprite sprite = MinecraftClient.getInstance().getStatusEffectSpriteManager().getSprite(effect.getEffectType());
		ctx.drawSpriteStretched(RenderLayer::getGuiTextured, sprite, x, y, size, size);
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
		float[] xy = canvas.xy();
		int[] colors = canvas.colors();
		Matrix4f pose = new Matrix4f(ctx.getMatrices().peek().getPositionMatrix());
		RenderLayer layer = canvas.additive() ? Phases.GUI_LIGHT : Phases.GUI_FLAT;
		ctx.draw(consumers -> {
			VertexConsumer buffer = consumers.getBuffer(layer);
			for (int i = 0; i < count; i++) {
				buffer.vertex(pose, xy[i * 2], xy[i * 2 + 1], 0).color(colors[i]);
			}
		});
		ctx.draw();
		canvas.clear();
	}
}
