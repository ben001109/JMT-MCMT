package org.jmt.mcmt.mixin;

import org.jmt.mcmt.paralelised.fastutil.Int2ObjectConcurrentHashMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;

/**
 * {@code ChunkMap.entityMap} (entity id -> tracker) is mutated when entities start or stop being
 * tracked, which now happens on worker threads, while {@code ChunkMap.tick} iterates it on the
 * level thread. Use a concurrent map.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {

	// TrackedEntity is package-private; the erased descriptor is all Mixin needs to match.
	@Shadow @Final @Mutable private Int2ObjectMap<Object> entityMap;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void mcmt$useConcurrentEntityMap(CallbackInfo ci) {
		Int2ObjectMap<Object> map = new Int2ObjectConcurrentHashMap<>();
		map.putAll(this.entityMap);
		this.entityMap = map;
	}
}
