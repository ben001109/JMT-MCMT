package org.jmt.mcmt.serdes;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;

import org.jmt.mcmt.config.MCMTConfig;
import org.jmt.mcmt.config.MapConfigStore;
import org.jmt.mcmt.serdes.filter.ISerDesFilter;
import org.jmt.mcmt.serdes.filter.PistonFilter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

class SerDesRegistryTest {

	/**
	 * A fake modded block entity class so DefaultFilter treats it as unknown. Never constructed
	 * (the BlockEntity constructor needs a registry type); tests use the class or a Mockito mock.
	 */
	abstract static class ModdedBlockEntity extends BlockEntity {
		ModdedBlockEntity() {
			super(null, BlockPos.ZERO, null);
		}
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
		assertTrue(SerDesHookTypes.TETick.isTargetable(piston), "block entities must be targetable by the TE hook");
	}

	@Test
	void unknownClassFallsBackToDefaultFilter() {
		ISerDesFilter f = SerDesRegistry.getFilter(SerDesHookTypes.TETick, ModdedBlockEntity.class);
		assertNotNull(f);
		assertEquals("DefaultFilter", f.getClass().getSimpleName());
	}

	@Test
	void whitelistedClassReturnsNull() {
		MCMTConfig.teWhiteList.add(ModdedBlockEntity.class);
		SerDesRegistry.initFilters();
		SerDesRegistry.initLookup();
		try {
			assertNull(SerDesRegistry.getFilter(SerDesHookTypes.TETick, ModdedBlockEntity.class));
		} finally {
			MCMTConfig.teWhiteList.remove(ModdedBlockEntity.class);
			SerDesRegistry.initFilters();
			SerDesRegistry.initLookup();
		}
	}

	@Test
	void defaultFilterRunsTaskForModdedBlockEntity() {
		BlockEntity modded = Mockito.mock(ModdedBlockEntity.class);
		ISerDesFilter f = SerDesRegistry.getFilter(SerDesHookTypes.TETick, modded.getClass());
		assertNotNull(f);
		AtomicBoolean ran = new AtomicBoolean();
		f.serialise(() -> ran.set(true), modded, BlockPos.ZERO, null, SerDesHookTypes.TETick);
		assertTrue(ran.get());
	}
}
