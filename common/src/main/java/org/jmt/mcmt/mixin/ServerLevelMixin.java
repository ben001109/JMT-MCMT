package org.jmt.mcmt.mixin;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jmt.mcmt.ParallelProcessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Replaces {@code entityparallel.js} (entity ticks go to the worker pool) and the
 * {@code ServerWorldCollections} coremod (collections touched from worker threads).
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

	@Shadow @Final @Mutable Set<Mob> navigatingMobs;

	@Unique private final Object mcmt$blockEventLock = new Object();

	@Redirect(method = "tickNonPassenger",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
	private void mcmt$tickEntity(Entity entity) {
		ParallelProcessor.callEntityTick(entity, (ServerLevel) (Object) this);
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	private void mcmt$useConcurrentCollections(CallbackInfo ci) {
		Set<Mob> mobs = ConcurrentHashMap.newKeySet();
		mobs.addAll(this.navigatingMobs);
		this.navigatingMobs = mobs;
	}

	// Block events are queued from entity ticks on worker threads and drained on the level thread.

	@WrapMethod(method = "blockEvent")
	private void mcmt$blockEvent(BlockPos pos, Block block, int type, int data, Operation<Void> original) {
		synchronized (mcmt$blockEventLock) {
			original.call(pos, block, type, data);
		}
	}

	@WrapMethod(method = "runBlockEvents")
	private void mcmt$runBlockEvents(Operation<Void> original) {
		synchronized (mcmt$blockEventLock) {
			original.call();
		}
	}

	@WrapMethod(method = "clearBlockEvents")
	private void mcmt$clearBlockEvents(BoundingBox box, Operation<Void> original) {
		synchronized (mcmt$blockEventLock) {
			original.call(box);
		}
	}
}
