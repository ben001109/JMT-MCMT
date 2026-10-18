package org.jmt.mcmt.mixin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jmt.mcmt.paralelised.fastutil.ConcurrentLongLinkedOpenHashSet;
import org.jmt.mcmt.paralelised.fastutil.Long2ObjectConcurrentHashMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.jmt.mcmt.duck.EntitySectionStorageAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;

/**
 * Chunk visibility/load bookkeeping is read by worker threads (entity ticks, movement callbacks)
 * while the level thread updates it. Replace the fastutil hash collections with concurrent ones,
 * preserving the default return values vanilla configures in the constructor.
 */
@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntitySectionManagerMixin<T extends EntityAccess> {

	@Shadow @Final @Mutable Set<UUID> knownUuids;
	@Shadow @Final @Mutable private Long2ObjectMap<Visibility> chunkVisibility;
	// ChunkLoadStatus is package-private; the erased descriptor is all Mixin needs to match.
	@Shadow @Final @Mutable private Long2ObjectMap<Object> chunkLoadStatuses;
	@Shadow @Final @Mutable private LongSet chunksToUnload;
	@Shadow @Final private EntitySectionStorage<T> sectionStorage;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void mcmt$useConcurrentCollections(CallbackInfo ci) {
		this.knownUuids = ConcurrentHashMap.newKeySet();

		Long2ObjectMap<Visibility> vis = new Long2ObjectConcurrentHashMap<>();
		vis.defaultReturnValue(this.chunkVisibility.defaultReturnValue());
		vis.putAll(this.chunkVisibility);
		this.chunkVisibility = vis;
		// The section storage was constructed with `this.chunkVisibility::get`, bound to the old map.
		((EntitySectionStorageAccess) this.sectionStorage).mcmt$setInitialSectionVisibility(vis::get);

		Long2ObjectMap<Object> load = new Long2ObjectConcurrentHashMap<>();
		load.defaultReturnValue(this.chunkLoadStatuses.defaultReturnValue());
		load.putAll(this.chunkLoadStatuses);
		this.chunkLoadStatuses = load;

		LongSet unload = new ConcurrentLongLinkedOpenHashSet();
		unload.addAll(this.chunksToUnload);
		this.chunksToUnload = unload;
	}
}
