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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Block entities are added and removed by worker threads (block entity and environment ticks). */
@Mixin(ChunkAccess.class)
public abstract class ChunkAccessMixin {

	@Shadow @Final @Mutable protected Map<BlockPos, BlockEntity> blockEntities;
	@Shadow @Final @Mutable protected Map<BlockPos, CompoundTag> pendingBlockEntities;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void mcmt$useConcurrentBlockEntityMaps(CallbackInfo ci) {
		this.blockEntities = new ConcurrentHashMap<>(this.blockEntities);
		this.pendingBlockEntities = new ConcurrentHashMap<>(this.pendingBlockEntities);
	}
}
