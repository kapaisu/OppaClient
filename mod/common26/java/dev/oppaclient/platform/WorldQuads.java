package dev.oppaclient.platform;

import dev.oppaclient.overlay.NeonMesher;
import net.minecraft.world.phys.Vec3;

public interface WorldQuads {
	Vec3 eye();

	NeonMesher.QuadSink sink(boolean additive, boolean occluded);

	void flush();
}
