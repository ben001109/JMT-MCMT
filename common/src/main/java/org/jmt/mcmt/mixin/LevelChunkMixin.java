package org.jmt.mcmt.mixin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;

/** Ticker registrations change from worker threads while the level thread iterates them. */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {

	// RebindableTickingBlockEntityWrapper is package-private; the erased descriptor matches.
	@Shadow @Final @Mutable private Map<BlockPos, Object> tickersInLevel;

	@Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;Lnet/minecraft/world/ticks/LevelChunkTicks;Lnet/minecraft/world/ticks/LevelChunkTicks;J[Lnet/minecraft/world/level/chunk/LevelChunkSection;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;Lnet/minecraft/world/level/levelgen/blending/BlendingData;)V",
			at = @At("RETURN"))
	private void mcmt$useConcurrentTickerMap(CallbackInfo ci) {
		this.tickersInLevel = new ConcurrentHashMap<>(this.tickersInLevel);
	}
}
