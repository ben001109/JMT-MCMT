package org.jmt.mcmt.neoforge;

import org.jmt.mcmt.Constants;
import org.jmt.mcmt.MCMTCommon;
import org.jmt.mcmt.commands.ConfigCommand;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@Mod(Constants.MOD_ID)
public class MCMTNeoForge {

	private final NeoForgeConfigStore store = new NeoForgeConfigStore();

	public MCMTNeoForge(IEventBus modBus, ModContainer container) {
		container.registerConfig(ModConfig.Type.COMMON, store.spec, Constants.MOD_ID + "-common.toml");
		MCMTCommon.init(store);
		modBus.addListener(this::onConfig);
		NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
		NeoForge.EVENT_BUS.addListener(this::onServerStarting);
		NeoForge.EVENT_BUS.addListener(this::onServerStopped);
	}

	private void onConfig(ModConfigEvent event) {
		if (event.getConfig().getSpec() == store.spec) {
			MCMTCommon.reloadConfig();
		}
	}

	private void onRegisterCommands(RegisterCommandsEvent event) {
		ConfigCommand.register(event.getDispatcher());
	}

	private void onServerStarting(ServerStartingEvent event) {
		MCMTCommon.onServerStarting(event.getServer());
	}

	private void onServerStopped(ServerStoppedEvent event) {
		MCMTCommon.onServerStopped(event.getServer());
	}
}
