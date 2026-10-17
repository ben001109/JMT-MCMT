package org.jmt.mcmt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.block.entity.BlockEntity;

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public interface BoundTickingBlockEntityAccessor {

	@Accessor("blockEntity")
	BlockEntity mcmt$getBlockEntity();
}
