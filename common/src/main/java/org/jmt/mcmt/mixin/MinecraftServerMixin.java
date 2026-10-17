package org.jmt.mcmt.mixin;

import java.util.function.BooleanSupplier;

import org.jmt.mcmt.ParallelProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;

/**
 * Replaces the 1.18 {@code worldparallel.js} and {@code etpparallel.js} coremods.
 * <ul>
 * <li>{@code preTick} runs when the profiler section switches to "levels", before the level loop.</li>
 * <li>Each {@code ServerLevel.tick} call is redirected to the worker pool.</li>
 * <li>{@code postTick} runs when the profiler switches to "connection", after the loop, and waits
 * for every task submitted during the tick.</li>
 * <li>{@link #isSameThread()} treats MCMT worker threads as the server thread so tasks posted from
 * them run inline instead of being queued behind the blocked main thread.</li>
 * </ul>
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin extends ReentrantBlockableEventLoop<TickTask> {

	protected MinecraftServerMixin(String name) {
		super(name);
	}

	@Inject(method = "tickChildren",
			at = @At(value = "INVOKE_STRING",
					target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V",
					args = "ldc=levels"))
	private void mcmt$preTick(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
		ParallelProcessor.preTick((MinecraftServer) (Object) this);
	}

	@Redirect(method = "tickChildren",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerLevel;tick(Ljava/util/function/BooleanSupplier;)V"))
	private void mcmt$tickLevel(ServerLevel level, BooleanSupplier hasTimeLeft) {
		ParallelProcessor.callTick(level, hasTimeLeft, (MinecraftServer) (Object) this);
	}

	@Inject(method = "tickChildren",
			at = @At(value = "INVOKE_STRING",
					target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V",
					args = "ldc=connection"))
	private void mcmt$postTick(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
		ParallelProcessor.postTick((MinecraftServer) (Object) this);
	}

	@Override
	public boolean isSameThread() {
		return super.isSameThread() || ParallelProcessor.isThreadPooled("MCMT", Thread.currentThread());
	}
}
