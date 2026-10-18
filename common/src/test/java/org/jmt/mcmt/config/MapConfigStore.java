package org.jmt.mcmt.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** In-memory ConfigStore for tests. */
public class MapConfigStore implements ConfigStore {
	public final Map<String, Object> values = new HashMap<>();
	public int saveCount = 0;

	@Override public boolean getBoolean(String key, boolean def) { return (boolean) values.getOrDefault(key, def); }
	@Override public int getInt(String key, int def) { return (int) values.getOrDefault(key, def); }
	@Override public String getString(String key, String def) { return (String) values.getOrDefault(key, def); }
	@SuppressWarnings("unchecked")
	@Override public List<String> getStringList(String key, List<String> def) { return (List<String>) values.getOrDefault(key, def); }
	@Override public void setBoolean(String key, boolean value) { values.put(key, value); }
	@Override public void setInt(String key, int value) { values.put(key, value); }
	@Override public void setString(String key, String value) { values.put(key, value); }
	@Override public void setStringList(String key, List<String> value) { values.put(key, value); }
	@Override public void save() { saveCount++; }
}
