package org.jmt.mcmt.paralelised.fastutil;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/** The vanilla call patterns our Mixins route through the concurrent fastutil wrappers. */
class ConcurrentFastutilMapsTest {

	@Test
	void int2ObjectValuesIterateEverything() {
		Int2ObjectMap<String> m = new Int2ObjectConcurrentHashMap<>();
		m.put(1, "a");
		m.put(2, "b");
		m.put(3, "c");
		List<String> seen = new ArrayList<>();
		for (String s : m.values()) seen.add(s);
		assertEquals(3, seen.size());
		assertTrue(seen.containsAll(List.of("a", "b", "c")));
		assertEquals("b", m.get(2));
		assertNull(m.get(9));
		assertEquals("b", m.remove(2));
		assertEquals(2, m.size());
	}

	@Test
	void int2ObjectPutAllFromVanillaMap() {
		Int2ObjectMap<String> src = new it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap<>();
		src.put(5, "x");
		Int2ObjectMap<String> m = new Int2ObjectConcurrentHashMap<>();
		m.putAll(src);
		assertEquals("x", m.get(5));
		int n = 0;
		for (String ignored : m.values()) n++;
		assertEquals(1, n);
	}

	@Test
	void long2ObjectDefaultReturnValueAndPutSemantics() {
		Long2ObjectMap<String> m = new Long2ObjectConcurrentHashMap<>();
		m.defaultReturnValue("DEF");
		assertEquals("DEF", m.get(1L));
		assertEquals("DEF", m.put(1L, "a"));
		assertEquals("a", m.put(1L, "b"));
		assertEquals("b", m.get(1L));
		assertEquals("b", m.remove(1L));
		assertEquals("DEF", m.get(1L));
		assertEquals("DEF", m.remove(1L));
	}

	@Test
	void long2ObjectComputeIfAbsentAndFastIterable() {
		Long2ObjectMap<String> m = new Long2ObjectConcurrentHashMap<>();
		assertEquals("v7", m.computeIfAbsent(7L, k -> "v" + k));
		assertEquals("v7", m.computeIfAbsent(7L, k -> "other"));
		m.put(8L, "v8");
		int n = 0;
		for (Long2ObjectMap.Entry<String> e : Long2ObjectMaps.fastIterable(m)) {
			assertEquals("v" + e.getLongKey(), e.getValue());
			n++;
		}
		assertEquals(2, n);
		List<Long> keys = new ArrayList<>();
		m.keySet().forEach(k -> keys.add(k));
		assertEquals(2, keys.size());
		int v = 0;
		for (String ignored : m.values()) v++;
		assertEquals(2, v);
	}

	@Test
	void concurrentLongSetBasics() {
		LongSet s = new ConcurrentLongLinkedOpenHashSet();
		assertTrue(s.add(3L));
		assertFalse(s.add(3L));
		assertTrue(s.contains(3L));
		LongSet other = new LongOpenHashSet();
		other.add(4L);
		other.add(5L);
		s.addAll(other);
		assertEquals(3, s.size());
		s.removeIf(l -> l == 4L);
		assertEquals(2, s.size());
		int n = 0;
		for (var it = s.iterator(); it.hasNext(); it.nextLong()) n++;
		assertEquals(2, n);
	}
}
