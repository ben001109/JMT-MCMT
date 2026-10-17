package org.jmt.mcmt.paralelised.fastutil;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

class ConcurrentLongSortedSetTest {

	private static List<Long> drain(LongIterator it) {
		List<Long> out = new ArrayList<>();
		while (it.hasNext()) out.add(it.nextLong());
		return out;
	}

	@Test
	void behavesLikeAvlTreeSetIncludingSubSets() {
		Random r = new Random(7);
		LongSortedSet golden = new LongAVLTreeSet();
		LongSortedSet tested = new ConcurrentLongSortedSet();
		for (int i = 0; i < 5000; i++) {
			long v = r.nextInt(400) - 200;
			if (r.nextInt(3) == 0) {
				assertEquals(golden.remove(v), tested.remove(v));
			} else {
				assertEquals(golden.add(v), tested.add(v));
			}
		}
		assertEquals(golden.size(), tested.size());
		assertEquals(drain(golden.iterator()), drain(tested.iterator()));
		assertEquals(golden.firstLong(), tested.firstLong());
		assertEquals(golden.lastLong(), tested.lastLong());
		for (int i = 0; i < 200; i++) {
			long a = r.nextInt(500) - 250, b = a + r.nextInt(100);
			assertEquals(drain(golden.subSet(a, b).iterator()), drain(tested.subSet(a, b).iterator()), "subSet " + a + ".." + b);
			assertEquals(drain(golden.headSet(a).iterator()), drain(tested.headSet(a).iterator()));
			assertEquals(drain(golden.tailSet(a).iterator()), drain(tested.tailSet(a).iterator()));
		}
	}

	@Test
	void concurrentMutationWhileIteratingDoesNotThrow() throws Exception {
		ConcurrentLongSortedSet set = new ConcurrentLongSortedSet();
		for (long i = 0; i < 1000; i++) set.add(i);
		ExecutorService ex = Executors.newFixedThreadPool(6);
		List<Future<?>> fs = new ArrayList<>();
		for (int t = 0; t < 3; t++) {
			final long base = 10_000L * (t + 1);
			fs.add(ex.submit(() -> {
				for (int i = 0; i < 20_000; i++) {
					set.add(base + i);
					set.remove(base + i - 50);
				}
			}));
		}
		for (int t = 0; t < 3; t++) {
			fs.add(ex.submit(() -> {
				long sum = 0;
				for (int i = 0; i < 2000; i++) {
					LongIterator it = set.subSet(0, 100_000).iterator();
					while (it.hasNext()) sum += it.nextLong();
				}
				return sum;
			}));
		}
		for (Future<?> f : fs) f.get(60, TimeUnit.SECONDS);
		ex.shutdown();
		assertTrue(set.contains(999));
	}
}
