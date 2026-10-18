package org.jmt.mcmt.fabric;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jmt.mcmt.Constants;
import org.jmt.mcmt.MCMTCommon;
import org.jmt.mcmt.commands.ConfigCommand;
import org.jmt.mcmt.config.MCMTConfig;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

public class MCMTFabric implements ModInitializer {

	@Override
	public void onInitialize() {
		FabricConfigStore store = new FabricConfigStore(
				FabricLoader.getInstance().getConfigDir().resolve(Constants.MOD_ID + "-common.toml"));
		store.writeDefaultsIfMissing(defaults());
		MCMTCommon.init(store);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ConfigCommand.register(dispatcher));
		ServerLifecycleEvents.SERVER_STARTING.register(MCMTCommon::onServerStarting);
		ServerLifecycleEvents.SERVER_STARTED.register(MCMTCommon::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPED.register(MCMTCommon::onServerStopped);
	}

	private static Map<String, Object> defaults() {
		Map<String, Object> d = new LinkedHashMap<>();
		d.put(MCMTConfig.K_DISABLED, false);
		d.put(MCMTConfig.K_PARAMAX, -1);
		d.put(MCMTConfig.K_PARAMAXMODE, "Standard");
		d.put(MCMTConfig.K_DISABLE_WORLD, false);
		d.put(MCMTConfig.K_DISABLE_WORLD_POSTTICK, false);
		d.put(MCMTConfig.K_DISABLE_MULTICHUNK, false);
		d.put(MCMTConfig.K_DISABLE_ENTITY, false);
		d.put(MCMTConfig.K_DISABLE_TE, false);
		d.put(MCMTConfig.K_CHUNKLOCK_MODDED, true);
		d.put(MCMTConfig.K_TE_WHITELIST, List.of());
		d.put(MCMTConfig.K_TE_BLACKLIST, List.of());
		d.put(MCMTConfig.K_DISABLE_ENV, false);
		d.put(MCMTConfig.K_DISABLE_SCP, false);
		d.put(MCMTConfig.K_CHUNK_TIMEOUT, false);
		d.put(MCMTConfig.K_TIMEOUT_REGEN, false);
		d.put(MCMTConfig.K_BLANK_RETURN, false);
		d.put(MCMTConfig.K_TIMEOUT_COUNT, 5000);
		d.put(MCMTConfig.K_OPS_TRACING, false);
		d.put(MCMTConfig.K_LOGCAP, 720000);
		return d;
	}
}
