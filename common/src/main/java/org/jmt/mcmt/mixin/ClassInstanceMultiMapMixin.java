package org.jmt.mcmt.mixin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.util.ClassInstanceMultiMap;

/**
 * Replaces {@code gen2parallel.js}: entity sections are read by worker threads while other workers
 * move entities between sections, so every backing collection must be concurrent. That includes
 * the per-class sub-lists that {@code find} creates lazily.
 */
@Mixin(ClassInstanceMultiMap.class)
public abstract class ClassInstanceMultiMapMixin<T> {

	@Shadow @Final @Mutable private Map<Class<?>, List<T>> byClass;
	@Shadow @Final @Mutable private List<T> allInstances;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void mcmt$useConcurrentCollections(Class<T> baseClass, CallbackInfo ci) {
		this.allInstances = new CopyOnWriteArrayList<>(this.allInstances);
		Map<Class<?>, List<T>> map = new ConcurrentHashMap<>();
		map.put(baseClass, this.allInstances);
		this.byClass = map;
	}

	/** The lazily built per-class list must be copy-on-write too, or iterating it races with add/remove. */
	@SuppressWarnings("unchecked")
	@Redirect(method = "find",
			at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
	private Object mcmt$computeConcurrentSubList(Map<Class<?>, List<T>> map, Object key, Function<Class<?>, List<T>> vanillaFactory) {
		return map.computeIfAbsent((Class<?>) key, c -> {
			List<T> built = vanillaFactory.apply(c);
			return built instanceof CopyOnWriteArrayList ? built : new CopyOnWriteArrayList<>(built);
		});
	}
}
