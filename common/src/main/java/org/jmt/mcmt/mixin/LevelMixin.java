package org.jmt.mcmt.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.jmt.mcmt.ParallelProcessor;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

/**
 * Level-wide state that worker threads touch during entity ticks:
 * <ul>
 * <li>{@code random}: vanilla's {@code LegacyRandomSource} throws when used from two threads
 * (ThreadingDetector). Replace it with the thread-safe implementation vanilla already ships.</li>
 * <li>block entity ticker lists: registered from worker threads (block placement during entity
 * ticks) while the level thread owns them. Both become synchronised lists; iteration in
 * {@code tickBlockEntities} stays on the level thread and additions during it go to the pending list.</li>
 * </ul>
 */
@Mixin(Level.class)
public abstract class LevelMixin {

	@Shadow @Final @Mutable public RandomSource random;
	@Shadow @Final @Mutable protected List<TickingBlockEntity> blockEntityTickers;
	@Shadow @Final @Mutable private List<TickingBlockEntity> pendingBlockEntityTickers;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void mcmt$makeLevelStateThreadSafe(CallbackInfo ci) {
		this.random = RandomSource.createThreadSafe();
		this.blockEntityTickers = Collections.synchronizedList(new ArrayList<>(this.blockEntityTickers));
		this.pendingBlockEntityTickers = Collections.synchronizedList(new ArrayList<>(this.pendingBlockEntityTickers));
	}

	/** Replaces {@code teparallel.js}: block entity ticks go to the worker pool. */
	@Redirect(method = "tickBlockEntities",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V"))
	private void mcmt$tickBlockEntity(TickingBlockEntity ticker) {
		ParallelProcessor.callBlockEntityTick(ticker, (Level) (Object) this);
	}
}
