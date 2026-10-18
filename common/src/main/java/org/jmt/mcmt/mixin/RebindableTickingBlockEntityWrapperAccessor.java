package org.jmt.mcmt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.block.entity.TickingBlockEntity;

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$RebindableTickingBlockEntityWrapper")
public interface RebindableTickingBlockEntityWrapperAccessor {

	@Accessor("ticker")
	TickingBlockEntity mcmt$getTicker();
}
