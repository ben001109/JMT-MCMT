package org.jmt.mcmt.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Runtime configuration. Startup values live in a loader-provided {@link ConfigStore};
 * {@link #bake(ConfigStore)} copies store -> fields, {@link #save(ConfigStore)} copies fields -> store.
 * All fields are runtime-editable through the /mcmt config commands.
 */
public final class MCMTConfig {

	private MCMTConfig() {}

	public enum ParaMaxMode { Standard, Override, Reduction }

	// Keys (1.18 TOML paths, kept for compatibility with old config files)
	public static final String K_DISABLED = "general.disabled";
	public static final String K_PARAMAX = "general.parallelism.paraMax";
	public static final String K_PARAMAXMODE = "general.parallelism.paraMaxMode";
	public static final String K_DISABLE_WORLD = "world.disableWorld";
	public static final String K_DISABLE_WORLD_POSTTICK = "world.disableWorldPostTick";
	public static final String K_DISABLE_MULTICHUNK = "world.chunk.disableMultiChunk";
	public static final String K_DISABLE_ENTITY = "entity.disableEntity";
	public static final String K_DISABLE_TE = "te.disableTileEntity";
	public static final String K_CHUNKLOCK_MODDED = "te.chunkLockModded";
	public static final String K_TE_WHITELIST = "te.teWhiteList";
	public static final String K_TE_BLACKLIST = "te.teBlackList";
	public static final String K_DISABLE_ENV = "misc.disableEnvironment";
	public static final String K_DISABLE_SCP = "misc.disableChunkProvider";
	public static final String K_CHUNK_TIMEOUT = "debug.load-forcing.enableChunkTimeout";
	public static final String K_TIMEOUT_REGEN = "debug.load-forcing.enableTimeoutReload";
	public static final String K_BLANK_RETURN = "debug.load-forcing.enableBlankReturn";
	public static final String K_TIMEOUT_COUNT = "debug.load-forcing.timeoutCount";
	public static final String K_OPS_TRACING = "debug.ops-tracing.opsTracing";
	public static final String K_LOGCAP = "debug.logcap.logcap";

	// General
	public static volatile boolean disabled;

	// Parallelism
	public static volatile int paraMax;
	public static volatile ParaMaxMode paraMaxMode = ParaMaxMode.Standard;

	// World
	public static volatile boolean disableWorld;
	public static volatile boolean disableWorldPostTick;
	public static volatile boolean disableMultiChunk;

	// Entity
	public static volatile boolean disableEntity;

	// Block entity ("TE")
	public static volatile boolean disableTileEntity;
	public static volatile boolean chunkLockModded = true;
	public static Set<Class<?>> teWhiteList = ConcurrentHashMap.newKeySet();
	public static Set<Class<?>> teBlackList = ConcurrentHashMap.newKeySet();
	public static List<String> teUnfoundWhiteList = new ArrayList<>();
	public static List<String> teUnfoundBlackList = new ArrayList<>();

	// Misc
	public static volatile boolean disableEnvironment;
	public static volatile boolean disableChunkProvider;

	// Debug
	public static volatile boolean enableChunkTimeout;
	public static volatile boolean enableTimeoutRegen;
	public static volatile boolean enableBlankReturn;
	public static volatile int timeoutCount = 5000;
	public static volatile boolean opsTracing;
	public static volatile int logcap = 720000;

	public static int getParallelism() {
		int cores = Runtime.getRuntime().availableProcessors();
		switch (paraMaxMode) {
		case Standard:
			return paraMax <= 1 ? cores : Math.max(2, Math.min(cores, paraMax));
		case Override:
			return paraMax <= 1 ? cores : Math.max(2, paraMax);
		case Reduction:
			return Math.max(cores - Math.max(0, paraMax), 2);
		default:
			return cores;
		}
	}

	public static void bake(ConfigStore s) {
		disabled = s.getBoolean(K_DISABLED, false);
		paraMax = s.getInt(K_PARAMAX, -1);
		paraMaxMode = parseMode(s.getString(K_PARAMAXMODE, ParaMaxMode.Standard.name()));
		disableWorld = s.getBoolean(K_DISABLE_WORLD, false);
		disableWorldPostTick = s.getBoolean(K_DISABLE_WORLD_POSTTICK, false);
		disableMultiChunk = s.getBoolean(K_DISABLE_MULTICHUNK, false);
		disableEntity = s.getBoolean(K_DISABLE_ENTITY, false);
		disableTileEntity = s.getBoolean(K_DISABLE_TE, false);
		chunkLockModded = s.getBoolean(K_CHUNKLOCK_MODDED, true);
		disableEnvironment = s.getBoolean(K_DISABLE_ENV, false);
		disableChunkProvider = s.getBoolean(K_DISABLE_SCP, false);
		enableChunkTimeout = s.getBoolean(K_CHUNK_TIMEOUT, false);
		enableTimeoutRegen = s.getBoolean(K_TIMEOUT_REGEN, false);
		enableBlankReturn = s.getBoolean(K_BLANK_RETURN, false);
		timeoutCount = s.getInt(K_TIMEOUT_COUNT, 5000);
		opsTracing = s.getBoolean(K_OPS_TRACING, false);
		logcap = s.getInt(K_LOGCAP, 720000);

		Set<Class<?>> wl = ConcurrentHashMap.newKeySet();
		List<String> wlUnfound = new ArrayList<>();
		resolveClasses(s.getStringList(K_TE_WHITELIST, List.of()), wl, wlUnfound);
		teWhiteList = wl;
		teUnfoundWhiteList = wlUnfound;

		Set<Class<?>> bl = ConcurrentHashMap.newKeySet();
		List<String> blUnfound = new ArrayList<>();
		resolveClasses(s.getStringList(K_TE_BLACKLIST, List.of()), bl, blUnfound);
		teBlackList = bl;
		teUnfoundBlackList = blUnfound;
	}

	public static void save(ConfigStore s) {
		s.setBoolean(K_DISABLED, disabled);
		s.setInt(K_PARAMAX, paraMax);
		s.setString(K_PARAMAXMODE, paraMaxMode.name());
		s.setBoolean(K_DISABLE_WORLD, disableWorld);
		s.setBoolean(K_DISABLE_WORLD_POSTTICK, disableWorldPostTick);
		s.setBoolean(K_DISABLE_MULTICHUNK, disableMultiChunk);
		s.setBoolean(K_DISABLE_ENTITY, disableEntity);
		s.setBoolean(K_DISABLE_TE, disableTileEntity);
		s.setBoolean(K_CHUNKLOCK_MODDED, chunkLockModded);
		s.setBoolean(K_DISABLE_ENV, disableEnvironment);
		s.setBoolean(K_DISABLE_SCP, disableChunkProvider);
		s.setBoolean(K_CHUNK_TIMEOUT, enableChunkTimeout);
		s.setBoolean(K_TIMEOUT_REGEN, enableTimeoutRegen);
		s.setBoolean(K_BLANK_RETURN, enableBlankReturn);
		s.setInt(K_TIMEOUT_COUNT, timeoutCount);
		s.setBoolean(K_OPS_TRACING, opsTracing);
		s.setInt(K_LOGCAP, logcap);
		s.setStringList(K_TE_WHITELIST, classNames(teWhiteList, teUnfoundWhiteList));
		s.setStringList(K_TE_BLACKLIST, classNames(teBlackList, teUnfoundBlackList));
		s.save();
	}

	private static ParaMaxMode parseMode(String name) {
		for (ParaMaxMode m : ParaMaxMode.values()) {
			if (m.name().equalsIgnoreCase(name)) return m;
		}
		return ParaMaxMode.Standard;
	}

	private static void resolveClasses(List<String> names, Set<Class<?>> found, List<String> unfound) {
		for (String n : names) {
			try {
				found.add(Class.forName(n));
			} catch (ClassNotFoundException e) {
				unfound.add(n);
			}
		}
	}

	private static List<String> classNames(Set<Class<?>> classes, List<String> unfound) {
		List<String> out = new ArrayList<>(unfound);
		out.addAll(classes.stream().map(Class::getName).sorted().collect(Collectors.toList()));
		return out;
	}
}
