package org.jmt.mcmt;

import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.jmt.mcmt.config.MCMTConfig;
import org.jmt.mcmt.platform.Platform;
import org.jmt.mcmt.serdes.SerDesHookTypes;
import org.jmt.mcmt.serdes.SerDesRegistry;
import org.jmt.mcmt.serdes.filter.ISerDesFilter;
import org.jmt.mcmt.serdes.pools.PostExecutePool;
import org.slf4j.Logger;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Owns the worker pool and the per-tick phaser. Every Mixin hook ends up here.
 * Successor of the 1.18 {@code ASMHookTerminator}.
 */
public final class ParallelProcessor {

	private static final Logger LOGGER = Constants.LOG;

	private ParallelProcessor() {}

	static volatile Phaser phaser;
	static volatile ExecutorService executor;
	static volatile MinecraftServer server;
	static final AtomicBoolean isTicking = new AtomicBoolean();
	static final AtomicInteger threadId = new AtomicInteger();
	static final ConcurrentLinkedQueue<Throwable> tickFailures = new ConcurrentLinkedQueue<>();
	static final Map<String, Set<Thread>> threadTracker = new ConcurrentHashMap<>();

	// Statistics
	public static final AtomicInteger currentWorlds = new AtomicInteger();
	public static final AtomicInteger currentEnts = new AtomicInteger();
	public static final AtomicInteger currentTEs = new AtomicInteger();
	public static final AtomicInteger currentEnvs = new AtomicInteger();
	public static final Set<String> currentTasks = ConcurrentHashMap.newKeySet();

	public static synchronized void setupThreadpool(int parallelism) {
		ExecutorService old = executor;
		threadId.set(0);
		final ClassLoader cl = ParallelProcessor.class.getClassLoader();
		ForkJoinWorkerThreadFactory factory = pool -> {
			ForkJoinWorkerThread t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
			t.setName("MCMT-Pool-Thread-" + threadId.getAndIncrement());
			t.setContextClassLoader(cl);
			regThread("MCMT", t);
			return t;
		};
		executor = new ForkJoinPool(parallelism, factory, null, false);
		if (old != null) old.shutdown();
		LOGGER.info("MCMT thread pool started with parallelism {}", parallelism);
	}

	public static synchronized void shutdownThreadpool() {
		if (executor != null) {
			executor.shutdown();
			executor = null;
		}
	}

	/** Test hook: clear tick state without touching the pool. */
	static void resetForTests() {
		phaser = null;
		server = null;
		isTicking.set(false);
		tickFailures.clear();
	}

	public static void regThread(String poolName, Thread thread) {
		threadTracker.computeIfAbsent(poolName, k -> ConcurrentHashMap.newKeySet()).add(thread);
	}

	public static boolean isThreadPooled(String poolName, Thread t) {
		Set<Thread> s = threadTracker.get(poolName);
		return s != null && s.contains(t);
	}

	public static boolean serverExecutionThreadPatch(MinecraftServer ms) {
		return isThreadPooled("MCMT", Thread.currentThread());
	}

	public static boolean isTicking() {
		return isTicking.get();
	}

	// ---- Tick lifecycle --------------------------------------------------

	public static void preTick(MinecraftServer mcs) {
		if (phaser != null) {
			LOGGER.warn("MCMT preTick called while a tick is already in progress; is there a second server?");
			return;
		}
		if (executor == null) {
			setupThreadpool(MCMTConfig.getParallelism());
		}
		tickFailures.clear();
		isTicking.set(true);
		Phaser p = new Phaser();
		p.register();
		phaser = p;
		server = mcs;
	}

	public static void postTick(MinecraftServer mcs) {
		Phaser p = phaser;
		if (p == null) {
			return;
		}
		if (server != mcs) {
			LOGGER.warn("MCMT postTick called for a different server than preTick");
		}
		p.arriveAndAwaitAdvance();
		isTicking.set(false);
		phaser = null;
		Deque<Runnable> queue = PostExecutePool.POOL.getQueue();
		Iterator<Runnable> it = queue.iterator();
		while (it.hasNext()) {
			Runnable r = it.next();
			it.remove();
			r.run();
		}
		Throwable first = tickFailures.poll();
		if (first != null) {
			int more = tickFailures.size();
			tickFailures.clear();
			throw new RuntimeException("MCMT: " + (more + 1) + " parallel task(s) failed this tick; first failure: " + first, first);
		}
	}

	/**
	 * Submit work for the current tick. Outside a tick, or when the pool is missing,
	 * the task runs inline so game logic is never skipped.
	 */
	public static void submit(String traceName, Runnable task) {
		Phaser p = phaser;
		ExecutorService ex = executor;
		if (p == null || ex == null) {
			task.run();
			return;
		}
		String name = MCMTConfig.opsTracing ? traceName : null;
		if (name != null) currentTasks.add(name);
		p.register();
		ex.execute(() -> {
			try {
				task.run();
			} catch (Throwable t) {
				LOGGER.error("MCMT parallel task failed: {}", traceName, t);
				tickFailures.add(t);
			} finally {
				p.arriveAndDeregister();
				if (name != null) currentTasks.remove(name);
			}
		});
	}

	// ---- Hook entry points (wired by Mixins in later phases) --------------

	public static void callTick(ServerLevel level, BooleanSupplier hasTimeLeft, MinecraftServer mcs) {
		if (MCMTConfig.disabled || MCMTConfig.disableWorld || server != mcs) {
			level.tick(hasTimeLeft);
			Platform.HOOKS.onLevelPostTick(level, hasTimeLeft);
			return;
		}
		submit("WorldTick: " + level.dimension().location(), () -> {
			currentWorlds.incrementAndGet();
			try {
				level.tick(hasTimeLeft);
				if (MCMTConfig.disableWorldPostTick) {
					synchronized (ParallelProcessor.class) { Platform.HOOKS.onLevelPostTick(level, hasTimeLeft); }
				} else {
					Platform.HOOKS.onLevelPostTick(level, hasTimeLeft);
				}
			} finally {
				currentWorlds.decrementAndGet();
			}
		});
	}

	public static void callEntityTick(Entity entity, ServerLevel level) {
		if (MCMTConfig.disabled || MCMTConfig.disableEntity) {
			entity.tick();
			return;
		}
		submit("EntityTick: " + entity.getType() + "@" + entity.getId(), () -> {
			currentEnts.incrementAndGet();
			try {
				ISerDesFilter filter = SerDesRegistry.getFilter(SerDesHookTypes.EntityTick, entity.getClass());
				if (filter != null) {
					filter.serialise(entity::tick, entity, entity.getOnPos(), level, SerDesHookTypes.EntityTick);
				} else {
					entity.tick();
				}
			} finally {
				currentEnts.decrementAndGet();
			}
		});
	}

	public static void callBlockEntityTick(TickingBlockEntity ticker, Level level) {
		if (MCMTConfig.disabled || MCMTConfig.disableTileEntity || !(level instanceof ServerLevel)) {
			ticker.tick();
			return;
		}
		submit("TETick: " + ticker.getType() + "@" + ticker.getPos(), () -> {
			currentTEs.incrementAndGet();
			try {
				ISerDesFilter filter = SerDesRegistry.getFilter(SerDesHookTypes.TETick, ticker.getClass());
				if (filter != null) {
					filter.serialise(ticker::tick, ticker, ticker.getPos(), level, SerDesHookTypes.TETick);
				} else {
					ticker.tick();
				}
			} finally {
				currentTEs.decrementAndGet();
			}
		});
	}

	public static void callTickEnvironment(ServerLevel level, LevelChunk chunk, int randomTickSpeed) {
		if (MCMTConfig.disabled || MCMTConfig.disableEnvironment) {
			level.tickChunk(chunk, randomTickSpeed);
			return;
		}
		submit("EnvTick: " + chunk.getPos(), () -> {
			currentEnvs.incrementAndGet();
			try {
				level.tickChunk(chunk, randomTickSpeed);
			} finally {
				currentEnvs.decrementAndGet();
			}
		});
	}

	public static boolean shouldThreadChunks() {
		return MCMTConfig.disableMultiChunk;
	}

	// ---- Crash report -------------------------------------------------------

	public static String populateCrashReport() {
		StringBuilder sb = new StringBuilder("\n");
		sb.append("\t\tConfig Info:\n");
		sb.append("\t\t\t- Disabled: ").append(MCMTConfig.disabled).append('\n');
		sb.append("\t\t\t- World Disabled: ").append(MCMTConfig.disableWorld)
		  .append(" (onPostTick serialised: ").append(MCMTConfig.disableWorldPostTick).append(")\n");
		sb.append("\t\t\t- Entity Disabled: ").append(MCMTConfig.disableEntity).append('\n');
		sb.append("\t\t\t- Env Disabled: ").append(MCMTConfig.disableEnvironment).append('\n');
		sb.append("\t\t\t- TE Disabled: ").append(MCMTConfig.disableTileEntity).append('\n');
		sb.append("\t\t\t- SCP Disabled: ").append(MCMTConfig.disableChunkProvider).append('\n');
		if (MCMTConfig.opsTracing) {
			sb.append("\t\t-- Running Operations Begin --\n");
			for (String s : currentTasks) sb.append("\t\t\t").append(s).append('\n');
			sb.append("\t\t-- Running Operations End --\n");
		}
		return sb.toString();
	}
}
