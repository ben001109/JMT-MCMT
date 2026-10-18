package org.jmt.mcmt.neoforge.mixin;

import java.util.function.BooleanSupplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/**
 * NeoForge fires {@code LevelTickEvent.Post} right after {@code ServerLevel.tick} in
 * {@code tickChildren}. MCMT redirects that tick to a worker thread, so the loader's call would
 * fire before the level has actually ticked. It is dropped here and re-fired from
 * {@code NeoForgePlatformHooks.onLevelPostTick} on the thread that ran the tick.
 */
@Mixin(MinecraftServer.class)
public abstract class NeoForgeMinecraftServerMixin {

	@Redirect(method = "tickChildren",
			at = @At(value = "INVOKE",
					target = "Lnet/neoforged/neoforge/event/EventHooks;fireLevelTickPost(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V"),
			remap = false)
	private void mcmt$dropEarlyPostTick(Level level, BooleanSupplier hasTimeLeft) {
		// Intentionally empty; see class comment.
	}
}
