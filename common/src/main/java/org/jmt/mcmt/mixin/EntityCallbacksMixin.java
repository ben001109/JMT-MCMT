package org.jmt.mcmt.mixin;

import org.jmt.mcmt.duck.ServerLevelLocks;
import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.world.entity.Entity;

/**
 * {@code onTrackingStart}/{@code onTrackingEnd} check vanilla's {@code isUpdatingNavigations}
 * flag ("called during navigation iteration") and edit {@code navigatingMobs}. Entities spawn and
 * die on worker threads while another worker may be inside {@code sendBlockUpdated}, so both run
 * under the same per-level lock as that method (see {@link ServerLevelMixin}). The level is taken
 * from the entity: the synthetic outer-class field has loader-specific names.
 */
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class EntityCallbacksMixin {

	private static Object mcmt$lockFor(Entity entity) {
		return entity.level() instanceof ServerLevelLocks locks ? locks.mcmt$blockUpdateLock() : entity;
	}

	@WrapMethod(method = "onTrackingStart(Lnet/minecraft/world/entity/Entity;)V")
	private void mcmt$onTrackingStart(Entity entity, Operation<Void> original) {
		synchronized (mcmt$lockFor(entity)) {
			original.call(entity);
		}
	}

	@WrapMethod(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V")
	private void mcmt$onTrackingEnd(Entity entity, Operation<Void> original) {
		synchronized (mcmt$lockFor(entity)) {
			original.call(entity);
		}
	}
}
