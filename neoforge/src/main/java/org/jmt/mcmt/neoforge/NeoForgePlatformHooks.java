package org.jmt.mcmt.neoforge;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.jmt.mcmt.platform.PlatformHooks;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.CrashReportCallables;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.EventHooks;

public class NeoForgePlatformHooks implements PlatformHooks {

	@Override public String platformName() { return "NeoForge"; }

	@Override public boolean isDevelopmentEnvironment() { return !FMLLoader.isProduction(); }

	@Override public Path configDirectory() { return FMLPaths.CONFIGDIR.get(); }

	@Override
	public void onLevelPostTick(ServerLevel level, BooleanSupplier hasTimeLeft) {
		// NeoForge fires LevelTickEvent.Post from MinecraftServer.tickChildren right after
		// ServerLevel.tick. The MCMT Mixin redirects that call, so re-fire it here.
		EventHooks.fireLevelTickPost(level, hasTimeLeft);
	}

	@Override
	public void registerCrashReportSection(String name, Supplier<String> content) {
		CrashReportCallables.registerCrashCallable(name, content);
	}
}
