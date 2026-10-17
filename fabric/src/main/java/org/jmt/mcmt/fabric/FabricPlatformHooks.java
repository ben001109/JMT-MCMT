package org.jmt.mcmt.fabric;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.jmt.mcmt.Constants;
import org.jmt.mcmt.platform.PlatformHooks;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;

public class FabricPlatformHooks implements PlatformHooks {

	@Override public String platformName() { return "Fabric"; }

	@Override public boolean isDevelopmentEnvironment() { return FabricLoader.getInstance().isDevelopmentEnvironment(); }

	@Override public Path configDirectory() { return FabricLoader.getInstance().getConfigDir(); }

	@Override
	public void onLevelPostTick(ServerLevel level, BooleanSupplier hasTimeLeft) {
		// Fabric API fires ServerTickEvents.END_WORLD_TICK from inside ServerLevel.tick via its own
		// Mixin, so it still runs when MCMT redirects the call. Nothing to re-fire here.
	}

	@Override
	public void registerCrashReportSection(String name, Supplier<String> content) {
		// Fabric Loader has no public API for extra crash report sections.
		Constants.LOG.debug("Crash report section '{}' not registered: unsupported on Fabric", name);
	}
}
