package org.jmt.mcmt.mixin;

import org.jmt.mcmt.duck.EntitySectionStorageAccess;
import org.jmt.mcmt.paralelised.fastutil.ConcurrentLongSortedSet;
import org.jmt.mcmt.paralelised.fastutil.Long2ObjectConcurrentHashMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.Visibility;

/**
 * Entities moving on worker threads create and remove sections concurrently with other
 * workers querying them. Swap the hash map and AVL tree for concurrent equivalents
 * (formerly done by the {@code sync-fu} fastutil transformer).
 */
@Mixin(EntitySectionStorage.class)
public abstract class EntitySectionStorageMixin<T extends EntityAccess> implements EntitySectionStorageAccess {

	@Shadow @Final @Mutable private Long2ObjectMap<EntitySection<T>> sections;
	@Shadow @Final @Mutable private LongSortedSet sectionIds;
	@Shadow @Final @Mutable private Long2ObjectFunction<Visibility> intialSectionVisibility;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void mcmt$useConcurrentCollections(CallbackInfo ci) {
		Long2ObjectMap<EntitySection<T>> map = new Long2ObjectConcurrentHashMap<>();
		map.putAll(this.sections);
		this.sections = map;
		LongSortedSet ids = new ConcurrentLongSortedSet();
		ids.addAll(this.sectionIds);
		this.sectionIds = ids;
	}

	@Override
	public void mcmt$setInitialSectionVisibility(Long2ObjectFunction<Visibility> lookup) {
		this.intialSectionVisibility = lookup;
	}
}
