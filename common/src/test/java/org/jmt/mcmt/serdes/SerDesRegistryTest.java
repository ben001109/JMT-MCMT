package org.jmt.mcmt.serdes;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;

import org.jmt.mcmt.config.MCMTConfig;
import org.jmt.mcmt.config.MapConfigStore;
import org.jmt.mcmt.serdes.filter.ISerDesFilter;
import org.jmt.mcmt.serdes.filter.PistonFilter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

class SerDesRegistryTest {

	/** A fake modded ticker so DefaultFilter treats it as unknown. */
	static class ModdedTicker implements TickingBlockEntity {
		@Override public void tick() {}
		@Override public boolean isRemoved() { return false; }
		@Override public BlockPos getPos() { return BlockPos.ZERO; }
		@Override public String getType() { return "modded"; }
	}

	@BeforeAll
	static void init() {
		MCMTConfig.bake(new MapConfigStore());
		SerDesRegistry.init();
	}

	@Test
	void pistonFilterTargetsPistonMovingBlockEntity() throws Exception {
		// Load without initialising: PistonMovingBlockEntity's static init touches registries.
		Class<?> piston = Class.forName("net.minecraft.world.level.block.piston.PistonMovingBlockEntity",
				false, getClass().getClassLoader());
		assertTrue(new PistonFilter().getTargets().contains(piston));
	}

	@Test
	void unknownClassFallsBackToDefaultFilter() {
		ISerDesFilter f = SerDesRegistry.getFilter(SerDesHookTypes.TETick, ModdedTicker.class);
		assertNotNull(f);
		assertEquals("DefaultFilter", f.getClass().getSimpleName());
	}

	@Test
	void whitelistedClassReturnsNull() {
		MCMTConfig.teWhiteList.add(ModdedTicker.class);
		SerDesRegistry.initFilters();
		SerDesRegistry.initLookup();
		try {
			assertNull(SerDesRegistry.getFilter(SerDesHookTypes.TETick, ModdedTicker.class));
		} finally {
			MCMTConfig.teWhiteList.remove(ModdedTicker.class);
			SerDesRegistry.initFilters();
			SerDesRegistry.initLookup();
		}
	}

	@Test
	void defaultFilterRunsTaskForModdedTicker() {
		ISerDesFilter f = SerDesRegistry.getFilter(SerDesHookTypes.TETick, ModdedTicker.class);
		AtomicBoolean ran = new AtomicBoolean();
		f.serialise(() -> ran.set(true), new ModdedTicker(), BlockPos.ZERO, null, SerDesHookTypes.TETick);
		assertTrue(ran.get());
	}
}
