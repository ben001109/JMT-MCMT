package org.jmt.mcmt;

import org.jmt.mcmt.commands.StatsCommand;
import org.jmt.mcmt.config.ConfigStore;
import org.jmt.mcmt.config.MCMTConfig;
import org.jmt.mcmt.platform.Platform;
import org.jmt.mcmt.serdes.SerDesRegistry;

import net.minecraft.server.MinecraftServer;

/** Loader-independent lifecycle. Each loader calls these from its own events. */
public final class MCMTCommon {

	private MCMTCommon() {}

	private static ConfigStore store;

	/** Called once during mod construction with the loader's config store. */
	public static void init(ConfigStore configStore) {
		store = configStore;
		Constants.LOG.info("MCMT {} initialising on {}", MCMTVersion.get(), Platform.HOOKS.platformName());
		MCMTConfig.bake(store);
		SerDesRegistry.init();
		StatsCommand.runDataThread();
		Platform.HOOKS.registerCrashReportSection("MCMT", ParallelProcessor::populateCrashReport);
	}

	/** Re-read the store (after the loader reports a config reload). */
	public static void reloadConfig() {
		if (store != null) MCMTConfig.bake(store);
	}

	public static void saveConfig() {
		if (store != null) MCMTConfig.save(store);
	}

	public static void onServerStarting(MinecraftServer server) {
		Constants.LOG.info("MCMT setting up thread pool...");
		StatsCommand.setServer(server);
		StatsCommand.resetAll();
		ParallelProcessor.setupThreadpool(MCMTConfig.getParallelism());
	}

	public static void onServerStopped(MinecraftServer server) {
		ParallelProcessor.shutdownThreadpool();
	}
}
