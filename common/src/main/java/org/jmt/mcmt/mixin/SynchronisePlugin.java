package org.jmt.mcmt.mixin;

import java.util.List;
import java.util.Set;

import org.jmt.mcmt.Constants;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin config plugin that adds {@code ACC_SYNCHRONIZED} to every public, non-static method of the
 * targets of the marker Mixins listed in {@link #SYNCHRONISE_ALL}. This replaces the 1.18
 * {@code synchronizeClass} coremod helper without needing a Mixin method per target method, and
 * works on both loaders because it never refers to method names.
 */
public final class SynchronisePlugin implements IMixinConfigPlugin {

	private static final Set<String> SYNCHRONISE_ALL = Set.of(
			"org.jmt.mcmt.mixin.EntityTickListMixin",
			"org.jmt.mcmt.mixin.LevelTicksMixin"
	);

	private static final int POSITIVE = Opcodes.ACC_PUBLIC;
	private static final int NEGATIVE = Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_NATIVE
			| Opcodes.ACC_ABSTRACT | Opcodes.ACC_BRIDGE;

	@Override
	public void onLoad(String mixinPackage) {}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		if (!SYNCHRONISE_ALL.contains(mixinClassName)) {
			return;
		}
		int count = 0;
		for (MethodNode mn : targetClass.methods) {
			if ((mn.access & POSITIVE) == POSITIVE && (mn.access & NEGATIVE) == 0
					&& !mn.name.equals("<init>") && !mn.name.startsWith("mcmt$")) {
				mn.access |= Opcodes.ACC_SYNCHRONIZED;
				count++;
			}
		}
		Constants.LOG.debug("MCMT synchronised {} methods of {}", count, targetClassName);
	}
}
