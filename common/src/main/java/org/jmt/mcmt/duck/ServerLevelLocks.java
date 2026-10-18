package org.jmt.mcmt.duck;

/** Implemented on {@code ServerLevel} by Mixin; exposes the per-level locks MCMT adds. */
public interface ServerLevelLocks {

	/**
	 * Guards {@code sendBlockUpdated} and the entity tracking callbacks, which share vanilla's
	 * {@code isUpdatingNavigations} recursion flag and the {@code navigatingMobs} set.
	 */
	Object mcmt$blockUpdateLock();
}
