package org.jmt.mcmt.autotest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.jmt.mcmt.Constants;
import org.jmt.mcmt.ParallelProcessor;

import net.minecraft.server.MinecraftServer;

/**
 * Unattended test mode, enabled with {@code -Dmcmt.autotest=<seconds>}. Off by default and
 * inert unless the property is set.
 * <p>
 * Once the server is running (and, on an integrated server, once the local player has joined)
 * it creates load around spawn, lets the game tick for the given time, logs one
 * {@code MCMT-AUTOTEST RESULT} line with the parallel task counters, then shuts the game down
 * cleanly: the client via {@link ClientShutdown}, a dedicated server via {@code halt}.
 * Test scripts only need to read the log.
 */
public final class AutoTest {

	public static final String PROPERTY = "mcmt.autotest";

	private AutoTest() {}

	private static final List<String> SETUP = List.of(
			"gamemode creative @a",
			"gamerule doMobSpawning false",
			"gamerule doDaylightCycle true",
			"gamerule randomTickSpeed 30",
			"weather thunder 6000",
			"forceload add -40 -40 40 40",
			"fill -8 63 -8 8 63 8 minecraft:farmland",
			"fill -8 64 -8 8 64 8 minecraft:wheat",
			"setblock 10 64 2 minecraft:hopper",
			"setblock 11 64 2 minecraft:furnace",
			"setblock 12 64 2 minecraft:chest");

	private static final String[] SUMMONS = {
			"execute positioned 0 66 12 run summon minecraft:zombie ~ ~ ~ {PersistenceRequired:1b}",
			"execute positioned 5 66 -12 run summon minecraft:cow ~ ~ ~",
			"execute positioned -12 66 5 run summon minecraft:item ~ ~ ~ {Item:{id:\"minecraft:stone\",count:1}}"
	};
	private static final int[] SUMMON_COUNTS = { 100, 50, 50 };

	public static int configuredSeconds() {
		String v = System.getProperty(PROPERTY);
		if (v == null || v.isBlank()) return 0;
		try {
			return Math.max(5, Integer.parseInt(v.trim()));
		} catch (NumberFormatException e) {
			Constants.LOG.warn("Ignoring invalid -D{}={}", PROPERTY, v);
			return 0;
		}
	}

	/** Called from the loader's server-started event. */
	public static void onServerStarted(MinecraftServer server) {
		int seconds = configuredSeconds();
		if (seconds <= 0) return;
		Constants.LOG.warn("MCMT-AUTOTEST enabled: {} s on {} server", seconds, server.isDedicatedServer() ? "dedicated" : "integrated");
		Thread t = new Thread(() -> run(server, seconds), "MCMT AutoTest");
		t.setDaemon(true);
		t.start();
	}

	private static void run(MinecraftServer server, int seconds) {
		try {
			// Integrated server: wait for the local player so player-dependent ticks run.
			if (!server.isDedicatedServer()) {
				long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
				while (server.getPlayerCount() == 0 && System.nanoTime() < deadline) {
					Thread.sleep(250);
				}
				Constants.LOG.warn("MCMT-AUTOTEST player count {}", server.getPlayerCount());
			}
			Thread.sleep(2000);
			onServerThread(server, () -> {
				for (String c : SETUP) command(server, c);
				for (int i = 0; i < SUMMONS.length; i++) {
					for (int n = 0; n < SUMMON_COUNTS[i]; n++) command(server, SUMMONS[i]);
				}
				command(server, "tp @a 0 66 0");
			});
			Constants.LOG.warn("MCMT-AUTOTEST load created, ticking for {} s", seconds);

			for (int elapsed = 0; elapsed < seconds; elapsed += 10) {
				Thread.sleep(TimeUnit.SECONDS.toMillis(Math.min(10, seconds - elapsed)));
				if (!server.isRunning()) {
					Constants.LOG.error("MCMT-AUTOTEST server stopped early");
					return;
				}
				Constants.LOG.warn("MCMT-AUTOTEST progress {}s {}", Math.min(seconds, elapsed + 10), counters(server));
			}

			Constants.LOG.warn("MCMT-AUTOTEST RESULT {} players={}", counters(server), server.getPlayerCount());
			shutdown(server);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Throwable t) {
			Constants.LOG.error("MCMT-AUTOTEST failed", t);
			shutdown(server);
		}
	}

	private static String counters(MinecraftServer server) {
		return "World:" + ParallelProcessor.totalWorlds.get()
				+ " Entity:" + ParallelProcessor.totalEnts.get()
				+ " TE:" + ParallelProcessor.totalTEs.get()
				+ " Env:" + ParallelProcessor.totalEnvs.get()
				+ " mspt:" + String.format(java.util.Locale.ROOT, "%.2f", server.getAverageTickTimeNanos() / 1_000_000.0);
	}

	private static void command(MinecraftServer server, String cmd) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
	}

	private static void onServerThread(MinecraftServer server, Runnable r) throws Exception {
		CompletableFuture<Void> f = new CompletableFuture<>();
		server.execute(() -> {
			try {
				r.run();
				f.complete(null);
			} catch (Throwable t) {
				f.completeExceptionally(t);
			}
		});
		f.get(60, TimeUnit.SECONDS);
	}

	private static void shutdown(MinecraftServer server) {
		Constants.LOG.warn("MCMT-AUTOTEST shutting down");
		if (server.isDedicatedServer()) {
			server.halt(false);
		} else {
			ClientShutdown.stop();
		}
	}
}
