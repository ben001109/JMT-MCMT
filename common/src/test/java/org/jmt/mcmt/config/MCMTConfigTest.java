package org.jmt.mcmt.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MCMTConfigTest {

	MapConfigStore store;

	@BeforeEach
	void setUp() {
		store = new MapConfigStore();
	}

	@Test
	void bakeReadsDefaultsWhenStoreEmpty() {
		MCMTConfig.bake(store);
		assertFalse(MCMTConfig.disabled);
		assertEquals(-1, MCMTConfig.paraMax);
		assertEquals(MCMTConfig.ParaMaxMode.Standard, MCMTConfig.paraMaxMode);
		assertTrue(MCMTConfig.chunkLockModded);
		assertEquals(720000, MCMTConfig.logcap);
	}

	@Test
	void bakeResolvesKnownClassesAndKeepsUnknownNames() {
		store.setStringList("te.teWhiteList", List.of("java.lang.String", "no.such.Clazz"));
		MCMTConfig.bake(store);
		assertTrue(MCMTConfig.teWhiteList.contains(String.class));
		assertEquals(List.of("no.such.Clazz"), MCMTConfig.teUnfoundWhiteList);
	}

	@Test
	void saveWritesRuntimeValuesBack() {
		MCMTConfig.bake(store);
		MCMTConfig.disabled = true;
		MCMTConfig.paraMax = 6;
		MCMTConfig.teBlackList.add(Integer.class);
		MCMTConfig.save(store);
		assertEquals(true, store.values.get("general.disabled"));
		assertEquals(6, store.values.get("general.parallelism.paraMax"));
		assertTrue(store.getStringList("te.teBlackList", List.of()).contains("java.lang.Integer"));
		assertEquals(1, store.saveCount);
	}

	@Test
	void parallelismStandardIsCappedByCores() {
		MCMTConfig.bake(store);
		int cores = Runtime.getRuntime().availableProcessors();
		MCMTConfig.paraMaxMode = MCMTConfig.ParaMaxMode.Standard;
		MCMTConfig.paraMax = cores + 10;
		assertEquals(Math.max(2, cores), MCMTConfig.getParallelism());
		MCMTConfig.paraMax = -1;
		assertEquals(cores, MCMTConfig.getParallelism());
	}

	@Test
	void parallelismOverrideIgnoresCoreCap() {
		MCMTConfig.bake(store);
		MCMTConfig.paraMaxMode = MCMTConfig.ParaMaxMode.Override;
		MCMTConfig.paraMax = 1000;
		assertEquals(1000, MCMTConfig.getParallelism());
	}

	@Test
	void parallelismReductionNeverBelowTwo() {
		MCMTConfig.bake(store);
		MCMTConfig.paraMaxMode = MCMTConfig.ParaMaxMode.Reduction;
		MCMTConfig.paraMax = 100000;
		assertEquals(2, MCMTConfig.getParallelism());
	}
}
