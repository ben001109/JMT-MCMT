package org.jmt.mcmt.paralelised;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Thread-safe (chunkPos, status) -> chunk cache, generic in the status key so it can be unit
 * tested without loading vanilla registries; that replaces the 4-slot array cache in
 * {@code ServerChunkCache}, which is not safe to use from several threads.
 * Ported from the 1.18 {@code ParaServerChunkProvider}; eviction now runs inline on insert
 * instead of on a dedicated thread.
 */
public final class ChunkCache<S> {

	public static final int DEFAULT_CAPACITY = 4096;

	private final Map<Address<S>, Line> cache = new ConcurrentHashMap<>();
	private final AtomicInteger access = new AtomicInteger(Integer.MIN_VALUE);
	private final int capacity;

	public ChunkCache() {
		this(DEFAULT_CAPACITY);
	}

	public ChunkCache(int capacity) {
		this.capacity = capacity;
	}

	@Nullable
	public ChunkAccess lookup(long chunkPos, S status) {
		int tick = access.getAndIncrement();
		if (tick == Integer.MAX_VALUE) {
			cache.clear(); // counter rollover, extremely rare
			return null;
		}
		Line line = cache.get(new Address<>(chunkPos, status));
		if (line == null) return null;
		line.lastAccess = tick;
		return line.get();
	}

	public void store(long chunkPos, @Nullable ChunkAccess chunk, S status) {
		if (chunk == null) return;
		int tick = access.getAndIncrement();
		cache.put(new Address<>(chunkPos, status), new Line(chunk, tick));
		if (cache.size() > capacity) {
			evict();
		}
	}

	public void clear() {
		cache.clear();
	}

	public int size() {
		return cache.size();
	}

	/** Drops dead references and the least recently used half of the entries above capacity. */
	private synchronized void evict() {
		int size = cache.size();
		if (size <= capacity) return;
		long max = Long.MIN_VALUE, min = Long.MAX_VALUE;
		for (Line l : cache.values()) {
			max = Math.max(max, l.lastAccess);
			min = Math.min(min, l.lastAccess);
		}
		long cutoff = min + (long) ((max - min) / ((float) size / (float) capacity));
		cache.entrySet().removeIf(e -> e.getValue().lastAccess < cutoff || e.getValue().get() == null);
	}

	private record Address<S>(long chunk, S status) {}

	private static final class Line {
		private final WeakReference<ChunkAccess> chunk;
		volatile int lastAccess;

		Line(ChunkAccess chunk, int lastAccess) {
			this.chunk = new WeakReference<>(chunk);
			this.lastAccess = lastAccess;
		}

		@Nullable
		ChunkAccess get() {
			return chunk.get();
		}
	}
}
