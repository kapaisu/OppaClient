package dev.oppaclient.platform;

import dev.oppaclient.overlay.NeonMesher;
import net.minecraft.util.math.Vec3d;

public interface WorldQuads {
	Vec3d eye();

	NeonMesher.QuadSink sink(boolean additive, boolean occluded);

	void flush();
}
