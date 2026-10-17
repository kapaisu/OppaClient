package dev.oppaclient.overlay;

import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

public final class OverlaySettings {
	private static final Predicate<OverlayConfig> ALWAYS = c -> true;

	public sealed interface Setting permits Toggle, Range, Choice, Color {
		String label();

		String hint();

		Predicate<OverlayConfig> visible();

		void reset(OverlayConfig config);

		boolean isDefault(OverlayConfig config);
	}

	public record Toggle(String label, String hint, Predicate<OverlayConfig> get, BiConsumer<OverlayConfig, Boolean> set,
						 Predicate<OverlayConfig> visible) implements Setting {
		@Override
		public void reset(OverlayConfig config) {
			set.accept(config, get.test(OverlayConfig.DEFAULTS));
		}

		@Override
		public boolean isDefault(OverlayConfig config) {
			return get.test(config) == get.test(OverlayConfig.DEFAULTS);
		}
	}

	public record Range(String label, String hint, float min, float max, float step, Unit unit,
						ToDoubleFunction<OverlayConfig> get, BiConsumer<OverlayConfig, Float> set,
						Predicate<OverlayConfig> visible) implements Setting {
		public float value(OverlayConfig config) {
			return (float) get.applyAsDouble(config);
		}

		public void apply(OverlayConfig config, float raw) {
			float clamped = Math.clamp(raw, min, max);
			float snapped = min + Math.round((clamped - min) / step) * step;
			set.accept(config, Math.clamp(snapped, min, max));
		}

		public float fraction(OverlayConfig config) {
			return (value(config) - min) / (max - min);
		}

		public String format(float v) {
			return unit.format(v);
		}

		@Override
		public void reset(OverlayConfig config) {
			set.accept(config, value(OverlayConfig.DEFAULTS));
		}

		@Override
		public boolean isDefault(OverlayConfig config) {
			return Math.abs(value(config) - value(OverlayConfig.DEFAULTS)) < step * 0.5f;
		}
	}

	public record Choice<E extends Enum<E> & Labeled>(String label, String hint, E[] values,
													   Function<OverlayConfig, E> get, BiConsumer<OverlayConfig, E> set,
													   Predicate<OverlayConfig> visible) implements Setting {
		public int index(OverlayConfig config) {
			return get.apply(config).ordinal();
		}

		public void select(OverlayConfig config, int index) {
			set.accept(config, values[Math.floorMod(index, values.length)]);
		}

		@Override
		public void reset(OverlayConfig config) {
			set.accept(config, get.apply(OverlayConfig.DEFAULTS));
		}

		@Override
		public boolean isDefault(OverlayConfig config) {
			return get.apply(config) == get.apply(OverlayConfig.DEFAULTS);
		}
	}

	public record Color(String label, String hint, ToIntFunction<OverlayConfig> get, ObjIntConsumer<OverlayConfig> set,
						Predicate<OverlayConfig> visible) implements Setting {
		@Override
		public void reset(OverlayConfig config) {
			set.accept(config, get.applyAsInt(OverlayConfig.DEFAULTS));
		}

		@Override
		public boolean isDefault(OverlayConfig config) {
			return get.applyAsInt(config) == get.applyAsInt(OverlayConfig.DEFAULTS);
		}
	}

	public enum Unit {
		PIXELS {
			@Override
			String format(float v) {
				return String.format(Locale.ROOT, "%.2f px", v * 16f);
			}
		},
		PERCENT {
			@Override
			String format(float v) {
				return Math.round(v * 100f) + "%";
			}
		},
		MILLIS {
			@Override
			String format(float v) {
				return Math.round(v * 1000f) + " ms";
			}
		},
		HERTZ {
			@Override
			String format(float v) {
				return String.format(Locale.ROOT, "%.2f Hz", v);
			}
		},
		METERS {
			@Override
			String format(float v) {
				return String.format(Locale.ROOT, "%.1f m", v);
			}
		},
		PER_METER {
			@Override
			String format(float v) {
				return String.format(Locale.ROOT, "%.1f / m", v);
			}
		},
		FACTOR {
			@Override
			String format(float v) {
				return String.format(Locale.ROOT, "%.1fx", v);
			}
		},
		PLAIN {
			@Override
			String format(float v) {
				return String.format(Locale.ROOT, "%.1f", v);
			}
		},
		COUNT {
			@Override
			String format(float v) {
				return Integer.toString(Math.round(v));
			}
		},
		SECONDS {
			@Override
			String format(float v) {
				return String.format(Locale.ROOT, "%.1f s", v);
			}
		},
		CHUNKS {
			@Override
			String format(float v) {
				int n = Math.round(v);
				return n + (n == 1 ? " chunk" : " chunks");
			}
		},
		SPEED {
			@Override
			String format(float v) {
				return String.format(Locale.ROOT, "%.1f m/s", v);
			}
		},
		HEIGHT {
			@Override
			String format(float v) {
				return "Y " + Math.round(v);
			}
		},
		BLOCKS {
			@Override
			String format(float v) {
				return Math.round(v) + " blocks";
			}
		},
		DISTANCE {
			@Override
			String format(float v) {
				return Math.round(v) + " m";
			}
		},
		SCREEN {
			@Override
			String format(float v) {
				return Math.round(v) + " px";
			}
		};

		abstract String format(float v);
	}

	public record Category(String name, String blurb, List<Setting> settings) {}

	public static final Category TUBE = new Category("Tube", "Shape of the light itself", List.of(
			new Range("Thickness", "Width of the lit glass tube at the center of the glow.",
					0.004f, 0.04f, 0.001f, Unit.PIXELS, c -> c.thickness, (c, v) -> c.thickness = v, ALWAYS),
			new Range("Core heat", "How far the hottest part of the discharge washes out toward white.",
					0f, 1f, 0.01f, Unit.PERCENT, c -> c.coreHeat, (c, v) -> c.coreHeat = v, ALWAYS),
			new Range("Halo radius", "How far light scatters away from the tube.",
					0.02f, 0.4f, 0.005f, Unit.PIXELS, c -> c.haloRadius, (c, v) -> c.haloRadius = v, ALWAYS),
			new Range("Halo strength", "Brightness of the scattered light relative to the tube.",
					0f, 1.5f, 0.01f, Unit.PERCENT, c -> c.haloStrength, (c, v) -> c.haloStrength = v, ALWAYS),
			new Range("Falloff", "Higher values pull the halo in tight around the tube.",
					1f, 8f, 0.1f, Unit.PLAIN, c -> c.falloff, (c, v) -> c.falloff = v, ALWAYS),
			new Range("Brightness", "Overall output of the tube and everything it lights.",
					0.2f, 2f, 0.01f, Unit.PERCENT, c -> c.brightness, (c, v) -> c.brightness = v, ALWAYS),
			new Range("Inflate", "Lifts the tube off the block surface.",
					0f, 0.06f, 0.001f, Unit.PIXELS, c -> c.inflate, (c, v) -> c.inflate = v, ALWAYS),
			new Range("Distance scaling", "Keeps the tube readable at range by thickening it with distance.",
					0f, 1f, 0.01f, Unit.PERCENT, c -> c.distanceScaling, (c, v) -> c.distanceScaling = v, ALWAYS)
	));

	public static final Category COLOR = new Category("Color", "What the tube emits", List.of(
			new Choice<>("Source", "Where the tube gets its color from.",
					ColorMode.values(), c -> c.colorMode, (c, v) -> c.colorMode = v, ALWAYS),
			new Color("Primary", "Main tube color.",
					c -> c.primary, (c, v) -> c.primary = v,
					c -> c.colorMode == ColorMode.SOLID || c.colorMode == ColorMode.GRADIENT),
			new Color("Secondary", "The color the gradient flows into.",
					c -> c.secondary, (c, v) -> c.secondary = v, c -> c.colorMode == ColorMode.GRADIENT),
			new Choice<>("Gas", "Real fill gases. Each one glows its own color and burns to its own core.",
					Gas.values(), c -> c.gas, (c, v) -> c.gas = v, c -> c.colorMode == ColorMode.GAS),
			new Range("Flow speed", "How fast color travels along the tube.",
					0f, 2f, 0.01f, Unit.HERTZ, c -> c.flowSpeed, (c, v) -> c.flowSpeed = v,
					c -> c.colorMode == ColorMode.GRADIENT || c.colorMode == ColorMode.SPECTRUM),
			new Range("Flow scale", "How many color cycles fit in one block.",
					0.1f, 4f, 0.1f, Unit.PER_METER, c -> c.flowScale, (c, v) -> c.flowScale = v,
					c -> c.colorMode == ColorMode.GRADIENT || c.colorMode == ColorMode.SPECTRUM)
	));

	public static final Category DISCHARGE = new Category("Discharge", "How the gas behaves when lit", List.of(
			new Choice<>("Strike", "Flicker to life when a target is picked up, like a cold tube starting.",
					Strike.values(), c -> c.strike, (c, v) -> c.strike = v, ALWAYS),
			new Range("Strike time", "How long the tube stutters before it holds.",
					0.1f, 1.2f, 0.01f, Unit.MILLIS, c -> c.strikeTime, (c, v) -> c.strikeTime = v,
					c -> c.strike != Strike.OFF),
			new Range("Flicker", "Unstable current. Low values read as a living tube, high as a dying one.",
					0f, 1f, 0.01f, Unit.PERCENT, c -> c.flicker, (c, v) -> c.flicker = v, ALWAYS),
			new Range("Breathe", "Slow swell and dim of the whole tube.",
					0f, 1f, 0.01f, Unit.PERCENT, c -> c.breathe, (c, v) -> c.breathe = v, ALWAYS),
			new Range("Breathe rate", "Cycles per second.",
					0.1f, 3f, 0.01f, Unit.HERTZ, c -> c.breatheRate, (c, v) -> c.breatheRate = v, c -> c.breathe > 0f),
			new Range("Fade out", "Afterglow when the tube loses its target.",
					0f, 1f, 0.01f, Unit.MILLIS, c -> c.fadeOut, (c, v) -> c.fadeOut = v, ALWAYS),
			new Toggle("Mining charge", "Current builds while you mine. The tube heats, thickens and hums.",
					c -> c.miningCharge, (c, v) -> c.miningCharge = v, ALWAYS),
			new Toggle("Break burst", "A broken block releases its light in one last flash.",
					c -> c.breakBurst, (c, v) -> c.breakBurst = v, ALWAYS)
	));

	public static final Category MOTION = new Category("Motion", "How the outline travels", List.of(
			new Choice<>("Style", "Snap jumps instantly. Glide settles without overshoot. Spring overshoots and rings out.",
					Motion.values(), c -> c.motion, (c, v) -> c.motion = v, ALWAYS),
			new Range("Response", "Natural frequency of the spring. Higher is snappier.",
					1f, 14f, 0.1f, Unit.HERTZ, c -> c.response, (c, v) -> c.response = v, c -> c.motion != Motion.SNAP),
			new Range("Bounce", "How much the spring overshoots before it settles.",
					0f, 0.85f, 0.01f, Unit.PERCENT, c -> c.bounce, (c, v) -> c.bounce = v, c -> c.motion == Motion.SPRING),
			new Range("Glide range", "Targets farther apart than this snap instead of travelling.",
					0.5f, 8f, 0.1f, Unit.METERS, c -> c.glideRange, (c, v) -> c.glideRange = v, c -> c.motion != Motion.SNAP)
	));

	public static final Category LIGHT = new Category("Light", "What the tube lights around it", List.of(
			new Range("Surface spill", "Light the tube throws onto the faces next to it.",
					0f, 1f, 0.01f, Unit.PERCENT, c -> c.spill, (c, v) -> c.spill = v, ALWAYS),
			new Range("Spill reach", "How far across a face the spill travels.",
					0.05f, 0.5f, 0.005f, Unit.PIXELS, c -> c.spillReach, (c, v) -> c.spillReach = v, c -> c.spill > 0f),
			new Range("Fill", "Even tint over every visible face.",
					0f, 0.5f, 0.01f, Unit.PERCENT, c -> c.fill, (c, v) -> c.fill = v, ALWAYS),
			new Range("Through walls", "Strength of the tube where something stands in front of it.",
					0f, 1f, 0.01f, Unit.PERCENT, c -> c.throughWalls, (c, v) -> c.throughWalls = v, ALWAYS),
			new Choice<>("Blending", "Light adds onto the scene like real emission. Paint stays visible on bright surfaces.",
					Blend.values(), c -> c.blend, (c, v) -> c.blend = v, ALWAYS),
			new Range("Shader boost", "Extra output while a shader pack is active, so its bloom picks the tube up.",
					1f, 4f, 0.1f, Unit.FACTOR, c -> c.shaderBoost, (c, v) -> c.shaderBoost = v, ALWAYS)
	));

	public static final Category TARGETS = new Category("Targets", "What gets an outline", List.of(
			new Toggle("Blocks", "Outline the block under the crosshair.",
					c -> c.blocks, (c, v) -> c.blocks = v, ALWAYS),
			new Toggle("Entities", "Outline the mob, item or player under the crosshair.",
					c -> c.entities, (c, v) -> c.entities = v, ALWAYS),
			new Toggle("Separate entity color", "Give entities their own tube color.",
					c -> c.entityOwnColor, (c, v) -> c.entityOwnColor = v, c -> c.entities),
			new Color("Entity color", "Tube color used for entities.",
					c -> c.entityColor, (c, v) -> c.entityColor = v, c -> c.entities && c.entityOwnColor),
			new Toggle("Hide with HUD", "Turn the outline off along with the HUD.",
					c -> c.hideWithHud, (c, v) -> c.hideWithHud = v, ALWAYS)
	));

	public static final List<Category> CATEGORIES = List.of(TUBE, COLOR, DISCHARGE, MOTION, LIGHT, TARGETS);

	private OverlaySettings() {
	}

	static void sanitize(OverlayConfig config) {
		OverlayConfig d = OverlayConfig.DEFAULTS;
		for (Category category : CATEGORIES) {
			for (Setting setting : category.settings()) {
				if (setting instanceof Choice<?> choice && choice.get().apply(config) == null) {
					choice.reset(config);
				} else if (setting instanceof Range range) {
					float v = range.value(config);
					range.apply(config, Float.isFinite(v) ? v : range.value(d));
				}
			}
		}
	}
}
