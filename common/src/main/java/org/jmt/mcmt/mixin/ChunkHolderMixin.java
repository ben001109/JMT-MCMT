package org.jmt.mcmt.mixin;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Block and light change tracking (per-section {@code ShortOpenHashSet}s and bit sets) is written
 * from worker threads and drained on the level thread; serialise the three entry points.
 */
@Mixin(ChunkHolder.class)
public abstract class ChunkHolderMixin {

	@WrapMethod(method = "blockChanged")
	private void mcmt$blockChanged(BlockPos pos, Operation<Void> original) {
		synchronized (this) {
			original.call(pos);
		}
	}

	@WrapMethod(method = "sectionLightChanged")
	private void mcmt$sectionLightChanged(LightLayer layer, int section, Operation<Void> original) {
		synchronized (this) {
			original.call(layer, section);
		}
	}

	@WrapMethod(method = "broadcastChanges")
	private void mcmt$broadcastChanges(LevelChunk chunk, Operation<Void> original) {
		synchronized (this) {
			original.call(chunk);
		}
	}
}
