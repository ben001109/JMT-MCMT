package org.jmt.mcmt.paralelised;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class ConcurrentDoublyLinkedListTest {

	@Test
	void matchesLinkedListUnderRandomSingleThreadedOps() {
		Random r = new Random(42);
		for (int round = 0; round < 50; round++) {
			List<Integer> golden = new LinkedList<>();
			List<Integer> tested = new ConcurrentDoublyLinkedList<>();
			for (int j = 0; j < 1000; j++) {
				int value = r.nextInt(256);
				if (r.nextInt(4) == 0) {
					golden.remove((Integer) value);
					tested.remove((Integer) value);
				} else {
					golden.add(value);
					tested.add(value);
				}
			}
			assertEquals(golden, new ArrayList<>(tested), "round " + round);
		}
	}

	@Test
	void concurrentAddsAreAllRetained() throws Exception {
		ConcurrentDoublyLinkedList<Integer> list = new ConcurrentDoublyLinkedList<>();
		int threads = 8, perThread = 2000;
		ExecutorService ex = Executors.newFixedThreadPool(threads);
		List<Future<?>> fs = new ArrayList<>();
		for (int t = 0; t < threads; t++) {
			final int base = t * perThread;
			fs.add(ex.submit(() -> { for (int i = 0; i < perThread; i++) list.add(base + i); }));
		}
		for (Future<?> f : fs) f.get(30, TimeUnit.SECONDS);
		ex.shutdown();
		assertEquals(threads * perThread, list.size());
		assertEquals(threads * perThread, new ArrayList<>(list).stream().distinct().count());
	}
}
