package org.jmt.mcmt.config;

import java.util.List;

/**
 * Loader-side persistence for {@link MCMTConfig}. Keys are the 1.18 TOML paths
 * (e.g. "general.disabled", "general.parallelism.paraMax") so old files keep working.
 */
public interface ConfigStore {
	boolean getBoolean(String key, boolean def);
	int getInt(String key, int def);
	String getString(String key, String def);
	List<String> getStringList(String key, List<String> def);

	void setBoolean(String key, boolean value);
	void setInt(String key, int value);
	void setString(String key, String value);
	void setStringList(String key, List<String> value);

	/** Write pending changes to disk. */
	void save();
}
