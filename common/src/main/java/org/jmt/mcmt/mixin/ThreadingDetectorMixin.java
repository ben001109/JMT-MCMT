package org.jmt.mcmt.mixin;

import java.util.concurrent.locks.Lock;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.util.ThreadingDetector;

/**
 * Vanilla's {@code ThreadingDetector} crashes the game when two threads touch a guarded object
 * ({@code PalettedContainer}, {@code LegacyRandomSource}). Under MCMT that is expected, so the
 * detector becomes a real reentrant lock: the second thread waits instead of crashing. The
 * existing {@code stackTraceLock} field (a {@code ReentrantLock}) is reused as that lock.
 */
@Mixin(ThreadingDetector.class)
public abstract class ThreadingDetectorMixin {

	@Shadow @Final private Lock stackTraceLock;

	@Inject(method = "checkAndLock", at = @At("HEAD"), cancellable = true)
	private void mcmt$lock(CallbackInfo ci) {
		this.stackTraceLock.lock();
		ci.cancel();
	}

	@Inject(method = "checkAndUnlock", at = @At("HEAD"), cancellable = true)
	private void mcmt$unlock(CallbackInfo ci) {
		this.stackTraceLock.unlock();
		ci.cancel();
	}
}
