package org.jmt.mcmt.paralelised;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import net.minecraft.world.level.ChunkPos;

class ChunkLockTest {

	@Test
	void lockReturnsSortedUniqueTargets() {
		ChunkLock cl = new ChunkLock();
		long[] locks = cl.lock(ChunkPos.asLong(10, 10), 1);
		assertEquals(9, locks.length);
		for (int i = 1; i < locks.length; i++) assertTrue(locks[i - 1] < locks[i]);
		cl.unlock(locks);
	}

	@Test
	void overlappingRegionsNeverRunConcurrently() throws Exception {
		ChunkLock cl = new ChunkLock();
		AtomicInteger inside = new AtomicInteger();
		AtomicInteger maxInside = new AtomicInteger();
		ExecutorService ex = Executors.newFixedThreadPool(8);
		CountDownLatch done = new CountDownLatch(8);
		for (int t = 0; t < 8; t++) {
			final int dx = t % 2; // all regions overlap around (0..1, 0)
			ex.submit(() -> {
				try {
					for (int i = 0; i < 200; i++) {
						long[] l = cl.lock(ChunkPos.asLong(dx, 0), 1);
						try {
							int now = inside.incrementAndGet();
							maxInside.accumulateAndGet(now, Math::max);
							Thread.yield();
							inside.decrementAndGet();
						} finally {
							cl.unlock(l);
						}
					}
				} finally {
					done.countDown();
				}
			});
		}
		assertTrue(done.await(60, TimeUnit.SECONDS), "deadlock: workers did not finish");
		ex.shutdown();
		assertEquals(1, maxInside.get());
	}
}
