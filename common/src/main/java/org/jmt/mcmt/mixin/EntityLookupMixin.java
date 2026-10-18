package org.jmt.mcmt.mixin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jmt.mcmt.paralelised.fastutil.Int2ObjectConcurrentHashMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;

/** Entity id/uuid indexes are written from worker threads (spawn, discard) while others read them. */
@Mixin(EntityLookup.class)
public abstract class EntityLookupMixin<T extends EntityAccess> {

	@Shadow @Final @Mutable private Int2ObjectMap<T> byId;
	@Shadow @Final @Mutable private Map<UUID, T> byUuid;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void mcmt$useConcurrentCollections(CallbackInfo ci) {
		Int2ObjectMap<T> ids = new Int2ObjectConcurrentHashMap<>();
		ids.putAll(this.byId);
		this.byId = ids;
		this.byUuid = new ConcurrentHashMap<>(this.byUuid);
	}
}
