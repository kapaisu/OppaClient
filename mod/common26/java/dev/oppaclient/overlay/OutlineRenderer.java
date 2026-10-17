package dev.oppaclient.overlay;

import dev.oppaclient.mixin.GameRendererInvoker;
import dev.oppaclient.mixin.InteractionManagerAccessor;
import dev.oppaclient.platform.Platform;
import dev.oppaclient.platform.WorldQuads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

public final class OutlineRenderer {
	private static final float CROSSFADE = 0.09f;
	private static final float FADE_IN = 0.06f;
	private static final float BURST_TIME = 0.38f;

	private final NeonMesher mesher = new NeonMesher();
	private final Look look = new Look();
	private final List<Glow> glows = new ArrayList<>();
	private final Map<VoxelShape, EdgeSet> edgeCache = new WeakHashMap<>();
	private Glow current;
	private double lastFrame = Double.NaN;
	private long seeds = 0x6F707061L;

	public boolean hidesVanillaOutline() {
		OverlayConfig config = OverlayConfig.get();
		return config.enabled && config.blocks;
	}

	public void render(WorldQuads context) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel world = client.level;
		if (world == null || client.player == null) {
			glows.clear();
			current = null;
			return;
		}
		OverlayConfig config = OverlayConfig.get();
		double now = Util.getNanos() / 1.0e9;
		float dt = Double.isNaN(lastFrame) ? 0f : (float) Math.min(0.1, now - lastFrame);
		lastFrame = now;
		float tickDelta = Platform.tickProgress();

		checkBroken(world, config, now);
		Target target = findTarget(client, world, config, tickDelta);
		retarget(target, config, now);
		if (current != null && current.entity != null) {
			current.anchor = entityBox(current.entity, tickDelta);
		}
		step(dt, config);

		if (glows.isEmpty()) {
			return;
		}
		draw(context, client, config, now);
	}

	private record Target(Object key, VoxelShape shape, AABB anchor, Entity entity, int blockColor) {
		EdgeSet edges(Map<VoxelShape, EdgeSet> cache) {
			return shape == null ? EdgeSet.CUBE : cache.computeIfAbsent(shape, EdgeSet::of);
		}
	}

	private Target findTarget(Minecraft client, ClientLevel world, OverlayConfig config, float tickDelta) {
		if (!config.enabled || (config.hideWithHud && Platform.hudHidden())) {
			return null;
		}
		HitResult hit = client.hitResult;
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK && config.blocks) {
			if (!((GameRendererInvoker) client.gameRenderer).oppaclient$shouldRenderBlockOutline()) {
				return null;
			}
			BlockPos pos = blockHit.getBlockPos();
			BlockState state = world.getBlockState(pos);
			if (state.isAir() || !world.getWorldBorder().isWithinBounds(pos)) {
				return null;
			}
			Entity viewer = client.getCameraEntity() != null ? client.getCameraEntity() : client.player;
			VoxelShape shape = state.getShape(world, pos, CollisionContext.of(viewer));
			if (shape.isEmpty()) {
				return null;
			}
			AABB anchor = shape.bounds().move(pos);
			int blockColor = config.colorMode == ColorMode.BLOCK ? Colors.asLight(state.getMapColor(world, pos).col) : 0;
			return new Target(new BlockKey(pos.immutable(), shape), shape, anchor, null, blockColor);
		}
		if (hit instanceof EntityHitResult entityHit && hit.getType() == HitResult.Type.ENTITY && config.entities) {
			Entity entity = entityHit.getEntity();
			return new Target(entity.getId(), null, entityBox(entity, tickDelta), entity, 0);
		}
		return null;
	}

	private record BlockKey(BlockPos pos, VoxelShape shape) {
		@Override
		public boolean equals(Object o) {
			return o instanceof BlockKey other && pos.equals(other.pos) && shape == other.shape;
		}

		@Override
		public int hashCode() {
			return pos.hashCode() * 31 + System.identityHashCode(shape);
		}
	}

	private static AABB entityBox(Entity entity, float tickDelta) {
		Vec3 lerped = entity.getPosition(tickDelta);
		Vec3 actual = Platform.entityPos(entity);
		return entity.getBoundingBox().move(lerped.x - actual.x, lerped.y - actual.y, lerped.z - actual.z);
	}

	private void retarget(Target target, OverlayConfig config, double now) {
		if (target == null) {
			if (current != null) {
				current.fadeTo(0f, config.fadeOut);
				current = null;
			}
			return;
		}
		if (current != null && Objects.equals(current.key, target.key())) {
			if (current.entity == null) {
				current.anchor = target.anchor();
			}
			current.blockColor = target.blockColor();
			return;
		}

		boolean anythingLit = false;
		for (Glow glow : glows) {
			if (glow.burst < 0 && glow.alpha > 0.05f) {
				anythingLit = true;
				break;
			}
		}

		Glow previous = current;
		Glow next = null;
		for (Glow glow : glows) {
			if (glow.burst < 0 && glow != previous && Objects.equals(glow.key, target.key())) {
				next = glow;
				break;
			}
		}
		if (next == null) {
			next = new Glow(target.key(), target.edges(edgeCache), target.anchor(), target.entity(), seeds++);
			glows.add(next);
		}
		next.blockColor = target.blockColor();
		next.anchor = target.anchor();

		boolean glide = previous != null && config.motion != Motion.SNAP
				&& center(previous.display()).distanceTo(center(target.anchor())) <= config.glideRange;
		if (glide) {
			next.launch(previous.display(), previous);
			next.fadeTo(1f, CROSSFADE);
			previous.follow(next.anchor);
			previous.fadeTo(0f, CROSSFADE);
		} else {
			next.settle();
			if (previous != null) {
				previous.fadeTo(0f, config.fadeOut);
			}
			if (next.alpha <= 0.001f) {
				next.alpha = 0f;
			}
			next.fadeTo(1f, FADE_IN);
		}

		boolean strike = switch (config.strike) {
			case OFF -> false;
			case ON_ACQUIRE -> !anythingLit;
			case ALWAYS -> true;
		};
		if (strike) {
			next.discharge.strike(now, config.strikeTime);
			next.alpha = 1f;
		}
		current = next;
	}

	private void checkBroken(ClientLevel world, OverlayConfig config, double now) {
		if (current == null || !(current.key instanceof BlockKey key)) {
			return;
		}
		BlockState state = world.getBlockState(key.pos());
		if (!state.isAir() && !state.getShape(world, key.pos()).isEmpty()) {
			return;
		}
		if (config.breakBurst && config.enabled) {
			Glow burst = new Glow(null, current.set, current.display(), null, seeds++);
			burst.burst = now;
			burst.blockColor = current.blockColor;
			burst.alpha = 1f;
			glows.add(burst);
			glows.remove(current);
		} else {
			current.fadeTo(0f, config.fadeOut);
		}
		current = null;
	}

	private void step(float dt, OverlayConfig config) {
		float frequency = config.motion == Motion.SNAP ? 30f : config.response;
		float damping = config.motion == Motion.SPRING ? 1f - config.bounce : 1f;
		for (int i = glows.size() - 1; i >= 0; i--) {
			Glow glow = glows.get(i);
			glow.step(dt, frequency, damping);
			if (glow != current && glow.alpha <= 0.001f && glow.alphaTarget <= 0f) {
				glows.remove(i);
			}
		}
	}

	private void draw(WorldQuads context, Minecraft client, OverlayConfig config, double now) {
		Vec3 eye = context.eye();
		boolean additive = config.blend == Blend.ADDITIVE;
		float boost = IrisCompat.shaderPackActive() ? config.shaderBoost : 1f;

		float charge = 0f;
		BlockPos chargedPos = null;
		if (config.miningCharge && client.gameMode != null && client.gameMode.isDestroying()) {
			InteractionManagerAccessor manager = (InteractionManagerAccessor) client.gameMode;
			charge = Math.clamp(manager.oppaclient$breakingProgress(), 0f, 1f);
			chargedPos = manager.oppaclient$breakingPos();
		}

		NeonMesher.QuadSink sink = context.sink(additive, false);

		for (Glow glow : glows) {
			float glowCharge = chargedPos != null && glow == current && glow.key instanceof BlockKey key
					&& key.pos().equals(chargedPos) ? charge : 0f;
			NeonMesher.Placement at = place(glow, config, eye, now, glowCharge, boost);
			if (at == null) {
				continue;
			}
			if (glow.burst < 0) {
				mesher.fill(glow.set, look, at, config.fill, sink);
				mesher.spill(glow.set, look, at, config.spill * (1f + glowCharge * 0.8f), config.spillReach, sink);
			}
			mesher.tubes(glow.set, look, at, NeonMesher.Edges.FRONT, sink);
		}

		if (config.throughWalls > 0f) {
			NeonMesher.QuadSink hiddenSink = context.sink(additive, true);
			for (Glow glow : glows) {
				if (glow.burst >= 0) {
					continue;
				}
				NeonMesher.Placement at = place(glow, config, eye, now, 0f, boost);
				if (at == null) {
					continue;
				}
				at.envelope *= config.throughWalls;
				at.pull = 0f;
				mesher.tubes(glow.set, look, at, NeonMesher.Edges.ALL, hiddenSink);
			}
		}

		context.flush();
	}

	private NeonMesher.Placement place(Glow glow, OverlayConfig config, Vec3 eye, double now, float charge, float boost) {
		float envelope = glow.alpha * glow.discharge.level(now, config);
		AABB box = glow.display();
		float widthScale = 1f;

		if (glow.burst >= 0) {
			float e = (float) ((now - glow.burst) / BURST_TIME);
			if (e >= 1f) {
				glow.alpha = 0f;
				glow.alphaTarget = 0f;
				return null;
			}
			float grow = e * 0.14f;
			box = box.inflate(grow);
			envelope = (1f - e) * (1f - e) * 1.6f;
			widthScale = 1f + 1.4f * e;
		}
		if (envelope <= 0.002f) {
			return null;
		}

		Vec3 middle = center(box);
		double distance = middle.distanceTo(eye);
		float far = (float) Math.max(1.0, distance / 4.0);
		widthScale *= 1f + (far - 1f) * config.distanceScaling;

		float thickness = config.thickness * (1f + charge * 0.6f);
		float heat = Math.min(1f, config.coreHeat + charge * 0.5f);
		look.shape(thickness, config.haloRadius, config.haloStrength, config.falloff, heat,
				config.brightness * boost * (1f + charge * 0.5f));
		look.colors(config, now);
		if (glow.entity != null && config.entityOwnColor) {
			look.solid(config.entityColor);
		} else if (config.colorMode == ColorMode.BLOCK) {
			look.solid(glow.blockColor != 0 ? glow.blockColor : config.primary);
		}

		NeonMesher.Placement at = glow.placement;
		at.eyeX = eye.x;
		at.eyeY = eye.y;
		at.eyeZ = eye.z;
		at.envelope = envelope;
		at.width = widthScale;
		at.inflate = config.inflate;
		at.pull = Math.min(config.haloRadius * widthScale * 1.2f + 0.02f, 0.6f);

		if (charge > 0f) {
			float j = charge * charge * 0.006f;
			double t = now * 67.0;
			box = box.move(Math.sin(t) * j, Math.sin(t * 1.31 + 1.7) * j, Math.sin(t * 0.87 + 4.1) * j);
		}
		at.map(glow.set.bounds, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
		return at;
	}

	private static Vec3 center(AABB box) {
		return new Vec3((box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5);
	}

	private static final class Glow {
		final Object key;
		final EdgeSet set;
		final Entity entity;
		final Discharge discharge;
		final NeonMesher.Placement placement = new NeonMesher.Placement();
		AABB anchor;
		AABB followTarget;
		final Spring[] offset = {new Spring(), new Spring(), new Spring(), new Spring(), new Spring(), new Spring()};
		float alpha;
		float alphaTarget = 1f;
		float fadeTime = FADE_IN;
		int blockColor;
		double burst = -1;

		Glow(Object key, EdgeSet set, AABB anchor, Entity entity, long seed) {
			this.key = key;
			this.set = set;
			this.anchor = anchor;
			this.entity = entity;
			this.discharge = new Discharge(seed);
		}

		AABB display() {
			return new AABB(anchor.minX + offset[0].value, anchor.minY + offset[1].value, anchor.minZ + offset[2].value,
					anchor.maxX + offset[3].value, anchor.maxY + offset[4].value, anchor.maxZ + offset[5].value);
		}

		void launch(AABB from, Glow source) {
			followTarget = null;
			double[] start = {from.minX - anchor.minX, from.minY - anchor.minY, from.minZ - anchor.minZ,
					from.maxX - anchor.maxX, from.maxY - anchor.maxY, from.maxZ - anchor.maxZ};
			for (int i = 0; i < 6; i++) {
				offset[i].value = (float) start[i];
				offset[i].velocity = source.offset[i].velocity;
				offset[i].target = 0f;
			}
		}

		void settle() {
			followTarget = null;
			for (Spring spring : offset) {
				spring.snap(0f);
			}
		}

		void follow(AABB target) {
			followTarget = target;
		}

		void fadeTo(float to, float seconds) {
			alphaTarget = to;
			fadeTime = Math.max(seconds, 0.001f);
		}

		void step(float dt, float frequency, float damping) {
			if (followTarget != null) {
				offset[0].target = (float) (followTarget.minX - anchor.minX);
				offset[1].target = (float) (followTarget.minY - anchor.minY);
				offset[2].target = (float) (followTarget.minZ - anchor.minZ);
				offset[3].target = (float) (followTarget.maxX - anchor.maxX);
				offset[4].target = (float) (followTarget.maxY - anchor.maxY);
				offset[5].target = (float) (followTarget.maxZ - anchor.maxZ);
			}
			for (Spring spring : offset) {
				spring.step(dt, frequency, damping);
			}
			float rate = dt / fadeTime;
			if (alpha < alphaTarget) {
				alpha = Math.min(alphaTarget, alpha + rate);
			} else if (alpha > alphaTarget) {
				alpha = Math.max(alphaTarget, alpha - rate);
			}
		}
	}
}
