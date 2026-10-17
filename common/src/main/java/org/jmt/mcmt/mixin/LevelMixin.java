package org.jmt.mcmt.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jmt.mcmt.ParallelProcessor;
import org.jmt.mcmt.paralelised.ThreadLocalNeighborUpdater;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.level.redstone.NeighborUpdater;

/**
 * Level-wide state that worker threads touch during entity, block entity and environment ticks:
 * <ul>
 * <li>{@code random}: vanilla's {@code LegacyRandomSource} is single-threaded; use the thread-safe
 * implementation vanilla already ships.</li>
 * <li>{@code neighborUpdater}: the collecting updater keeps a stack of pending updates; give each
 * thread its own.</li>
 * <li>block entity ticker lists: registered from worker threads while the level thread owns them;
 * both become synchronised lists.</li>
 * </ul>
 * Also replaces {@code teparallel.js}: block entity ticks go to the worker pool.
 */
@Mixin(Level.class)
public abstract class LevelMixin {

	@Shadow @Final @Mutable public RandomSource random;
	@Shadow @Final @Mutable protected NeighborUpdater neighborUpdater;
	@Shadow @Final @Mutable protected List<TickingBlockEntity> blockEntityTickers;
	@Shadow @Final @Mutable private List<TickingBlockEntity> pendingBlockEntityTickers;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void mcmt$makeLevelStateThreadSafe(CallbackInfo ci) {
		this.random = RandomSource.createThreadSafe();
		if (this.neighborUpdater instanceof CollectingNeighborUpdater collecting) {
			final Level level = (Level) (Object) this;
			final int maxChained = ((CollectingNeighborUpdaterAccessor) collecting).mcmt$getMaxChainedNeighborUpdates();
			this.neighborUpdater = new ThreadLocalNeighborUpdater(() -> new CollectingNeighborUpdater(level, maxChained));
		}
		this.blockEntityTickers = Collections.synchronizedList(new ArrayList<>(this.blockEntityTickers));
		this.pendingBlockEntityTickers = Collections.synchronizedList(new ArrayList<>(this.pendingBlockEntityTickers));
	}

	@Redirect(method = "tickBlockEntities",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V"))
	private void mcmt$tickBlockEntity(TickingBlockEntity ticker) {
		ParallelProcessor.callBlockEntityTick(ticker, (Level) (Object) this);
	}
}
