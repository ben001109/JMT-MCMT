package org.jmt.mcmt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.redstone.CollectingNeighborUpdater;

@Mixin(CollectingNeighborUpdater.class)
public interface CollectingNeighborUpdaterAccessor {

	@Accessor("maxChainedNeighborUpdates")
	int mcmt$getMaxChainedNeighborUpdates();
}
