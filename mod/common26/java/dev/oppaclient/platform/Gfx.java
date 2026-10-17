package dev.oppaclient.platform;

import dev.oppaclient.overlay.Canvas;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

public interface Gfx {
	void fill(int x0, int y0, int x1, int y1, int argb);

	void push();

	void pop();

	void translate(float x, float y);

	void scale(float x, float y);

	void blit(Identifier texture, int x, int y, int w, int h, float u, float v, int textureW, int textureH, int argb);

	void text(Component text, int x, int y, int argb, boolean shadow);

	void item(ItemStack stack, int x, int y);

	void wrapped(Component text, int x, int y, int width, int maxLines, int lineHeight, int argb);

	void effect(MobEffectInstance effect, int x, int y, int size);

	void scissor(int x0, int y0, int x1, int y1);

	void endScissor();

	void canvas(Canvas canvas, int x, int y, int w, int h);
}
