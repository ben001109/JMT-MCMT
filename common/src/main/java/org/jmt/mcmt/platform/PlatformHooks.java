package org.jmt.mcmt.platform;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.server.level.ServerLevel;

/**
 * The only loader-specific behaviour common code may call.
 * One implementation per loader, discovered through {@link java.util.ServiceLoader}.
 */
public interface PlatformHooks {

	String platformName();

	boolean isDevelopmentEnvironment();

	Path configDirectory();

	/**
	 * Fire the loader's "level finished ticking" event. Called on whichever thread
	 * ran {@code ServerLevel.tick}, immediately after it, because the Mixin that
	 * parallelises level ticks skips the loader's own call.
	 */
	void onLevelPostTick(ServerLevel level, BooleanSupplier hasTimeLeft);

	/** Add a named section to crash reports. Implementations may no-op if unsupported. */
	void registerCrashReportSection(String name, Supplier<String> content);
}
