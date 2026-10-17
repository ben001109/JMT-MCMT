package org.jmt.mcmt.paralelised;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import net.minecraft.world.level.chunk.ChunkAccess;

class ChunkCacheTest {

	private static ChunkAccess chunk() {
		return Mockito.mock(ChunkAccess.class);
	}

	private static String status(String name) {
		return name;
	}

	@Test
	void storeThenLookupReturnsSameChunkForSameStatusOnly() {
		ChunkCache cache = new ChunkCache<String>(16);
		String full = status("full");
		String features = status("features");
		ChunkAccess c = chunk();
		cache.store(7L, c, full);
		assertSame(c, cache.lookup(7L, full));
		assertNull(cache.lookup(7L, features));
		assertNull(cache.lookup(8L, full));
	}

	@Test
	void nullChunksAreNotStored() {
		ChunkCache cache = new ChunkCache<String>(16);
		cache.store(1L, null, status("full"));
		assertEquals(0, cache.size());
	}

	@Test
	void evictionKeepsSizeBounded() {
		ChunkCache cache = new ChunkCache<String>(64);
		String full = status("full");
		List<ChunkAccess> keep = new ArrayList<>();
		for (long i = 0; i < 1000; i++) {
			ChunkAccess c = chunk();
			keep.add(c);
			cache.store(i, c, full);
		}
		assertTrue(cache.size() <= 64 * 2, "size " + cache.size());
	}

	@Test
	void concurrentStoresAndLookupsDoNotCorrupt() throws Exception {
		ChunkCache cache = new ChunkCache<String>(256);
		String full = status("full");
		ChunkAccess[] chunks = new ChunkAccess[512];
		for (int i = 0; i < chunks.length; i++) chunks[i] = chunk();
		ExecutorService ex = Executors.newFixedThreadPool(8);
		List<Future<?>> fs = new ArrayList<>();
		for (int t = 0; t < 8; t++) {
			fs.add(ex.submit(() -> {
				for (int i = 0; i < 20000; i++) {
					int pos = i % chunks.length;
					cache.store(pos, chunks[pos], full);
					ChunkAccess got = cache.lookup(pos, full);
					if (got != null && got != chunks[pos]) {
						throw new AssertionError("wrong chunk for pos " + pos);
					}
				}
			}));
		}
		for (Future<?> f : fs) f.get(60, TimeUnit.SECONDS);
		ex.shutdown();
	}
}
