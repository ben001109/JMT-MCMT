package org.jmt.mcmt.fabric;

import java.nio.file.Path;
import java.util.List;

import org.jmt.mcmt.config.ConfigStore;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;

/** TOML-backed store using night-config (bundled into the Fabric jar). */
public class FabricConfigStore implements ConfigStore {

	private final CommentedFileConfig cfg;

	public FabricConfigStore(Path file) {
		cfg = CommentedFileConfig.builder(file).preserveInsertionOrder().sync().build();
		cfg.load();
	}

	@Override public boolean getBoolean(String key, boolean def) { return cfg.getOrElse(key, def); }
	@Override public int getInt(String key, int def) { return cfg.getIntOrElse(key, def); }
	@Override public String getString(String key, String def) { return cfg.getOrElse(key, def); }
	@Override public List<String> getStringList(String key, List<String> def) { return cfg.getOrElse(key, def); }

	@Override public void setBoolean(String key, boolean value) { cfg.set(key, value); }
	@Override public void setInt(String key, int value) { cfg.set(key, value); }
	@Override public void setString(String key, String value) { cfg.set(key, value); }
	@Override public void setStringList(String key, List<String> value) { cfg.set(key, value); }

	@Override public void save() { cfg.save(); }

	/** Writes defaults for any key missing from the file so users see every option. */
	public void writeDefaultsIfMissing(java.util.Map<String, Object> defaults) {
		boolean changed = false;
		for (var e : defaults.entrySet()) {
			if (!cfg.contains(e.getKey())) {
				cfg.set(e.getKey(), e.getValue());
				changed = true;
			}
		}
		if (changed) cfg.save();
	}
}
