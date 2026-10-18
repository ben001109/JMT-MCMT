package org.jmt.mcmt.autotest;

import net.minecraft.client.Minecraft;

/**
 * Kept in its own class so dedicated servers never load {@code net.minecraft.client}: it is only
 * referenced from the integrated-server branch of {@link AutoTest}.
 */
final class ClientShutdown {

	private ClientShutdown() {}

	static void stop() {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(mc::stop);
	}
}
