package org.jmt.mcmt.commands;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.jmt.mcmt.Constants;
import org.jmt.mcmt.ParallelProcessor;
import org.jmt.mcmt.config.MCMTConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;

public class StatsCommand {

	public static LiteralArgumentBuilder<CommandSourceStack> registerStatus(LiteralArgumentBuilder<CommandSourceStack> root) {
		return root.then(Commands.literal("stats")
			.then(Commands.literal("reset").executes(ctx -> {
				resetAll();
				return 1;
			}))
			.executes(ctx -> {
				if (!threadStats) {
					ConfigCommand.success(ctx, "Stat calcs are disabled so stats are out of date");
				}
				StringBuilder sb = new StringBuilder("Current max threads " + mean(maxThreads, liveValues) + " (");
				sb.append("World:").append(mean(maxWorlds, liveValues));
				sb.append(" Entity:").append(mean(maxEntities, liveValues));
				sb.append(" TE:").append(mean(maxTEs, liveValues));
				sb.append(" Env:").append(mean(maxEnvs, liveValues)).append(')');
				ConfigCommand.success(ctx, sb.toString());
				return 1;
			})
			.then(Commands.literal("toggle").requires(src -> src.hasPermission(2)).executes(ctx -> {
				threadStats = !threadStats;
				ConfigCommand.success(ctx, "Stat calcs are " + (!threadStats ? "disabled" : "enabled") + "!");
				return 1;
			}))
			.then(Commands.literal("startlog").requires(src -> src.hasPermission(2)).executes(ctx -> {
				doLogging = true;
				ConfigCommand.success(ctx, "Logging started!");
				return 1;
			}))
			.then(Commands.literal("stoplog").requires(src -> src.hasPermission(2)).executes(ctx -> {
				ConfigCommand.success(ctx, "Logging stopping...");
				doLogging = false;
				return 1;
			})));
	}

	public static float mean(int[] data, int max) {
		if (max <= 0) return 0;
		float total = 0;
		for (int i = 0; i < max; i++) {
			total += data[i];
		}
		return total / max;
	}

	static volatile MinecraftServer mcs;

	public static void setServer(MinecraftServer server) {
		mcs = server;
	}

	static volatile boolean resetThreadStats = false;
	static volatile boolean threadStats = false;
	static volatile boolean doLogging = false;

	// Thread stats: a ring of `samples` buckets, each covering `stepsPer` 10 ms steps
	static final int samples = 100;
	static final int stepsPer = 35;
	static int[] maxThreads = new int[samples];
	static int[] maxWorlds = new int[samples];
	static int[] maxTEs = new int[samples];
	static int[] maxEntities = new int[samples];
	static int[] maxEnvs = new int[samples];
	static int currentSteps = 0;
	static int currentPos = 0;
	static int liveValues = 0;

	static FileWriter logFile;
	static Thread statsThread;
	static int warnLog = 0;
	static int warningDelay = 15000;

	static final Logger DEV_WARNING = LoggerFactory.getLogger("MCMT Dev Warning");
	static final SimpleDateFormat SDF = new SimpleDateFormat("ddMMyy-hhmmss.SSS");

	public static void resetAll() {
		resetThreadStats = true;
	}

	public static void resetWarnDelay() {
		warningDelay = 15000;
	}

	public static synchronized void runDataThread() {
		if (statsThread != null) return;
		statsThread = new Thread(StatsCommand::dataLoop, "MCMT Stats Thread");
		statsThread.setDaemon(true);
		statsThread.start();
	}

	private static void dataLoop() {
		while (true) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				return;
			}
			try {
				step();
			} catch (Exception e) {
				Constants.LOG.error("MCMT stats thread error", e);
			}
		}
	}

	private static void step() throws IOException {
		if (threadStats || doLogging) {
			if (resetThreadStats) {
				maxThreads = new int[samples];
				maxWorlds = new int[samples];
				maxTEs = new int[samples];
				maxEntities = new int[samples];
				maxEnvs = new int[samples];
				currentSteps = 0;
				currentPos = 0;
				liveValues = 0;
				resetThreadStats = false;
			}
			if (++currentSteps % stepsPer == 0) {
				currentPos = (currentPos + 1) % samples;
				liveValues = Math.min(liveValues + 1, samples);
				maxWorlds[currentPos] = 0;
				maxTEs[currentPos] = 0;
				maxEntities[currentPos] = 0;
				maxEnvs[currentPos] = 0;
				maxThreads[currentPos] = 0;
			}
			int worlds = ParallelProcessor.currentWorlds.get();
			int tes = ParallelProcessor.currentTEs.get();
			int entities = ParallelProcessor.currentEnts.get();
			int envs = ParallelProcessor.currentEnvs.get();
			maxWorlds[currentPos] = Math.max(maxWorlds[currentPos], worlds);
			maxTEs[currentPos] = Math.max(maxTEs[currentPos], tes);
			maxEntities[currentPos] = Math.max(maxEntities[currentPos], entities);
			maxEnvs[currentPos] = Math.max(maxEnvs[currentPos], envs);
			maxThreads[currentPos] = Math.max(maxThreads[currentPos], worlds + tes + entities + envs);
		}
		MinecraftServer server = mcs;
		if (server != null && !server.isRunning()) {
			doLogging = false;
		}
		if (doLogging) {
			if (logFile == null) {
				logFile = new FileWriter("mcmt-stats-log-" + SDF.format(new Date()) + ".csv");
				logFile.write("TickTime,Memory,AvgMCMPThreads,Enabled,\n");
			}
			if (currentSteps % stepsPer == 0) {
				float tickTimeMs = -1;
				if (server != null && server.isRunning()) {
					tickTimeMs = server.getAverageTickTimeNanos() / 1_000_000.0f;
				}
				float threadCount = mean(maxThreads, liveValues);
				long memory = Runtime.getRuntime().maxMemory() - Runtime.getRuntime().freeMemory();
				int enabled = 0;
				enabled |= MCMTConfig.disabled ? 1 : 0;
				enabled |= MCMTConfig.disableWorld ? 2 : 0;
				enabled |= MCMTConfig.disableEntity ? 4 : 0;
				enabled |= MCMTConfig.disableEnvironment ? 8 : 0;
				enabled |= MCMTConfig.disableChunkProvider ? 16 : 0;
				enabled |= MCMTConfig.chunkLockModded ? 256 : 0;
				logFile.write(tickTimeMs + "," + memory + "," + threadCount + "," + enabled + ",\n");
			}
		} else if (logFile != null) {
			logFile.flush();
			logFile.close();
			logFile = null;
		}

		warnLog++;
		if (warnLog % warningDelay == 0) {
			DEV_WARNING.warn("MCMT is installed; error logs are likely invalid for any other mods");
			warningDelay = (int) Math.min((long) (warningDelay * 1.2), Math.max(MCMTConfig.logcap, 15000));
		}
	}
}
