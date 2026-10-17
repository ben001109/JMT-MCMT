package org.jmt.mcmt.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.world.level.entity.EntityTickList;

/**
 * Marker Mixin: {@link SynchronisePlugin} makes every public method of the target
 * {@code synchronized} (1.18 {@code generalparallel.js} "EntityTickList"). Entities are added and
 * removed from worker threads while the level thread iterates the list.
 */
@Mixin(EntityTickList.class)
public abstract class EntityTickListMixin {
}
