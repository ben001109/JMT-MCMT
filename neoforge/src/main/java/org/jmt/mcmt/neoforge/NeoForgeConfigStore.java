package org.jmt.mcmt.neoforge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jmt.mcmt.config.ConfigStore;
import org.jmt.mcmt.config.MCMTConfig;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@link ModConfigSpec} with the 1.18 key layout, exposed through {@link ConfigStore}. */
public class NeoForgeConfigStore implements ConfigStore {

	public final ModConfigSpec spec;
	private final Map<String, ModConfigSpec.ConfigValue<?>> values = new HashMap<>();

	public NeoForgeConfigStore() {
		ModConfigSpec.Builder b = new ModConfigSpec.Builder();
		b.push("general");
		bool(b, MCMTConfig.K_DISABLED, "Globally disable all toggleable functionality", false);
		b.push("parallelism");
		integer(b, MCMTConfig.K_PARAMAX, "Thread count config; In standard mode: will never create more threads\nthan there are CPU threads (as that causes context switch churning)\nValues <=1 are treated as 'all cores'", -1, -1, Integer.MAX_VALUE);
		string(b, MCMTConfig.K_PARAMAXMODE, "Other modes for paraMax\nOverride: Standard but without the CoreCount ceiling\nReduction: Parallelism becomes Math.max(CoreCount-paraMax, 2)", "Standard");
		b.pop(2);
		b.push("world");
		bool(b, MCMTConfig.K_DISABLE_WORLD, "Disable world parallelisation", false);
		bool(b, MCMTConfig.K_DISABLE_WORLD_POSTTICK, "Serialise world post tick events", false);
		b.push("chunk");
		bool(b, MCMTConfig.K_DISABLE_MULTICHUNK, "Disable world parallel chunk loading", false);
		b.pop(2);
		b.push("entity");
		bool(b, MCMTConfig.K_DISABLE_ENTITY, "Disable entity parallelisation", false);
		b.pop();
		b.push("te");
		bool(b, MCMTConfig.K_DISABLE_TE, "Disable block entity parallelisation", false);
		bool(b, MCMTConfig.K_CHUNKLOCK_MODDED, "Use chunklocks for any unknown (i.e. modded) block entities", true);
		list(b, MCMTConfig.K_TE_WHITELIST, "Block entity classes that will always be fully parallelised");
		list(b, MCMTConfig.K_TE_BLACKLIST, "Block entity classes that will always be chunklocked");
		b.pop();
		b.push("misc");
		bool(b, MCMTConfig.K_DISABLE_ENV, "Disable environment (random ticks, weather) parallelisation", false);
		bool(b, MCMTConfig.K_DISABLE_SCP, "Disable parallelised chunk caching", false);
		b.pop();
		b.push("debug");
		b.push("load-forcing");
		bool(b, MCMTConfig.K_CHUNK_TIMEOUT, "Enable chunk loading timeouts", false);
		bool(b, MCMTConfig.K_TIMEOUT_REGEN, "Attempt to re-load timed out chunks", false);
		bool(b, MCMTConfig.K_BLANK_RETURN, "Return an empty chunk instead of regenerating", false);
		integer(b, MCMTConfig.K_TIMEOUT_COUNT, "Workless iterations (~100us) before a chunk load is declared timed out", 5000, 500, 500000);
		b.pop();
		b.push("ops-tracing");
		bool(b, MCMTConfig.K_OPS_TRACING, "Enable ops tracing for debugging lockups (performance cost)", false);
		b.pop();
		b.push("logcap");
		integer(b, MCMTConfig.K_LOGCAP, "Maximum time between MCMT presence alerts in 10ms steps", 720000, 15000, Integer.MAX_VALUE);
		b.pop(2);
		spec = b.build();
	}

	private static String leaf(String key) {
		return key.substring(key.lastIndexOf('.') + 1);
	}

	private void bool(ModConfigSpec.Builder b, String key, String comment, boolean def) {
		values.put(key, b.comment(comment).define(leaf(key), def));
	}

	private void integer(ModConfigSpec.Builder b, String key, String comment, int def, int min, int max) {
		values.put(key, b.comment(comment).defineInRange(leaf(key), def, min, max));
	}

	private void string(ModConfigSpec.Builder b, String key, String comment, String def) {
		values.put(key, b.comment(comment).define(leaf(key), def));
	}

	private void list(ModConfigSpec.Builder b, String key, String comment) {
		values.put(key, b.comment(comment).defineListAllowEmpty(leaf(key), new ArrayList<String>(), () -> "", o -> o instanceof String));
	}

	@SuppressWarnings("unchecked")
	private <T> ModConfigSpec.ConfigValue<T> v(String key) {
		ModConfigSpec.ConfigValue<T> cv = (ModConfigSpec.ConfigValue<T>) values.get(key);
		if (cv == null) throw new IllegalArgumentException("Unknown MCMT config key " + key);
		return cv;
	}

	private boolean loaded() {
		return spec.isLoaded();
	}

	@Override public boolean getBoolean(String key, boolean def) { return loaded() ? this.<Boolean>v(key).get() : def; }
	@Override public int getInt(String key, int def) { return loaded() ? this.<Integer>v(key).get() : def; }
	@Override public String getString(String key, String def) { return loaded() ? this.<String>v(key).get() : def; }
	@Override public List<String> getStringList(String key, List<String> def) {
		return loaded() ? new ArrayList<>(this.<List<? extends String>>v(key).get()) : def;
	}

	@Override public void setBoolean(String key, boolean value) { if (loaded()) this.<Boolean>v(key).set(value); }
	@Override public void setInt(String key, int value) { if (loaded()) this.<Integer>v(key).set(value); }
	@Override public void setString(String key, String value) { if (loaded()) this.<String>v(key).set(value); }
	@Override public void setStringList(String key, List<String> value) {
		if (loaded()) this.<List<? extends String>>v(key).set(new ArrayList<>(value));
	}

	@Override public void save() { if (loaded()) spec.save(); }
}
