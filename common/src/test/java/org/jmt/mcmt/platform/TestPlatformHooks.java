package org.jmt.mcmt.platform;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.server.level.ServerLevel;

public class TestPlatformHooks implements PlatformHooks {
	private static final Path DIR;
	static {
		try {
			DIR = Files.createTempDirectory("mcmt-test-config");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override public String platformName() { return "test"; }
	@Override public boolean isDevelopmentEnvironment() { return true; }
	@Override public Path configDirectory() { return DIR; }
	@Override public void onLevelPostTick(ServerLevel level, BooleanSupplier hasTimeLeft) {}
	@Override public void registerCrashReportSection(String name, Supplier<String> content) {}
}
