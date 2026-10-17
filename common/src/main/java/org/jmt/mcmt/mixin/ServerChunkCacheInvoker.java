package org.jmt.mcmt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

@Mixin(ServerChunkCache.class)
public interface ServerChunkCacheInvoker {

	@Invoker("storeInCache")
	void mcmt$invokeStoreInCache(long pos, ChunkAccess chunk, ChunkStatus status);
}
