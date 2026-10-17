package org.jmt.mcmt;

public final class MCMTVersion {
	private MCMTVersion() {}

	public static String get() {
		Package p = MCMTVersion.class.getPackage();
		String v = p == null ? null : p.getImplementationVersion();
		return v == null ? "dev" : v;
	}
}
