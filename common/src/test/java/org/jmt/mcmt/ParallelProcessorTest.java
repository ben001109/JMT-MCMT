package org.jmt.mcmt;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;

import org.jmt.mcmt.config.MCMTConfig;
import org.jmt.mcmt.config.MapConfigStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParallelProcessorTest {

	@BeforeEach
	void reset() {
		MCMTConfig.bake(new MapConfigStore());
		ParallelProcessor.setupThreadpool(4);
		ParallelProcessor.resetForTests();
	}

	@Test
	void poolThreadsAreTrackedAsMcmtThreads() {
		Thread[] seen = new Thread[1];
		ParallelProcessor.preTick(null);
		ParallelProcessor.submit("test", () -> seen[0] = Thread.currentThread());
		ParallelProcessor.postTick(null);
		assertNotNull(seen[0]);
		assertTrue(ParallelProcessor.isThreadPooled("MCMT", seen[0]));
		assertTrue(seen[0].getName().startsWith("MCMT-Pool-Thread-"));
	}

	@Test
	void postTickWaitsForAllSubmittedTasks() {
		AtomicInteger count = new AtomicInteger();
		ParallelProcessor.preTick(null);
		for (int i = 0; i < 500; i++) {
			ParallelProcessor.submit("test", () -> { Thread.yield(); count.incrementAndGet(); });
		}
		ParallelProcessor.postTick(null);
		assertEquals(500, count.get());
	}

	@Test
	void submitOutsideTickRunsInline() {
		Thread[] seen = new Thread[1];
		ParallelProcessor.submit("test", () -> seen[0] = Thread.currentThread());
		assertSame(Thread.currentThread(), seen[0]);
	}

	@Test
	void taskExceptionIsRethrownAtPostTick() {
		ParallelProcessor.preTick(null);
		ParallelProcessor.submit("boom", () -> { throw new IllegalStateException("boom"); });
		RuntimeException ex = assertThrows(RuntimeException.class, () -> ParallelProcessor.postTick(null));
		assertNotNull(ex.getCause());
		assertEquals("boom", ex.getCause().getMessage());
	}
}
