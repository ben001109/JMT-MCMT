package org.jmt.mcmt.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.world.ticks.LevelTicks;

/**
 * Marker Mixin: {@link SynchronisePlugin} makes every public method of {@code LevelTicks}
 * {@code synchronized}. Worker threads schedule block and fluid ticks (block changes during
 * entity, block entity and environment ticks) while the level thread owns the queues
 * (1.18 {@code generalparallel.js} "ServerTickList").
 */
@Mixin(LevelTicks.class)
public abstract class LevelTicksMixin {
}
