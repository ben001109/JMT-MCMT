package org.jmt.mcmt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.world.level.chunk.PalettedContainer;

/**
 * Block reads are lock-free in vanilla. While another worker writes to the same section the
 * palette can grow between the id lookup and the value lookup, which yields a {@code null}
 * block state (seen as an NPE in entity movement code). Instead of locking every read, retry a
 * null result under the container's own write lock, which {@link ThreadingDetectorMixin} made a
 * real lock.
 */
@Mixin(PalettedContainer.class)
public abstract class PalettedContainerMixin<T> {

	@Shadow public abstract void acquire();
	@Shadow public abstract void release();

	@WrapMethod(method = "get(III)Ljava/lang/Object;")
	private T mcmt$getWithRetry(int x, int y, int z, Operation<T> original) {
		T value = original.call(x, y, z);
		if (value != null) {
			return value;
		}
		acquire();
		try {
			return original.call(x, y, z);
		} finally {
			release();
		}
	}
}
