package org.jmt.mcmt.platform;

import java.util.ServiceLoader;

import org.jmt.mcmt.Constants;

public final class Platform {
	public static final PlatformHooks HOOKS = load(PlatformHooks.class);

	private Platform() {}

	public static <T> T load(Class<T> clazz) {
		T service = ServiceLoader.load(clazz).findFirst()
				.orElseThrow(() -> new IllegalStateException("No implementation of " + clazz.getName() + " on the classpath"));
		Constants.LOG.debug("Loaded {} for service {}", service, clazz.getSimpleName());
		return service;
	}
}
