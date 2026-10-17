package org.jmt.mcmt.duck;

import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import net.minecraft.world.level.entity.Visibility;

/** Implemented on {@code EntitySectionStorage} by Mixin; see {@code EntitySectionStorageMixin}. */
public interface EntitySectionStorageAccess {

	/**
	 * Re-points the "visibility of a new section" lookup. Vanilla hands over a method reference
	 * bound to the manager's original {@code chunkVisibility} map; after MCMT swaps that map for a
	 * concurrent one the reference would keep reading the stale, never-updated original.
	 */
	void mcmt$setInitialSectionVisibility(Long2ObjectFunction<Visibility> lookup);
}
