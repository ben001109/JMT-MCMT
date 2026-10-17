package org.jmt.mcmt.mixin;

import java.util.function.BooleanSupplier;

import org.jmt.mcmt.ParallelProcessor;
import org.jmt.mcmt.config.MCMTConfig;
import org.jmt.mcmt.paralelised.ChunkCache;
import org.jmt.mcmt.paralelised.ChunkLock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Replaces {@code pscp.js}, {@code scpcoaccess.js} and the 1.18 {@code ParaServerChunkProvider}
 * subclass. Worker threads are allowed through the "main thread only" checks, the unsafe 4-slot
 * vanilla cache is bypassed for them, and a concurrent cache plus per-chunk locking sits in front
 * of the real chunk lookups.
 */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin {

	@Shadow @Final Thread mainThread;

	@Unique private final ChunkCache<ChunkStatus> mcmt$cache = new ChunkCache<>();
	@Unique private final ChunkLock mcmt$loadLock = new ChunkLock();

	@Unique
	private static boolean mcmt$onWorker() {
		return ParallelProcessor.isThreadPooled("MCMT", Thread.currentThread());
	}

	/** Worker threads pose as the main thread, so vanilla does not bounce them through the executor. */
	@Redirect(method = { "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;", "getChunkNow" },
			at = @At(value = "INVOKE", target = "Ljava/lang/Thread;currentThread()Ljava/lang/Thread;"))
	private Thread mcmt$currentThread() {
		Thread t = Thread.currentThread();
		return mcmt$onWorker() ? this.mainThread : t;
	}

	/** Only the real main thread may write the vanilla 4-slot cache; workers use the concurrent one. */
	@Redirect(method = { "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;", "getChunkNow" },
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;storeInCache(JLnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/chunk/status/ChunkStatus;)V"))
	private void mcmt$storeInCache(ServerChunkCache self, long pos, ChunkAccess chunk, ChunkStatus status) {
		if (Thread.currentThread() == this.mainThread) {
			((ServerChunkCacheInvoker) this).mcmt$invokeStoreInCache(pos, chunk, status);
		}
	}

	@WrapMethod(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;")
	private ChunkAccess mcmt$getChunk(int x, int z, ChunkStatus status, boolean load, Operation<ChunkAccess> original) {
		if (MCMTConfig.disabled || MCMTConfig.disableChunkProvider || !mcmt$onWorker()) {
			return original.call(x, z, status, load);
		}
		long pos = ChunkPos.asLong(x, z);
		ChunkAccess cached = mcmt$cache.lookup(pos, status);
		if (cached != null) {
			return cached;
		}
		ChunkAccess result;
		if (ParallelProcessor.shouldThreadChunks()) {
			// Several chunks may load at once, but each chunk by one thread only.
			long[] locks = mcmt$loadLock.lock(pos, 0);
			try {
				cached = mcmt$cache.lookup(pos, status);
				if (cached != null) return cached;
				result = original.call(x, z, status, load);
			} finally {
				mcmt$loadLock.unlock(locks);
			}
		} else {
			synchronized (this) {
				cached = mcmt$cache.lookup(pos, status);
				if (cached != null) return cached;
				result = original.call(x, z, status, load);
			}
		}
		mcmt$cache.store(pos, result, status);
		return result;
	}

	@WrapMethod(method = "getChunkNow")
	private LevelChunk mcmt$getChunkNow(int x, int z, Operation<LevelChunk> original) {
		if (MCMTConfig.disabled || !mcmt$onWorker()) {
			return original.call(x, z);
		}
		long pos = ChunkPos.asLong(x, z);
		ChunkAccess cached = mcmt$cache.lookup(pos, ChunkStatus.FULL);
		if (cached instanceof LevelChunk lc) {
			return lc;
		}
		LevelChunk result;
		synchronized (this) {
			result = original.call(x, z);
		}
		mcmt$cache.store(pos, result, ChunkStatus.FULL);
		return result;
	}

	/** Replaces {@code envparallel.js}: per-chunk environment ticks (random ticks, weather) go to the pool. */
	@Redirect(method = "tickChunks",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V"))
	private void mcmt$tickChunk(ServerLevel level, LevelChunk chunk, int randomTickSpeed) {
		ParallelProcessor.callTickEnvironment(level, chunk, randomTickSpeed);
	}

	/** The chunk source tick is serialised against worker-thread chunk loads (1.18 {@code SCPTick}). */
	@WrapMethod(method = "tick(Ljava/util/function/BooleanSupplier;Z)V")
	private void mcmt$tick(BooleanSupplier hasTimeLeft, boolean tickChunks, Operation<Void> original) {
		synchronized (this) {
			original.call(hasTimeLeft, tickChunks);
		}
	}
}
