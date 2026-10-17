package org.jmt.mcmt.paralelised;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.NeighborUpdater;

/**
 * Vanilla's {@code CollectingNeighborUpdater} keeps a per-level stack of pending neighbour
 * updates and is not thread-safe. Block changes now happen on several worker threads at once, so
 * each thread gets its own collecting updater. Neighbour update ordering is preserved per thread,
 * which matches what a single-threaded tick would have done for that thread's block change.
 */
public final class ThreadLocalNeighborUpdater implements NeighborUpdater {

	private final ThreadLocal<NeighborUpdater> perThread;

	public ThreadLocalNeighborUpdater(Supplier<NeighborUpdater> factory) {
		this.perThread = ThreadLocal.withInitial(factory);
	}

	private NeighborUpdater delegate() {
		return perThread.get();
	}

	@Override
	public void shapeUpdate(Direction direction, BlockState state, BlockPos pos, BlockPos neighborPos, int flags, int recursionLeft) {
		delegate().shapeUpdate(direction, state, pos, neighborPos, flags, recursionLeft);
	}

	@Override
	public void neighborChanged(BlockPos pos, Block block, BlockPos fromPos) {
		delegate().neighborChanged(pos, block, fromPos);
	}

	@Override
	public void neighborChanged(BlockState state, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
		delegate().neighborChanged(state, pos, block, fromPos, movedByPiston);
	}

	@Override
	public void updateNeighborsAtExceptFromFacing(BlockPos pos, Block block, @Nullable Direction except) {
		delegate().updateNeighborsAtExceptFromFacing(pos, block, except);
	}
}
