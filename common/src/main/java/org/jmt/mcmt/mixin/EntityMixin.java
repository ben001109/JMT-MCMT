package org.jmt.mcmt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Entities read each other's state while ticking on different threads (collision pushing calls
 * {@code other.isPushable()} -> {@code onClimbable()} -> {@code getInBlockState()}). Vanilla's
 * lazy cache re-reads the field after filling it, so a concurrent {@code setPosRaw} that clears
 * the cache makes it return {@code null}. Return the locally computed value instead.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

	@Shadow private BlockState inBlockState;
	@Shadow public abstract Level level();
	@Shadow public abstract BlockPos blockPosition();

	@Inject(method = "getInBlockState", at = @At("HEAD"), cancellable = true)
	private void mcmt$raceFreeInBlockState(CallbackInfoReturnable<BlockState> cir) {
		BlockState state = this.inBlockState;
		if (state == null) {
			state = level().getBlockState(blockPosition());
			this.inBlockState = state;
		}
		cir.setReturnValue(state);
	}
}
