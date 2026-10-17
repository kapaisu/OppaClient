package dev.oppaclient.overlay;

import dev.oppaclient.OppaClient;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

public final class IrisCompat {
	private static Object api;
	private static Method shaderPackInUse;

	private IrisCompat() {
	}

	public static void init(Object... pipelines) {
		if (!FabricLoader.getInstance().isModLoaded("iris")) {
			return;
		}
		try {
			Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Class<?> programClass = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
			api = apiClass.getMethod("getInstance").invoke(null);
			shaderPackInUse = apiClass.getMethod("isShaderPackInUse");
			if (pipelines.length == 0) {
				return;
			}
			@SuppressWarnings({"unchecked", "rawtypes"})
			Object basic = Enum.valueOf((Class<? extends Enum>) programClass, "BASIC");
			Method assign = null;
			for (Method m : apiClass.getMethods()) {
				if (m.getName().equals("assignPipeline") && m.getParameterCount() == 2 && m.getParameterTypes()[1] == programClass) assign = m;
			}
			if (assign == null) throw new NoSuchMethodException("assignPipeline");
			for (Object pipeline : pipelines) {
				assign.invoke(api, pipeline, basic);
			}
			OppaClient.LOG.info("Registered {} pipelines with Iris", pipelines.length);
		} catch (ReflectiveOperationException | LinkageError e) {
			OppaClient.LOG.warn("Iris is present but its pipeline API is not; outlines may not draw under shader packs", e);
			api = null;
			shaderPackInUse = null;
		}
	}

	public static boolean shaderPackActive() {
		if (shaderPackInUse == null) {
			return false;
		}
		try {
			return (boolean) shaderPackInUse.invoke(api);
		} catch (ReflectiveOperationException e) {
			shaderPackInUse = null;
			return false;
		}
	}
}
