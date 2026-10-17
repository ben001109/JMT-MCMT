# Phase 1: Multiloader Skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the `1.21.1` branch into a `common` / `fabric` / `neoforge` Gradle build that produces two mod jars which start a 1.21.1 dedicated server, load config, answer `/mcmt` commands, and pass a scripted smoke test, with the thread pool and SerDes registry ported but no Mixin hooks yet.

**Architecture:** Follows the CC0 MultiLoader-Template layout: `common` compiles against vanilla via ModDevGradle (NeoForm) with MojMap names; `fabric` and `neoforge` compile the `common` sources into their own jars. Loader-specific behaviour (config storage, command registration, lifecycle) sits behind a `PlatformHooks` service interface. All 1.18 Java logic is copied from the `1.18.2-forge-legacy` branch and edited; the JS coremods and `sync-fu` are deleted.

**Tech Stack:** Gradle 8.10 wrapper, Java 21, ModDevGradle 2.0.x, fabric-loom 1.9, NeoForge 21.1.x, Fabric Loader 0.16.x+, Fabric API (command + lifecycle), Mixin 0.8.5 (compile only in phase 1), night-config core+toml (bundled into the Fabric jar), JUnit 5, PowerShell + bash smoke scripts.

Spec: `docs/superpowers/specs/2026-10-18-port-1.21.1-multiloader-design.md`.

**Deviation from spec, recorded here:** the spec said Fabric would get a hand-written TOML reader. `SerDesConfig` already depends on night-config (bundled by NeoForge). Instead of two TOML stacks, `common` depends on `com.electronwill.night-config:core` and `:toml` 3.8.x, and the Fabric jar includes them (jar-in-jar). The Fabric jar remains standalone. Section 4.3 of the spec is amended accordingly in Task 1.

---

## File structure after phase 1

```
settings.gradle                         pluginManagement repos, include common/fabric/neoforge
build.gradle                            plugin versions (apply false)
gradle.properties                       all versions
gradle/wrapper/gradle-wrapper.properties  8.10
buildSrc/build.gradle
buildSrc/src/main/groovy/multiloader-common.gradle
buildSrc/src/main/groovy/multiloader-loader.gradle
common/build.gradle
common/src/main/java/org/jmt/mcmt/Constants.java
common/src/main/java/org/jmt/mcmt/MCMTCommon.java              shared init sequence
common/src/main/java/org/jmt/mcmt/ParallelProcessor.java       ex-ASMHookTerminator
common/src/main/java/org/jmt/mcmt/config/MCMTConfig.java       runtime config values + bake/save via ConfigStore
common/src/main/java/org/jmt/mcmt/config/ConfigStore.java      interface loaders implement
common/src/main/java/org/jmt/mcmt/config/SerDesConfig.java     ported, FMLPaths -> PlatformHooks
common/src/main/java/org/jmt/mcmt/platform/PlatformHooks.java
common/src/main/java/org/jmt/mcmt/platform/Platform.java       ServiceLoader holder
common/src/main/java/org/jmt/mcmt/commands/{ConfigCommand,StatsCommand,DebugCommands}.java
common/src/main/java/org/jmt/mcmt/serdes/**                    ported unchanged except imports
common/src/main/java/org/jmt/mcmt/paralelised/**               ported unchanged except imports (no ParaServerChunkProvider yet)
common/src/main/java/org/jmt/mcmt/util/ConcurrentCollections.java
common/src/main/java/org/jmt/mcmt/mixin/                       empty package + jmt_mcmt.mixins.json with no entries
common/src/main/resources/jmt_mcmt.mixins.json
common/src/main/resources/pack.mcmeta
common/src/test/java/org/jmt/mcmt/**                           JUnit tests
fabric/build.gradle
fabric/src/main/java/org/jmt/mcmt/fabric/MCMTFabric.java
fabric/src/main/java/org/jmt/mcmt/fabric/FabricPlatformHooks.java
fabric/src/main/java/org/jmt/mcmt/fabric/FabricConfigStore.java
fabric/src/main/resources/fabric.mod.json
fabric/src/main/resources/META-INF/services/org.jmt.mcmt.platform.PlatformHooks
neoforge/build.gradle
neoforge/src/main/java/org/jmt/mcmt/neoforge/MCMTNeoForge.java
neoforge/src/main/java/org/jmt/mcmt/neoforge/NeoForgePlatformHooks.java
neoforge/src/main/java/org/jmt/mcmt/neoforge/NeoForgeConfigStore.java
neoforge/src/main/resources/META-INF/neoforge.mods.toml
neoforge/src/main/resources/META-INF/services/org.jmt.mcmt.platform.PlatformHooks
tests/smoke/run-smoke.ps1
tests/smoke/run-smoke.sh
tests/smoke/commands.txt
tests/smoke/allowlist.txt
.github/workflows/build.yml
```

Conventions used by every task:

- Source for ported files: `git show 1.18.2-forge-legacy:forge-mod/src/main/java/org/jmt/mcmt/<path>`.
- All commands run from the repo root `E:\work\Mod porting\MCMT\JMT-MCMT` on branch `1.21.1`.
- Gradle is invoked as `./gradlew` (Git Bash) with `JAVA_HOME` pointing at JDK 21: `export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"`.
- Commit after every task with the trailer `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

---

### Task 1: Remove the 1.18 build and lay down the Gradle skeleton

**Files:**
- Delete: `forge-mod/`, `sync-fu/`, `etc/`, `build.gradle`, `settings.gradle`, `gradle.properties`, `README.MD` (replaced later)
- Create: `settings.gradle`, `build.gradle`, `gradle.properties`, `buildSrc/build.gradle`, `buildSrc/src/main/groovy/multiloader-common.gradle`, `buildSrc/src/main/groovy/multiloader-loader.gradle`, `common/build.gradle`, `fabric/build.gradle`, `neoforge/build.gradle`, `.gitignore`
- Modify: `gradle/wrapper/gradle-wrapper.properties`, `docs/superpowers/specs/2026-10-18-port-1.21.1-multiloader-design.md` (section 4.3 amendment)

- [ ] **Step 1: Delete the old tree**

```bash
git rm -rq forge-mod sync-fu etc build.gradle settings.gradle gradle.properties README.MD
```

- [ ] **Step 2: Write `settings.gradle`**

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        exclusiveContent {
            forRepository { maven { name = 'Fabric'; url = uri('https://maven.fabricmc.net') } }
            filter { includeGroup('net.fabricmc'); includeGroup('fabric-loom') }
        }
        exclusiveContent {
            forRepository { maven { name = 'Sponge'; url = uri('https://repo.spongepowered.org/repository/maven-public') } }
            filter { includeGroupAndSubgroups('org.spongepowered') }
        }
        exclusiveContent {
            forRepository { maven { name = 'NeoForge'; url = uri('https://maven.neoforged.net/releases') } }
            filter { includeGroupAndSubgroups('net.neoforged') }
        }
    }
}

plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
}

rootProject.name = 'jmt-mcmt'
include('common')
include('fabric')
include('neoforge')
```

- [ ] **Step 3: Write root `build.gradle`**

```groovy
plugins {
    id 'fabric-loom' version '1.9-SNAPSHOT' apply false
    id 'net.neoforged.moddev' version '2.0.148' apply false
}

// Build both loader jars with a single task.
tasks.register('buildAll') {
    group = 'build'
    description = 'Builds the Fabric and NeoForge jars'
    dependsOn(':fabric:build', ':neoforge:build')
}
```

- [ ] **Step 4: Write `gradle.properties`**

```properties
# Project
version=0.25.0-alpha.1
group=org.jmt.mcmt
java_version=21

# Common
minecraft_version=1.21.1
mod_name=MCMT
mod_author=jediminer543
mod_id=jmt_mcmt
license=BSD-2-Clause
description=Minecraft Multi-Threading mod: parallelises world, entity, block entity and environment ticks on the server.
minecraft_version_range=[1.21.1,1.21.2)
neo_form_version=1.21.1-20240808.144430

# Fabric
fabric_version=0.116.17+1.21.1
fabric_loader_version=0.16.4

# NeoForge
neoforge_version=21.1.262
neoforge_loader_version_range=[4,)

# Libraries
nightconfig_version=3.8.1
mixin_version=0.8.5
mixinextras_version=0.4.1

# Gradle
org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=false
org.gradle.parallel=true
```

- [ ] **Step 5: Point the wrapper at Gradle 8.10**

Replace `gradle/wrapper/gradle-wrapper.properties` with:

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

Then regenerate the wrapper jar/scripts so they match (the old wrapper is Gradle 6):

```bash
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"
# Use the old wrapper once to bootstrap; if it fails on Java 21, download 8.10 manually:
curl -sL -o /tmp/gradle-8.10-bin.zip https://services.gradle.org/distributions/gradle-8.10-bin.zip
unzip -q -o /tmp/gradle-8.10-bin.zip -d /tmp/gradle-8.10
/tmp/gradle-8.10/gradle-8.10/bin/gradle wrapper --gradle-version 8.10 --no-daemon
```

Expected: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` updated; `./gradlew --version` prints `Gradle 8.10`.

- [ ] **Step 6: Write `buildSrc/build.gradle`**

```groovy
plugins {
    id 'groovy-gradle-plugin'
}
```

- [ ] **Step 7: Write `buildSrc/src/main/groovy/multiloader-common.gradle`**

```groovy
plugins {
    id 'java-library'
}

base {
    archivesName = "${mod_id}-${project.name}-${minecraft_version}"
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(java_version)
    withSourcesJar()
}

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven { name = 'Sponge'; url = 'https://repo.spongepowered.org/repository/maven-public' } }
        filter { includeGroupAndSubgroups('org.spongepowered') }
    }
    maven { name = 'NeoForge'; url = 'https://maven.neoforged.net/releases' }
}

['apiElements', 'runtimeElements', 'sourcesElements'].each { variant ->
    configurations."$variant".outgoing {
        capability("$group:${base.archivesName.get()}:$version")
        capability("$group:$mod_id-${project.name}-${minecraft_version}:$version")
        capability("$group:$mod_id:$version")
    }
}

jar {
    from(rootProject.file('LICENCE')) { rename { "${it}_${mod_name}" } }
    manifest {
        attributes([
            'Specification-Title'   : mod_name,
            'Specification-Vendor'  : mod_author,
            'Specification-Version' : project.jar.archiveVersion,
            'Implementation-Title'  : project.name,
            'Implementation-Version': project.jar.archiveVersion,
            'Implementation-Vendor' : mod_author,
            'Built-On-Minecraft'    : minecraft_version
        ])
    }
}

processResources {
    var expandProps = [
        'version'                      : version,
        'group'                        : project.group,
        'minecraft_version'            : minecraft_version,
        'minecraft_version_range'      : minecraft_version_range,
        'fabric_version'               : fabric_version,
        'fabric_loader_version'        : fabric_loader_version,
        'mod_name'                     : mod_name,
        'mod_author'                   : mod_author,
        'mod_id'                       : mod_id,
        'license'                      : license,
        'description'                  : description,
        'neoforge_version'             : neoforge_version,
        'neoforge_loader_version_range': neoforge_loader_version_range,
        'java_version'                 : java_version
    ]
    filesMatching(['pack.mcmeta', 'fabric.mod.json', 'META-INF/neoforge.mods.toml', '*.mixins.json']) {
        expand expandProps
    }
    inputs.properties(expandProps)
}

tasks.withType(Test).configureEach {
    useJUnitPlatform()
    testLogging { events 'failed'; exceptionFormat 'full' }
}
```

- [ ] **Step 8: Write `buildSrc/src/main/groovy/multiloader-loader.gradle`**

```groovy
plugins {
    id 'multiloader-common'
}

configurations {
    commonJava { canBeResolved = true }
    commonResources { canBeResolved = true }
}

dependencies {
    compileOnly(project(':common')) {
        capabilities { requireCapability "$group:$mod_id" }
    }
    commonJava project(path: ':common', configuration: 'commonJava')
    commonResources project(path: ':common', configuration: 'commonResources')
}

tasks.named('compileJava', JavaCompile) {
    dependsOn(configurations.commonJava)
    source(configurations.commonJava)
}

processResources {
    dependsOn(configurations.commonResources)
    from(configurations.commonResources)
}

tasks.named('sourcesJar', Jar) {
    dependsOn(configurations.commonJava)
    from(configurations.commonJava)
    dependsOn(configurations.commonResources)
    from(configurations.commonResources)
}
```

- [ ] **Step 9: Write `common/build.gradle`**

```groovy
plugins {
    id 'multiloader-common'
    id 'net.neoforged.moddev'
}

neoForge {
    neoFormVersion = neo_form_version
}

dependencies {
    compileOnly "org.spongepowered:mixin:${mixin_version}"
    compileOnly "io.github.llamalad7:mixinextras-common:${mixinextras_version}"
    annotationProcessor "io.github.llamalad7:mixinextras-common:${mixinextras_version}"

    // TOML for SerDes configs; NeoForge bundles it, Fabric includes it (see fabric/build.gradle).
    implementation "com.electronwill.night-config:core:${nightconfig_version}"
    implementation "com.electronwill.night-config:toml:${nightconfig_version}"

    testImplementation platform('org.junit:junit-bom:5.11.3')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

configurations {
    commonJava { canBeResolved = false; canBeConsumed = true }
    commonResources { canBeResolved = false; canBeConsumed = true }
}

artifacts {
    commonJava sourceSets.main.java.sourceDirectories.singleFile
    commonResources sourceSets.main.resources.sourceDirectories.singleFile
}
```

- [ ] **Step 10: Write `fabric/build.gradle`**

```groovy
plugins {
    id 'multiloader-loader'
    id 'fabric-loom'
}

dependencies {
    minecraft "com.mojang:minecraft:${minecraft_version}"
    mappings loom.officialMojangMappings()
    modImplementation "net.fabricmc:fabric-loader:${fabric_loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${fabric_version}"

    implementation "com.electronwill.night-config:core:${nightconfig_version}"
    implementation "com.electronwill.night-config:toml:${nightconfig_version}"
    include "com.electronwill.night-config:core:${nightconfig_version}"
    include "com.electronwill.night-config:toml:${nightconfig_version}"
}

loom {
    mixin {
        defaultRefmapName.set("${mod_id}.refmap.json")
    }
    runs {
        server {
            server()
            setConfigName('Fabric Server')
            ideConfigGenerated(true)
            runDir('runs/server')
        }
    }
}
```

- [ ] **Step 11: Write `neoforge/build.gradle`**

```groovy
plugins {
    id 'multiloader-loader'
    id 'net.neoforged.moddev'
}

neoForge {
    version = neoforge_version
    runs {
        server {
            server()
            gameDirectory = project.file('runs/server')
        }
    }
    mods {
        "${mod_id}" {
            sourceSet sourceSets.main
        }
    }
}
```

- [ ] **Step 12: Write `.gitignore`**

```gitignore
# Gradle
.gradle/
build/
buildSrc/.gradle/
buildSrc/build/
# IDE
.idea/
*.iml
.vscode/
.settings/
.classpath
.project
bin/
# Runs
runs/
run/
tests/smoke/out/
tests/smoke/work/
# OS
.DS_Store
Thumbs.db
```

- [ ] **Step 13: Amend spec section 4.3**

In `docs/superpowers/specs/2026-10-18-port-1.21.1-multiloader-design.md` replace the Fabric bullet under "Each loader supplies the storage" with:

```markdown
- Fabric: night-config (core + toml) bundled into the jar with Loom `include`,
  reading and writing `config/jmt_mcmt-common.toml` with the same keys as the
  NeoForge spec. No other external dependency.
```

- [ ] **Step 14: Verify Gradle configures with empty source sets**

```bash
mkdir -p common/src/main/java common/src/main/resources fabric/src/main/java fabric/src/main/resources neoforge/src/main/java neoforge/src/main/resources
./gradlew projects --no-daemon
```

Expected: lists `:common`, `:fabric`, `:neoforge`; no configuration errors. (First run downloads NeoForm/NeoForge and decompiles; allow 10+ minutes.)

- [ ] **Step 15: Commit**

```bash
git add -A
git commit -m "Replace 1.18 Forge build with multiloader Gradle skeleton

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: Platform abstraction and constants

**Files:**
- Create: `common/src/main/java/org/jmt/mcmt/Constants.java`
- Create: `common/src/main/java/org/jmt/mcmt/platform/PlatformHooks.java`
- Create: `common/src/main/java/org/jmt/mcmt/platform/Platform.java`
- Create: `common/src/main/java/org/jmt/mcmt/config/ConfigStore.java`

- [ ] **Step 1: `Constants.java`**

```java
package org.jmt.mcmt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Constants {
	public static final String MOD_ID = "jmt_mcmt";
	public static final String MOD_NAME = "MCMT";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

	private Constants() {}
}
```

Note: 1.21.1 ships SLF4J for both loaders; log4j is no longer a safe direct dependency on Fabric. Every ported file swaps `org.apache.logging.log4j.LogManager/Logger` for `org.slf4j.LoggerFactory/Logger`.

- [ ] **Step 2: `PlatformHooks.java`**

```java
package org.jmt.mcmt.platform;

import java.nio.file.Path;
import java.util.function.Supplier;

import net.minecraft.server.level.ServerLevel;

/**
 * The only loader-specific behaviour common code may call.
 * One implementation per loader, discovered through {@link java.util.ServiceLoader}.
 */
public interface PlatformHooks {

	String platformName();

	boolean isDevelopmentEnvironment();

	Path configDirectory();

	/** Fire the loader's "level finished ticking" event for a level ticked on a pool thread. */
	void onLevelPostTick(ServerLevel level);

	/** Add a named section to crash reports. Implementations may no-op if unsupported. */
	void registerCrashReportSection(String name, Supplier<String> content);
}
```

- [ ] **Step 3: `Platform.java`**

```java
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
```

- [ ] **Step 4: `ConfigStore.java`**

```java
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
```

- [ ] **Step 5: Compile**

```bash
./gradlew :common:compileJava --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add common
git commit -m "Add platform hooks, config store interface and constants

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: Port `MCMTConfig` (ex `GeneralConfig`) with tests

**Files:**
- Create: `common/src/main/java/org/jmt/mcmt/config/MCMTConfig.java`
- Test: `common/src/test/java/org/jmt/mcmt/config/MCMTConfigTest.java`
- Test helper: `common/src/test/java/org/jmt/mcmt/config/MapConfigStore.java`

- [ ] **Step 1: Write the test helper `MapConfigStore`**

```java
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
```

- [ ] **Step 2: Write the failing tests**

```java
package org.jmt.mcmt.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MCMTConfigTest {

	MapConfigStore store;

	@BeforeEach
	void setUp() {
		store = new MapConfigStore();
	}

	@Test
	void bakeReadsDefaultsWhenStoreEmpty() {
		MCMTConfig.bake(store);
		assertFalse(MCMTConfig.disabled);
		assertEquals(-1, MCMTConfig.paraMax);
		assertEquals(MCMTConfig.ParaMaxMode.Standard, MCMTConfig.paraMaxMode);
		assertTrue(MCMTConfig.chunkLockModded);
		assertEquals(720000, MCMTConfig.logcap);
	}

	@Test
	void bakeResolvesKnownClassesAndKeepsUnknownNames() {
		store.setStringList("general.te.teWhiteList", List.of("java.lang.String", "no.such.Clazz"));
		MCMTConfig.bake(store);
		assertTrue(MCMTConfig.teWhiteList.contains(String.class));
		assertEquals(List.of("no.such.Clazz"), MCMTConfig.teUnfoundWhiteList);
	}

	@Test
	void saveWritesRuntimeValuesBack() {
		MCMTConfig.bake(store);
		MCMTConfig.disabled = true;
		MCMTConfig.paraMax = 6;
		MCMTConfig.teBlackList.add(Integer.class);
		MCMTConfig.save(store);
		assertEquals(true, store.values.get("general.disabled"));
		assertEquals(6, store.values.get("general.parallelism.paraMax"));
		assertTrue(store.getStringList("general.te.teBlackList", List.of()).contains("java.lang.Integer"));
		assertEquals(1, store.saveCount);
	}

	@Test
	void parallelismStandardIsCappedByCores() {
		MCMTConfig.bake(store);
		int cores = Runtime.getRuntime().availableProcessors();
		MCMTConfig.paraMaxMode = MCMTConfig.ParaMaxMode.Standard;
		MCMTConfig.paraMax = cores + 10;
		assertEquals(Math.max(2, cores), MCMTConfig.getParallelism());
		MCMTConfig.paraMax = -1;
		assertEquals(cores, MCMTConfig.getParallelism());
	}

	@Test
	void parallelismOverrideIgnoresCoreCap() {
		MCMTConfig.bake(store);
		MCMTConfig.paraMaxMode = MCMTConfig.ParaMaxMode.Override;
		MCMTConfig.paraMax = 1000;
		assertEquals(1000, MCMTConfig.getParallelism());
	}

	@Test
	void parallelismReductionNeverBelowTwo() {
		MCMTConfig.bake(store);
		MCMTConfig.paraMaxMode = MCMTConfig.ParaMaxMode.Reduction;
		MCMTConfig.paraMax = 100000;
		assertEquals(2, MCMTConfig.getParallelism());
	}
}
```

- [ ] **Step 3: Run the tests to see them fail**

```bash
./gradlew :common:test --no-daemon
```

Expected: compilation failure, `MCMTConfig` does not exist.

- [ ] **Step 4: Write `MCMTConfig.java`**

```java
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
```

- [ ] **Step 5: Run the tests**

```bash
./gradlew :common:test --no-daemon
```

Expected: 6 tests pass.

- [ ] **Step 6: Commit**

```bash
git add common
git commit -m "Port GeneralConfig to loader-agnostic MCMTConfig with tests

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: Port the parallel collections and `ChunkLock` with tests

**Files:**
- Create (ported): `common/src/main/java/org/jmt/mcmt/paralelised/{ChunkLock,ConcurrentDoublyLinkedList,ConcurrentLinkedList,RunnableManagedBlocker}.java`, `paralelised/pooling/LockAwareThreadPool.java`, `paralelised/fastutil/**` (all 10 files)
- Create: `common/src/main/java/org/jmt/mcmt/util/ConcurrentCollections.java`
- Test: `common/src/test/java/org/jmt/mcmt/paralelised/ConcurrentDoublyLinkedListTest.java`, `ChunkLockTest.java`

- [ ] **Step 1: Copy the files from the legacy branch**

```bash
B=1.18.2-forge-legacy; S=forge-mod/src/main/java/org/jmt/mcmt; D=common/src/main/java/org/jmt/mcmt
mkdir -p $D/paralelised/pooling $D/paralelised/fastutil/sync $D/util
for f in ChunkLock ConcurrentDoublyLinkedList ConcurrentLinkedList RunnableManagedBlocker; do git show $B:$S/paralelised/$f.java > $D/paralelised/$f.java; done
git show $B:$S/paralelised/pooling/LockAwareThreadPool.java > $D/paralelised/pooling/LockAwareThreadPool.java
for f in $(git ls-tree -r --name-only $B | grep "$S/paralelised/fastutil/"); do git show $B:$f > $D/paralelised/fastutil/${f#$S/paralelised/fastutil/}; done
git show $B:$S/asmdest/ConcurrentCollections.java > $D/util/ConcurrentCollections.java
```

- [ ] **Step 2: Edit `ConcurrentCollections.java`**

Change the package line to `package org.jmt.mcmt.util;`. Replace the two log4j imports with `import org.slf4j.Logger; import org.slf4j.LoggerFactory;` and the field with `private static final Logger LOGGER = LoggerFactory.getLogger("MCMT");`. Change the three `LOGGER.info(...)` calls to `LOGGER.debug(...)` (they fire on every collection creation).

- [ ] **Step 3: Remove the commons-lang3 dependency**

`ChunkLock` uses `ArrayUtils.reverse`; `ConcurrentDoublyLinkedList`, `ConcurrentLinkedList`, `Int2ObjectConcurrentHashMap` use `NotImplementedException`; `FastUtilHackUtil` uses `ArrayUtils`. commons-lang3 is present at runtime on both loaders (vanilla depends on it), so keep the imports. No change.

- [ ] **Step 4: Write the failing `ConcurrentDoublyLinkedListTest`**

```java
package org.jmt.mcmt.paralelised;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

import org.junit.jupiter.api.Test;

class ConcurrentDoublyLinkedListTest {

	@Test
	void matchesLinkedListUnderRandomSingleThreadedOps() {
		Random r = new Random(42);
		for (int round = 0; round < 50; round++) {
			List<Integer> golden = new LinkedList<>();
			List<Integer> tested = new ConcurrentDoublyLinkedList<>();
			for (int j = 0; j < 1000; j++) {
				int value = r.nextInt(256);
				if (r.nextInt(4) == 0) {
					golden.remove((Integer) value);
					tested.remove((Integer) value);
				} else {
					golden.add(value);
					tested.add(value);
				}
			}
			assertEquals(golden, new ArrayList<>(tested), "round " + round);
		}
	}

	@Test
	void concurrentAddsAreAllRetained() throws Exception {
		ConcurrentDoublyLinkedList<Integer> list = new ConcurrentDoublyLinkedList<>();
		int threads = 8, perThread = 2000;
		ExecutorService ex = Executors.newFixedThreadPool(threads);
		List<Future<?>> fs = new ArrayList<>();
		for (int t = 0; t < threads; t++) {
			final int base = t * perThread;
			fs.add(ex.submit(() -> { for (int i = 0; i < perThread; i++) list.add(base + i); }));
		}
		for (Future<?> f : fs) f.get(30, TimeUnit.SECONDS);
		ex.shutdown();
		assertEquals(threads * perThread, list.size());
		assertEquals(threads * perThread, new ArrayList<>(list).stream().distinct().count());
	}
}
```

- [ ] **Step 5: Write the failing `ChunkLockTest`**

```java
package org.jmt.mcmt.paralelised;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import net.minecraft.world.level.ChunkPos;

class ChunkLockTest {

	@Test
	void lockReturnsSortedUniqueTargets() {
		ChunkLock cl = new ChunkLock();
		long[] locks = cl.lock(ChunkPos.asLong(10, 10), 1);
		assertEquals(9, locks.length);
		for (int i = 1; i < locks.length; i++) assertTrue(locks[i - 1] < locks[i]);
		cl.unlock(locks);
	}

	@Test
	void overlappingRegionsNeverRunConcurrently() throws Exception {
		ChunkLock cl = new ChunkLock();
		AtomicInteger inside = new AtomicInteger();
		AtomicInteger maxInside = new AtomicInteger();
		ExecutorService ex = Executors.newFixedThreadPool(8);
		CountDownLatch done = new CountDownLatch(8);
		for (int t = 0; t < 8; t++) {
			final int dx = t % 2; // all regions overlap at (0..1, 0)
			ex.submit(() -> {
				try {
					for (int i = 0; i < 200; i++) {
						long[] l = cl.lock(ChunkPos.asLong(dx, 0), 1);
						try {
							int now = inside.incrementAndGet();
							maxInside.accumulateAndGet(now, Math::max);
							Thread.yield();
							inside.decrementAndGet();
						} finally {
							cl.unlock(l);
						}
					}
				} finally {
					done.countDown();
				}
			});
		}
		assertTrue(done.await(60, TimeUnit.SECONDS), "deadlock: workers did not finish");
		ex.shutdown();
		assertEquals(1, maxInside.get());
	}
}
```

- [ ] **Step 6: Run tests**

```bash
./gradlew :common:test --no-daemon
```

Expected: all pass (the ported code is unchanged logic). If `concurrentAddsAreAllRetained` fails, record the failure in the plan's follow-up section and mark the test `@Disabled("known 1.18 race, see issue")`; do not alter the list in this phase.

- [ ] **Step 7: Commit**

```bash
git add common
git commit -m "Port parallel collections, fastutil wrappers and ChunkLock with tests

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: Port the SerDes system and `SerDesConfig`

**Files:**
- Create (ported): `common/src/main/java/org/jmt/mcmt/serdes/**` (12 files), `common/src/main/java/org/jmt/mcmt/config/SerDesConfig.java`
- Test: `common/src/test/java/org/jmt/mcmt/serdes/SerDesRegistryTest.java`

- [ ] **Step 1: Copy**

```bash
B=1.18.2-forge-legacy; S=forge-mod/src/main/java/org/jmt/mcmt; D=common/src/main/java/org/jmt/mcmt
mkdir -p $D/serdes/filter $D/serdes/pools
for f in $(git ls-tree -r --name-only $B | grep "$S/serdes/"); do git show $B:$f > $D/serdes/${f#$S/serdes/}; done
git show $B:$S/config/SerDesConfig.java > $D/config/SerDesConfig.java
```

- [ ] **Step 2: Edit `SerDesConfig.java`**

- Remove `import net.minecraftforge.fml.loading.FMLPaths;` and add `import org.jmt.mcmt.platform.Platform;`.
- Replace both occurrences of `FMLPaths.CONFIGDIR.get()` with `Platform.HOOKS.configDirectory()`.
- Replace `System.out.println("Saving config to " + ...)` with `org.jmt.mcmt.Constants.LOG.info("Saving SerDes filter config to {}", saveTo);`.
- Replace each `e.printStackTrace();` with `org.jmt.mcmt.Constants.LOG.error("SerDes config error", e);`.

- [ ] **Step 3: Edit `SerDesRegistry.java`**

- Replace log4j imports/field with SLF4J: `private static final Logger LOGGER = LoggerFactory.getLogger("MCMT");`.
- Replace `import org.jmt.mcmt.config.GeneralConfig;` with `import org.jmt.mcmt.config.MCMTConfig;` and every `GeneralConfig.` with `MCMTConfig.`.
- In `DefaultFilter.filterTE`, change the vanilla package check from `"net.minecraft.tileentity."` to `"net.minecraft.world.level.block.entity."` (MojMap package; the old string was an MCP name and never matched on 1.18).
- In `initPools`, collapse the seven `catch` blocks into one `catch (ReflectiveOperationException | RuntimeException e) { LOGGER.error("Failed to create SerDes pool {}", pc.getName(), e); }`.
- In `DefaultFilter.serialise`, the `LOGGER.error("Exception running " + ...)` lines become parameterised SLF4J calls.

- [ ] **Step 4: Edit `LegacyFilter.java`**

Replace `GeneralConfig` with `MCMTConfig` (import and both usages).

- [ ] **Step 5: Other serdes files**

`AutoFilter`, `GenericConfigFilter`, `PistonFilter`, `VanillaFilter`, `ISerDesFilter`, `ISerDesHookType`, `SerDesHookTypes`, pools: no edits needed. `javax.annotation.Nullable/Nonnull` are available on both loaders through `jsr305` (vanilla dependency); keep.

- [ ] **Step 6: Write the failing `SerDesRegistryTest`**

Note: `SerDesRegistry.init()` calls `SerDesConfig.loadConfigs()` which needs `Platform.HOOKS`. The test registers a test-only `PlatformHooks` through `META-INF/services` in `common/src/test/resources`.

Create `common/src/test/java/org/jmt/mcmt/platform/TestPlatformHooks.java`:

```java
package org.jmt.mcmt.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import net.minecraft.server.level.ServerLevel;

public class TestPlatformHooks implements PlatformHooks {
	private static final Path DIR;
	static {
		try { DIR = Files.createTempDirectory("mcmt-test-config"); }
		catch (IOException e) { throw new UncheckedIOException(e); }
	}
	@Override public String platformName() { return "test"; }
	@Override public boolean isDevelopmentEnvironment() { return true; }
	@Override public Path configDirectory() { return DIR; }
	@Override public void onLevelPostTick(ServerLevel level) {}
	@Override public void registerCrashReportSection(String name, Supplier<String> content) {}
}
```

(add `import java.io.UncheckedIOException;`)

Create `common/src/test/resources/META-INF/services/org.jmt.mcmt.platform.PlatformHooks` containing one line:

```
org.jmt.mcmt.platform.TestPlatformHooks
```

Create the test:

```java
package org.jmt.mcmt.serdes;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;

import org.jmt.mcmt.config.MCMTConfig;
import org.jmt.mcmt.config.MapConfigStore;
import org.jmt.mcmt.serdes.filter.ISerDesFilter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;

class SerDesRegistryTest {

	/** A fake modded ticker so DefaultFilter treats it as unknown. */
	static class ModdedTicker implements TickingBlockEntity {
		@Override public void tick() {}
		@Override public boolean isRemoved() { return false; }
		@Override public net.minecraft.core.BlockPos getPos() { return net.minecraft.core.BlockPos.ZERO; }
		@Override public String getType() { return "modded"; }
	}

	@BeforeAll
	static void init() {
		MCMTConfig.bake(new MapConfigStore());
		SerDesRegistry.init();
	}

	@Test
	void pistonIsAlwaysChunkLocked() {
		ISerDesFilter f = SerDesRegistry.getFilter(SerDesHookTypes.TETick, PistonMovingBlockEntity.class);
		assertNotNull(f);
		assertEquals("PistonFilter", f.getClass().getSimpleName());
	}

	@Test
	void unknownClassFallsBackToDefaultFilter() {
		ISerDesFilter f = SerDesRegistry.getFilter(SerDesHookTypes.TETick, ModdedTicker.class);
		assertNotNull(f);
		assertEquals("DefaultFilter", f.getClass().getSimpleName());
	}

	@Test
	void whitelistedClassReturnsNull() {
		MCMTConfig.teWhiteList.add(ModdedTicker.class);
		SerDesRegistry.initFilters();
		SerDesRegistry.initLookup();
		assertNull(SerDesRegistry.getFilter(SerDesHookTypes.TETick, ModdedTicker.class));
		MCMTConfig.teWhiteList.remove(ModdedTicker.class);
		SerDesRegistry.initFilters();
		SerDesRegistry.initLookup();
	}

	@Test
	void defaultFilterRunsTaskForModdedTicker() {
		ISerDesFilter f = SerDesRegistry.getFilter(SerDesHookTypes.TETick, ModdedTicker.class);
		AtomicBoolean ran = new AtomicBoolean();
		f.serialise(() -> ran.set(true), new ModdedTicker(), net.minecraft.core.BlockPos.ZERO, null, SerDesHookTypes.TETick);
		assertTrue(ran.get());
	}
}
```

If `TickingBlockEntity` has a different method set in 1.21.1, adjust `ModdedTicker` to implement exactly the interface's methods (check `common/build/moddev/...` sources or use the IDE).

- [ ] **Step 7: Run tests**

```bash
./gradlew :common:test --no-daemon
```

Expected: all pass. The `pistonIsAlwaysChunkLocked` test requires the vanilla class to load without a running game; `PistonMovingBlockEntity` only references registries in static init via `BlockEntityType`, which may trigger bootstrap errors. If it throws `ExceptionInInitializerError`, replace that test with a check that `PistonFilter.getTargets()` contains the class name string via `Class.forName(..., false, loader)` (no initialisation).

- [ ] **Step 8: Commit**

```bash
git add common
git commit -m "Port SerDes registry, filters, pools and config to common

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: `ParallelProcessor` (ex `ASMHookTerminator`) and `MCMTCommon`

**Files:**
- Create: `common/src/main/java/org/jmt/mcmt/ParallelProcessor.java`
- Create: `common/src/main/java/org/jmt/mcmt/MCMTCommon.java`
- Test: `common/src/test/java/org/jmt/mcmt/ParallelProcessorTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.jmt.mcmt;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;

import org.jmt.mcmt.config.MCMTConfig;
import org.jmt.mcmt.config.MapConfigStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParallelProcessorTest {

	@BeforeEach
	void reset() {
		MCMTConfig.bake(new MapConfigStore());
		ParallelProcessor.setupThreadpool(4);
		ParallelProcessor.resetForTests();
	}

	@Test
	void poolThreadsAreTrackedAsMcmtThreads() throws Exception {
		Thread[] seen = new Thread[1];
		ParallelProcessor.preTick(null);
		ParallelProcessor.submit("test", () -> seen[0] = Thread.currentThread());
		ParallelProcessor.postTick(null);
		assertNotNull(seen[0]);
		assertTrue(ParallelProcessor.isThreadPooled("MCMT", seen[0]));
		assertTrue(seen[0].getName().startsWith("MCMT-Pool-Thread-"));
	}

	@Test
	void postTickWaitsForAllSubmittedTasks() {
		AtomicInteger count = new AtomicInteger();
		ParallelProcessor.preTick(null);
		for (int i = 0; i < 500; i++) {
			ParallelProcessor.submit("test", () -> { Thread.yield(); count.incrementAndGet(); });
		}
		ParallelProcessor.postTick(null);
		assertEquals(500, count.get());
	}

	@Test
	void submitOutsideTickRunsInline() {
		Thread[] seen = new Thread[1];
		ParallelProcessor.submit("test", () -> seen[0] = Thread.currentThread());
		assertSame(Thread.currentThread(), seen[0]);
	}

	@Test
	void taskExceptionIsRethrownAtPostTick() {
		ParallelProcessor.preTick(null);
		ParallelProcessor.submit("boom", () -> { throw new IllegalStateException("boom"); });
		RuntimeException ex = assertThrows(RuntimeException.class, () -> ParallelProcessor.postTick(null));
		assertTrue(ex.getMessage().contains("boom") || (ex.getCause() != null && ex.getCause().getMessage().contains("boom")));
	}
}
```

- [ ] **Step 2: Run to see failure**

```bash
./gradlew :common:test --tests 'org.jmt.mcmt.ParallelProcessorTest' --no-daemon
```

Expected: compile error, `ParallelProcessor` missing.

- [ ] **Step 3: Write `ParallelProcessor.java`**

```java
package org.jmt.mcmt;

import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.jmt.mcmt.config.MCMTConfig;
import org.jmt.mcmt.platform.Platform;
import org.jmt.mcmt.serdes.SerDesHookTypes;
import org.jmt.mcmt.serdes.SerDesRegistry;
import org.jmt.mcmt.serdes.filter.ISerDesFilter;
import org.jmt.mcmt.serdes.pools.PostExecutePool;
import org.slf4j.Logger;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Owns the worker pool and the per-tick phaser. Every Mixin hook ends up here.
 * Successor of the 1.18 {@code ASMHookTerminator}.
 */
public final class ParallelProcessor {

	private static final Logger LOGGER = Constants.LOG;

	private ParallelProcessor() {}

	static volatile Phaser phaser;
	static volatile ExecutorService executor;
	static volatile MinecraftServer server;
	static final AtomicBoolean isTicking = new AtomicBoolean();
	static final AtomicInteger threadId = new AtomicInteger();
	static final ConcurrentLinkedQueue<Throwable> tickFailures = new ConcurrentLinkedQueue<>();
	static final Map<String, Set<Thread>> threadTracker = new ConcurrentHashMap<>();

	// Statistics
	public static final AtomicInteger currentWorlds = new AtomicInteger();
	public static final AtomicInteger currentEnts = new AtomicInteger();
	public static final AtomicInteger currentTEs = new AtomicInteger();
	public static final AtomicInteger currentEnvs = new AtomicInteger();
	public static final Set<String> currentTasks = ConcurrentHashMap.newKeySet();

	public static synchronized void setupThreadpool(int parallelism) {
		ExecutorService old = executor;
		threadId.set(0);
		final ClassLoader cl = ParallelProcessor.class.getClassLoader();
		ForkJoinWorkerThreadFactory factory = pool -> {
			ForkJoinWorkerThread t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
			t.setName("MCMT-Pool-Thread-" + threadId.getAndIncrement());
			t.setContextClassLoader(cl);
			regThread("MCMT", t);
			return t;
		};
		executor = new ForkJoinPool(parallelism, factory, null, false);
		if (old != null) old.shutdown();
		LOGGER.info("MCMT thread pool started with parallelism {}", parallelism);
	}

	public static synchronized void shutdownThreadpool() {
		if (executor != null) {
			executor.shutdown();
			executor = null;
		}
	}

	/** Test hook: clear tick state without touching the pool. */
	static void resetForTests() {
		phaser = null;
		server = null;
		isTicking.set(false);
		tickFailures.clear();
	}

	public static void regThread(String poolName, Thread thread) {
		threadTracker.computeIfAbsent(poolName, k -> ConcurrentHashMap.newKeySet()).add(thread);
	}

	public static boolean isThreadPooled(String poolName, Thread t) {
		Set<Thread> s = threadTracker.get(poolName);
		return s != null && s.contains(t);
	}

	public static boolean serverExecutionThreadPatch(MinecraftServer ms) {
		return isThreadPooled("MCMT", Thread.currentThread());
	}

	public static boolean isTicking() {
		return isTicking.get();
	}

	// ---- Tick lifecycle --------------------------------------------------

	public static void preTick(MinecraftServer mcs) {
		if (phaser != null) {
			LOGGER.warn("MCMT preTick called while a tick is already in progress; is there a second server?");
			return;
		}
		if (executor == null) {
			setupThreadpool(MCMTConfig.getParallelism());
		}
		tickFailures.clear();
		isTicking.set(true);
		Phaser p = new Phaser();
		p.register();
		phaser = p;
		server = mcs;
	}

	public static void postTick(MinecraftServer mcs) {
		Phaser p = phaser;
		if (p == null) {
			return;
		}
		if (server != mcs) {
			LOGGER.warn("MCMT postTick called for a different server than preTick");
		}
		p.arriveAndAwaitAdvance();
		isTicking.set(false);
		phaser = null;
		Deque<Runnable> queue = PostExecutePool.POOL.getQueue();
		Iterator<Runnable> it = queue.iterator();
		while (it.hasNext()) {
			Runnable r = it.next();
			it.remove();
			r.run();
		}
		Throwable first = tickFailures.poll();
		if (first != null) {
			int more = tickFailures.size();
			tickFailures.clear();
			throw new RuntimeException("MCMT: " + (more + 1) + " parallel task(s) failed this tick; first failure: " + first, first);
		}
	}

	/**
	 * Submit work for the current tick. Outside a tick, or when the pool is missing,
	 * the task runs inline so game logic is never skipped.
	 */
	public static void submit(String traceName, Runnable task) {
		Phaser p = phaser;
		ExecutorService ex = executor;
		if (p == null || ex == null) {
			task.run();
			return;
		}
		String name = MCMTConfig.opsTracing ? traceName : null;
		if (name != null) currentTasks.add(name);
		p.register();
		ex.execute(() -> {
			try {
				task.run();
			} catch (Throwable t) {
				LOGGER.error("MCMT parallel task failed: {}", traceName, t);
				tickFailures.add(t);
			} finally {
				p.arriveAndDeregister();
				if (name != null) currentTasks.remove(name);
			}
		});
	}

	// ---- Hook entry points (wired by Mixins in later phases) --------------

	public static void callTick(ServerLevel level, BooleanSupplier hasTimeLeft, MinecraftServer mcs) {
		if (MCMTConfig.disabled || MCMTConfig.disableWorld || server != mcs) {
			level.tick(hasTimeLeft);
			Platform.HOOKS.onLevelPostTick(level);
			return;
		}
		submit("WorldTick: " + level.dimension().location(), () -> {
			currentWorlds.incrementAndGet();
			try {
				level.tick(hasTimeLeft);
				if (MCMTConfig.disableWorldPostTick) {
					synchronized (ParallelProcessor.class) { Platform.HOOKS.onLevelPostTick(level); }
				} else {
					Platform.HOOKS.onLevelPostTick(level);
				}
			} finally {
				currentWorlds.decrementAndGet();
			}
		});
	}

	public static void callEntityTick(Entity entity, ServerLevel level) {
		if (MCMTConfig.disabled || MCMTConfig.disableEntity) {
			entity.tick();
			return;
		}
		submit("EntityTick: " + entity.getType() + "@" + entity.getId(), () -> {
			currentEnts.incrementAndGet();
			try {
				ISerDesFilter filter = SerDesRegistry.getFilter(SerDesHookTypes.EntityTick, entity.getClass());
				if (filter != null) {
					filter.serialise(entity::tick, entity, entity.getOnPos(), level, SerDesHookTypes.EntityTick);
				} else {
					entity.tick();
				}
			} finally {
				currentEnts.decrementAndGet();
			}
		});
	}

	public static void callBlockEntityTick(TickingBlockEntity ticker, Level level) {
		if (MCMTConfig.disabled || MCMTConfig.disableTileEntity || !(level instanceof ServerLevel)) {
			ticker.tick();
			return;
		}
		submit("TETick: " + ticker.getType() + "@" + ticker.getPos(), () -> {
			currentTEs.incrementAndGet();
			try {
				ISerDesFilter filter = SerDesRegistry.getFilter(SerDesHookTypes.TETick, ticker.getClass());
				if (filter != null) {
					filter.serialise(ticker::tick, ticker, ticker.getPos(), level, SerDesHookTypes.TETick);
				} else {
					ticker.tick();
				}
			} finally {
				currentTEs.decrementAndGet();
			}
		});
	}

	public static void callTickEnvironment(ServerLevel level, LevelChunk chunk, int randomTickSpeed) {
		if (MCMTConfig.disabled || MCMTConfig.disableEnvironment) {
			level.tickChunk(chunk, randomTickSpeed);
			return;
		}
		submit("EnvTick: " + chunk.getPos(), () -> {
			currentEnvs.incrementAndGet();
			try {
				level.tickChunk(chunk, randomTickSpeed);
			} finally {
				currentEnvs.decrementAndGet();
			}
		});
	}

	public static boolean shouldThreadChunks() {
		return MCMTConfig.disableMultiChunk;
	}

	// ---- Crash report -------------------------------------------------------

	public static String populateCrashReport() {
		StringBuilder sb = new StringBuilder("\n");
		sb.append("\t\tConfig Info:\n");
		sb.append("\t\t\t- Disabled: ").append(MCMTConfig.disabled).append('\n');
		sb.append("\t\t\t- World Disabled: ").append(MCMTConfig.disableWorld)
		  .append(" (onPostTick serialised: ").append(MCMTConfig.disableWorldPostTick).append(")\n");
		sb.append("\t\t\t- Entity Disabled: ").append(MCMTConfig.disableEntity).append('\n');
		sb.append("\t\t\t- Env Disabled: ").append(MCMTConfig.disableEnvironment).append('\n');
		sb.append("\t\t\t- TE Disabled: ").append(MCMTConfig.disableTileEntity).append('\n');
		sb.append("\t\t\t- SCP Disabled: ").append(MCMTConfig.disableChunkProvider).append('\n');
		if (MCMTConfig.opsTracing) {
			sb.append("\t\t-- Running Operations Begin --\n");
			for (String s : currentTasks) sb.append("\t\t\t").append(s).append('\n');
			sb.append("\t\t-- Running Operations End --\n");
		}
		return sb.toString();
	}
}
```

- [ ] **Step 4: Write `MCMTCommon.java`**

```java
package org.jmt.mcmt;

import org.jmt.mcmt.commands.StatsCommand;
import org.jmt.mcmt.config.ConfigStore;
import org.jmt.mcmt.config.MCMTConfig;
import org.jmt.mcmt.platform.Platform;
import org.jmt.mcmt.serdes.SerDesRegistry;

import net.minecraft.server.MinecraftServer;

/** Loader-independent lifecycle. Each loader calls these from its own events. */
public final class MCMTCommon {

	private MCMTCommon() {}

	private static ConfigStore store;

	/** Called once during mod construction with the loader's config store. */
	public static void init(ConfigStore configStore) {
		store = configStore;
		Constants.LOG.info("MCMT {} initialising on {}", MCMTVersion.get(), Platform.HOOKS.platformName());
		MCMTConfig.bake(store);
		SerDesRegistry.init();
		StatsCommand.runDataThread();
		Platform.HOOKS.registerCrashReportSection("MCMT", ParallelProcessor::populateCrashReport);
	}

	/** Re-read the store (after the loader reports a config reload). */
	public static void reloadConfig() {
		if (store != null) MCMTConfig.bake(store);
	}

	public static void saveConfig() {
		if (store != null) MCMTConfig.save(store);
	}

	public static void onServerStarting(MinecraftServer server) {
		Constants.LOG.info("MCMT setting up thread pool...");
		StatsCommand.setServer(server);
		StatsCommand.resetAll();
		ParallelProcessor.setupThreadpool(MCMTConfig.getParallelism());
	}

	public static void onServerStopped(MinecraftServer server) {
		ParallelProcessor.shutdownThreadpool();
	}
}
```

Create `common/src/main/java/org/jmt/mcmt/MCMTVersion.java`:

```java
package org.jmt.mcmt;

public final class MCMTVersion {
	private MCMTVersion() {}
	public static String get() {
		String v = MCMTVersion.class.getPackage().getImplementationVersion();
		return v == null ? "dev" : v;
	}
}
```

- [ ] **Step 5: Run tests**

```bash
./gradlew :common:test --no-daemon
```

Expected: `ParallelProcessorTest` passes (4 tests). `MCMTCommon` does not compile yet because `StatsCommand` is missing; that is Task 7. If the build fails only on `MCMTCommon`, temporarily comment the `StatsCommand` lines, run, then restore them in Task 7.

- [ ] **Step 6: Commit**

```bash
git add common
git commit -m "Add ParallelProcessor (ex ASMHookTerminator) and MCMTCommon lifecycle

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: Port the commands

**Files:**
- Create (ported): `common/src/main/java/org/jmt/mcmt/commands/{ConfigCommand,StatsCommand,DebugCommands}.java`

- [ ] **Step 1: Copy**

```bash
B=1.18.2-forge-legacy; S=forge-mod/src/main/java/org/jmt/mcmt; D=common/src/main/java/org/jmt/mcmt
mkdir -p $D/commands
for f in ConfigCommand StatsCommand DebugCommands; do git show $B:$S/commands/$f.java > $D/commands/$f.java; done
```

- [ ] **Step 2: Edit `ConfigCommand.java`**

- Imports: drop `org.jmt.mcmt.asmdest.ASMHookTerminator`, `org.jmt.mcmt.config.GeneralConfig`, `net.minecraft.network.chat.TextComponent`. Add `org.jmt.mcmt.MCMTCommon`, `org.jmt.mcmt.config.MCMTConfig`, `org.jmt.mcmt.serdes.SerDesRegistry`, `net.minecraft.network.chat.Component`.
- Replace every `GeneralConfig.` with `MCMTConfig.`.
- Replace every `TextComponent message = new TextComponent(X);` with `Component message = Component.literal(X);` and `TextComponent message;` with `Component message;`. Replace remaining `new TextComponent(` with `Component.literal(`.
- `sendSuccess(message, true)` takes a `Supplier<Component>` in 1.21.1: replace every `sendSuccess(message, true)` with `sendSuccess(() -> message, true)`. Where `message` is reassigned afterwards (the `save` branch), introduce `final Component msg1`, `msg2`. `sendFailure(message)` is unchanged.
- `GeneralConfig.saveConfig()` becomes `MCMTCommon.saveConfig()`.
- `ASMHookTerminator.filterTE((TickingBlockEntity)te)` becomes `SerDesRegistry.DefaultFilter.filterTE(te)`.
- `cmdCtx.getSource().getPlayerOrException().pick(20, 0.0F, false)` is unchanged in 1.21.1.
- `cmdCtx.getSource().getLevel().getBlockEntity(bp)` unchanged. `te instanceof TickingBlockEntity`: `BlockEntity` no longer implements `TickingBlockEntity` in 1.21.1. Replace the check with `te != null && te.getBlockState().getBlock() instanceof net.minecraft.world.level.block.EntityBlock eb && eb.getTicker(cmdCtx.getSource().getLevel(), te.getBlockState(), (net.minecraft.world.level.block.entity.BlockEntityType) te.getType()) != null` (raw cast is required by the generic signature; add `@SuppressWarnings({"rawtypes","unchecked"})` on `registerConfig`). For the white/black list additions keep `te.getClass()`.

- [ ] **Step 3: Edit `StatsCommand.java`**

- Imports: log4j → SLF4J (`LoggerFactory.getLogger("MCMT Dev Warning")`); `ASMHookTerminator` → `org.jmt.mcmt.ParallelProcessor`; `GeneralConfig` → `MCMTConfig`; `TextComponent` → `Component`.
- Same `Component.literal` / `sendSuccess(() -> message, true)` substitutions as above.
- `ASMHookTerminator.currentX` → `ParallelProcessor.currentX`.
- `mcs.getAverageTickTime()` was removed; use `mcs.getAverageTickTimeNanos() / 1_000_000.0f`.
- Keep the stats thread, logging to CSV and the periodic warn exactly as they are.

- [ ] **Step 4: Rewrite `DebugCommands.java` to the subset that still compiles**

Keep `getBlockState`, `nbtdump`, `tick te`, `classpathDump`. Delete the `test structures` subcommand (its `ConfiguredStructureFeature` API no longer exists; the debug feature is not part of the mod's function). Edits:

- `TextComponent` → `Component.literal`, `sendSuccess(() -> message, true)`.
- `bs.getBlock().getRegistryName()` → `net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(bs.getBlock())`.
- `te.serializeNBT()` (Forge) → `te.saveWithFullMetadata(sw.registryAccess())`.
- `System.out.println(...)` → `Constants.LOG.info(...)`.
- `tick te`: `te instanceof TickingBlockEntity` no longer holds. Replace with: look up the ticker through the `EntityBlock` as in Task 7 step 2, then call `ticker.tick(sw, bp, bs, te)` (raw types, suppress warnings).
- Remove the `structureIdx` field and unused imports.

- [ ] **Step 5: Compile**

```bash
./gradlew :common:compileJava :common:test --no-daemon
```

Expected: BUILD SUCCESSFUL, all tests still pass. Fix any remaining API mismatches by reading the decompiled sources under `common/build/moddev/` (search with `grep -rn "methodName" common/build/moddev/`).

- [ ] **Step 6: Commit**

```bash
git add common
git commit -m "Port /mcmt config, stats and debug commands to 1.21.1 APIs

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 8: Common resources (Mixin config placeholder, pack.mcmeta)

**Files:**
- Create: `common/src/main/resources/jmt_mcmt.mixins.json`, `common/src/main/resources/pack.mcmeta`, `common/src/main/java/org/jmt/mcmt/mixin/package-info.java`

- [ ] **Step 1: `jmt_mcmt.mixins.json`** (empty lists; hooks are added in phase 2)

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "org.jmt.mcmt.mixin",
  "refmap": "${mod_id}.refmap.json",
  "compatibilityLevel": "JAVA_21",
  "mixins": [],
  "server": [],
  "injectors": {
    "defaultRequire": 1
  }
}
```

- [ ] **Step 2: `pack.mcmeta`**

```json
{
  "pack": {
    "description": "${mod_name}",
    "pack_format": 34
  }
}
```

- [ ] **Step 3: `package-info.java`**

```java
/** Mixins targeting vanilla classes. All targets use MojMap names. */
package org.jmt.mcmt.mixin;
```

- [ ] **Step 4: Commit**

```bash
git add common
git commit -m "Add common Mixin config placeholder and pack metadata

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 9: Fabric module

**Files:**
- Create: `fabric/src/main/java/org/jmt/mcmt/fabric/MCMTFabric.java`, `FabricPlatformHooks.java`, `FabricConfigStore.java`
- Create: `fabric/src/main/resources/fabric.mod.json`, `fabric/src/main/resources/META-INF/services/org.jmt.mcmt.platform.PlatformHooks`

- [ ] **Step 1: `FabricConfigStore.java`** (night-config TOML, same keys as NeoForge)

```java
package org.jmt.mcmt.fabric;

import java.nio.file.Path;
import java.util.List;

import org.jmt.mcmt.config.ConfigStore;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;

public class FabricConfigStore implements ConfigStore {

	private final CommentedFileConfig cfg;

	public FabricConfigStore(Path file) {
		cfg = CommentedFileConfig.builder(file).autosave().preserveInsertionOrder().build();
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
}
```

- [ ] **Step 2: `FabricPlatformHooks.java`**

```java
package org.jmt.mcmt.fabric;

import java.nio.file.Path;
import java.util.function.Supplier;

import org.jmt.mcmt.platform.PlatformHooks;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;

public class FabricPlatformHooks implements PlatformHooks {

	@Override public String platformName() { return "Fabric"; }

	@Override public boolean isDevelopmentEnvironment() { return FabricLoader.getInstance().isDevelopmentEnvironment(); }

	@Override public Path configDirectory() { return FabricLoader.getInstance().getConfigDir(); }

	@Override
	public void onLevelPostTick(ServerLevel level) {
		// Fabric fires END_WORLD_TICK itself from ServerLevel.tick via its own Mixin, so there is
		// nothing to re-fire here. Kept as a hook point for parity with NeoForge.
	}

	@Override
	public void registerCrashReportSection(String name, Supplier<String> content) {
		// Fabric has no public API for extra crash report sections; the content is logged on crash instead.
	}
}
```

(Remove the unused `ServerTickEvents` import.)

- [ ] **Step 3: `MCMTFabric.java`**

```java
package org.jmt.mcmt.fabric;

import org.jmt.mcmt.Constants;
import org.jmt.mcmt.MCMTCommon;
import org.jmt.mcmt.commands.ConfigCommand;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

public class MCMTFabric implements DedicatedServerModInitializer {

	@Override
	public void onInitializeServer() {
		MCMTCommon.init(new FabricConfigStore(
				FabricLoader.getInstance().getConfigDir().resolve(Constants.MOD_ID + "-common.toml")));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ConfigCommand.register(dispatcher));
		ServerLifecycleEvents.SERVER_STARTING.register(MCMTCommon::onServerStarting);
		ServerLifecycleEvents.SERVER_STOPPED.register(MCMTCommon::onServerStopped);
	}
}
```

- [ ] **Step 4: `fabric.mod.json`**

```json
{
  "schemaVersion": 1,
  "id": "${mod_id}",
  "version": "${version}",
  "name": "${mod_name}",
  "description": "${description}",
  "authors": ["${mod_author}"],
  "contact": {
    "homepage": "https://github.com/ben001109/JMT-MCMT",
    "sources": "https://github.com/ben001109/JMT-MCMT",
    "issues": "https://github.com/ben001109/JMT-MCMT/issues"
  },
  "license": "${license}",
  "environment": "server",
  "entrypoints": {
    "server": ["org.jmt.mcmt.fabric.MCMTFabric"]
  },
  "mixins": [
    "${mod_id}.mixins.json"
  ],
  "depends": {
    "fabricloader": ">=${fabric_loader_version}",
    "fabric-command-api-v2": "*",
    "fabric-lifecycle-events-v1": "*",
    "minecraft": "${minecraft_version}",
    "java": ">=${java_version}"
  }
}
```

- [ ] **Step 5: Service file** `fabric/src/main/resources/META-INF/services/org.jmt.mcmt.platform.PlatformHooks`

```
org.jmt.mcmt.fabric.FabricPlatformHooks
```

- [ ] **Step 6: Build the Fabric jar**

```bash
./gradlew :fabric:build --no-daemon
ls fabric/build/libs/
```

Expected: `jmt_mcmt-fabric-1.21.1-0.25.0-alpha.1.jar` present. Note: the `include` of night-config puts both jars under `META-INF/jars/`; verify with `unzip -l fabric/build/libs/jmt_mcmt-fabric-1.21.1-0.25.0-alpha.1.jar | grep META-INF/jars`.

- [ ] **Step 7: Commit**

```bash
git add fabric
git commit -m "Add Fabric loader module with TOML config store and lifecycle wiring

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 10: NeoForge module

**Files:**
- Create: `neoforge/src/main/java/org/jmt/mcmt/neoforge/MCMTNeoForge.java`, `NeoForgePlatformHooks.java`, `NeoForgeConfigStore.java`
- Create: `neoforge/src/main/resources/META-INF/neoforge.mods.toml`, `neoforge/src/main/resources/META-INF/services/org.jmt.mcmt.platform.PlatformHooks`

- [ ] **Step 1: `NeoForgeConfigStore.java`**

Builds a `ModConfigSpec` with the same keys/comments as 1.18, then adapts it to `ConfigStore`.

```java
package org.jmt.mcmt.neoforge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jmt.mcmt.config.ConfigStore;
import org.jmt.mcmt.config.MCMTConfig;

import net.neoforged.neoforge.common.ModConfigSpec;

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

	private static String leaf(String key) { return key.substring(key.lastIndexOf('.') + 1); }

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

	private boolean loaded() { return spec.isLoaded(); }

	@Override public boolean getBoolean(String key, boolean def) { return loaded() ? this.<Boolean>v(key).get() : def; }
	@Override public int getInt(String key, int def) { return loaded() ? this.<Integer>v(key).get() : def; }
	@Override public String getString(String key, String def) { return loaded() ? this.<String>v(key).get() : def; }
	@Override public List<String> getStringList(String key, List<String> def) { return loaded() ? new ArrayList<>(this.<List<? extends String>>v(key).get()) : def; }

	@Override public void setBoolean(String key, boolean value) { if (loaded()) this.<Boolean>v(key).set(value); }
	@Override public void setInt(String key, int value) { if (loaded()) this.<Integer>v(key).set(value); }
	@Override public void setString(String key, String value) { if (loaded()) this.<String>v(key).set(value); }
	@Override public void setStringList(String key, List<String> value) { if (loaded()) this.<List<? extends String>>v(key).set(new ArrayList<>(value)); }

	@Override public void save() { if (loaded()) spec.save(); }
}
```

- [ ] **Step 2: `NeoForgePlatformHooks.java`**

```java
package org.jmt.mcmt.neoforge;

import java.nio.file.Path;
import java.util.function.Supplier;

import org.jmt.mcmt.platform.PlatformHooks;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.logging.CrashReportExtender;

public class NeoForgePlatformHooks implements PlatformHooks {

	@Override public String platformName() { return "NeoForge"; }

	@Override public boolean isDevelopmentEnvironment() { return !FMLLoader.isProduction(); }

	@Override public Path configDirectory() { return FMLPaths.CONFIGDIR.get(); }

	@Override
	public void onLevelPostTick(ServerLevel level) {
		// NeoForge fires LevelTickEvent.Post from MinecraftServer.tickChildren after ServerLevel.tick.
		// Phase 2's Mixin redirects that call, so the event is re-fired here on the ticking thread.
		NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(level));
	}

	@Override
	public void registerCrashReportSection(String name, Supplier<String> content) {
		CrashReportExtender.registerCrashCallable(name, content::get);
	}
}
```

If `CrashReportExtender.registerCrashCallable` has a different signature in 21.1.x, check `neoforge/build/moddev/` sources and adapt; if it does not exist, make the method a no-op and log at debug.

- [ ] **Step 3: `MCMTNeoForge.java`**

```java
package org.jmt.mcmt.neoforge;

import org.jmt.mcmt.Constants;
import org.jmt.mcmt.MCMTCommon;
import org.jmt.mcmt.commands.ConfigCommand;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@Mod(Constants.MOD_ID)
public class MCMTNeoForge {

	private final NeoForgeConfigStore store = new NeoForgeConfigStore();

	public MCMTNeoForge(IEventBus modBus, ModContainer container) {
		container.registerConfig(ModConfig.Type.COMMON, store.spec, Constants.MOD_ID + "-common.toml");
		MCMTCommon.init(store);
		modBus.addListener(this::onConfig);
		NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
		NeoForge.EVENT_BUS.addListener(this::onServerStarting);
		NeoForge.EVENT_BUS.addListener(this::onServerStopped);
	}

	private void onConfig(ModConfigEvent event) {
		if (event.getConfig().getSpec() == store.spec) {
			MCMTCommon.reloadConfig();
		}
	}

	private void onRegisterCommands(RegisterCommandsEvent event) {
		ConfigCommand.register(event.getDispatcher());
	}

	private void onServerStarting(ServerStartingEvent event) {
		MCMTCommon.onServerStarting(event.getServer());
	}

	private void onServerStopped(ServerStoppedEvent event) {
		MCMTCommon.onServerStopped(event.getServer());
	}
}
```

- [ ] **Step 4: `neoforge.mods.toml`**

```toml
modLoader = "javafml"
loaderVersion = "${neoforge_loader_version_range}"
license = "${license}"
issueTrackerURL = "https://github.com/ben001109/JMT-MCMT/issues"

[[mods]]
modId = "${mod_id}"
version = "${version}"
displayName = "${mod_name}"
displayURL = "https://github.com/ben001109/JMT-MCMT"
authors = "${mod_author}"
description = '''${description}'''

[[mixins]]
config = "${mod_id}.mixins.json"

[[dependencies.${mod_id}]]
modId = "neoforge"
type = "required"
versionRange = "[${neoforge_version},)"
ordering = "NONE"
side = "SERVER"

[[dependencies.${mod_id}]]
modId = "minecraft"
type = "required"
versionRange = "${minecraft_version_range}"
ordering = "NONE"
side = "SERVER"
```

- [ ] **Step 5: Service file** `neoforge/src/main/resources/META-INF/services/org.jmt.mcmt.platform.PlatformHooks`

```
org.jmt.mcmt.neoforge.NeoForgePlatformHooks
```

- [ ] **Step 6: Build**

```bash
./gradlew :neoforge:build --no-daemon
ls neoforge/build/libs/
```

Expected: `jmt_mcmt-neoforge-1.21.1-0.25.0-alpha.1.jar`.

- [ ] **Step 7: Commit**

```bash
git add neoforge
git commit -m "Add NeoForge loader module with ModConfigSpec store and event wiring

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 11: Smoke test harness

**Files:**
- Create: `tests/smoke/run-smoke.ps1`, `tests/smoke/run-smoke.sh`, `tests/smoke/commands.txt`, `tests/smoke/allowlist.txt`, `tests/smoke/README.md`

The harness uses the Gradle run tasks (`:fabric:runServer`, `:neoforge:runServer`), which already wire the mod and loader. It feeds commands over stdin and inspects `runs/server/logs/latest.log`.

- [ ] **Step 1: `commands.txt`** (one command per line; `#` comments; `sleep N` waits N seconds)

```
# Load a 5x5 chunk area around spawn and populate it
gamerule doMobSpawning false
forceload add -40 -40 40 40
sleep 5
mcmt config state
mcmt stats toggle
# 200 entities: 100 zombies, 50 cows, 50 items
execute positioned 0 ~ 0 run summon minecraft:zombie ~ 80 ~ {PersistenceRequired:1b}
sleep 1
```

Then append, via the script generating the file, 99 more zombie lines, 50 `summon minecraft:cow ~ 80 ~`, 50 `summon minecraft:item ~ 80 ~ {Item:{id:"minecraft:stone",count:1}}`, and:

```
setblock 2 80 2 minecraft:hopper
setblock 3 80 2 minecraft:furnace
setblock 4 80 2 minecraft:chest
sleep 120
mcmt stats
mcmt config state
stop
```

Instead of hand-writing 200 lines, the scripts expand a `repeat N <command>` directive. Final `commands.txt`:

```
gamerule doMobSpawning false
forceload add -40 -40 40 40
sleep 5
mcmt config state
mcmt stats toggle
repeat 100 execute positioned 0 80 0 run summon minecraft:zombie ~ ~ ~ {PersistenceRequired:1b}
repeat 50 execute positioned 5 80 5 run summon minecraft:cow ~ ~ ~
repeat 50 execute positioned -5 80 -5 run summon minecraft:item ~ ~ ~ {Item:{id:"minecraft:stone",count:1}}
setblock 2 80 2 minecraft:hopper
setblock 3 80 2 minecraft:furnace
setblock 4 80 2 minecraft:chest
sleep 120
mcmt stats
mcmt config state
stop
```

- [ ] **Step 2: `allowlist.txt`** (regexes for log lines that look like errors but are harmless)

```
# Mojang API lookups fail offline; harmless
Failed to verify username
Authentication servers are down
# Fabric API warns when a dev refmap is missing
Reference map .* could not be read
# Our own test-induced noise
MCMT is installed; error logs are likely invalid
```

- [ ] **Step 3: `run-smoke.sh`**

```bash
#!/usr/bin/env bash
# Usage: tests/smoke/run-smoke.sh <fabric|neoforge> [tick-seconds]
set -uo pipefail
LOADER="${1:?loader}"
TICK_SECONDS="${2:-120}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/tests/smoke/out"; mkdir -p "$OUT"
RUNDIR="$ROOT/$LOADER/runs/server"
LOG="$RUNDIR/logs/latest.log"
REPORT="$OUT/$LOADER-report.txt"
STDOUT="$OUT/$LOADER-stdout.log"
FIFO="$OUT/$LOADER.stdin"

rm -rf "$RUNDIR/world" "$RUNDIR/logs" "$FIFO" "$STDOUT"
mkdir -p "$RUNDIR"
echo "eula=true" > "$RUNDIR/eula.txt"
cat > "$RUNDIR/server.properties" <<EOF
online-mode=false
view-distance=4
simulation-distance=4
spawn-protection=0
max-tick-time=60000
level-seed=mcmt-smoke
level-type=minecraft\:flat
generate-structures=false
EOF

mkfifo "$FIFO"
cd "$ROOT"
# Keep the fifo open for the lifetime of the server.
exec 3<>"$FIFO"
./gradlew ":$LOADER:runServer" --no-daemon --console=plain --quiet < "$FIFO" > "$STDOUT" 2>&1 &
GRADLE_PID=$!

fail() { echo "FAIL: $*" | tee -a "$REPORT"; kill $GRADLE_PID 2>/dev/null; exit 1; }
: > "$REPORT"

# Wait for "Done"
for i in $(seq 1 300); do
  grep -q 'Done (' "$STDOUT" 2>/dev/null && break
  kill -0 $GRADLE_PID 2>/dev/null || fail "server process exited before Done"
  sleep 1
done
grep -q 'Done (' "$STDOUT" || fail "server did not reach Done within 300s"
echo "server started" >> "$REPORT"

send() { echo "$1" >&3; }
while IFS= read -r line || [ -n "$line" ]; do
  [[ -z "$line" || "$line" == \#* ]] && continue
  if [[ "$line" == sleep\ * ]]; then
    secs="${line#sleep }"; [[ "$secs" == "120" ]] && secs="$TICK_SECONDS"; sleep "$secs"
  elif [[ "$line" == repeat\ * ]]; then
    n="$(echo "$line" | awk '{print $2}')"; cmd="$(echo "$line" | cut -d' ' -f3-)"
    for _ in $(seq 1 "$n"); do send "$cmd"; done
  else
    send "$line"
  fi
done < "$ROOT/tests/smoke/commands.txt"

# Wait for exit
for i in $(seq 1 60); do kill -0 $GRADLE_PID 2>/dev/null || break; sleep 1; done
kill -0 $GRADLE_PID 2>/dev/null && fail "server did not stop within 60s after /stop"
echo "server stopped cleanly" >> "$REPORT"

# Analyse
grep -vE -f <(grep -v '^#' "$ROOT/tests/smoke/allowlist.txt") "$STDOUT" > "$OUT/$LOADER-filtered.log"
if grep -qE 'Exception|Error:|ERROR\]|FATAL|Watchdog|A single server tick took' "$OUT/$LOADER-filtered.log"; then
  echo "--- offending lines ---" >> "$REPORT"
  grep -nE 'Exception|Error:|ERROR\]|FATAL|Watchdog|A single server tick took' "$OUT/$LOADER-filtered.log" | head -50 >> "$REPORT"
  fail "errors found in server log"
fi
grep -q 'MCMT is currently' "$STDOUT" || fail "/mcmt config state produced no output"
grep -q 'Current max threads' "$STDOUT" || fail "/mcmt stats produced no output"
echo "PASS" | tee -a "$REPORT"
```

- [ ] **Step 4: `run-smoke.ps1`** (same behaviour on Windows, used locally)

```powershell
# Usage: tests/smoke/run-smoke.ps1 -Loader fabric|neoforge [-TickSeconds 120]
param([Parameter(Mandatory)][ValidateSet('fabric','neoforge')][string]$Loader, [int]$TickSeconds = 120)
$ErrorActionPreference = 'Stop'
$Root = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$Out = Join-Path $Root 'tests\smoke\out'; New-Item -ItemType Directory -Force $Out | Out-Null
$RunDir = Join-Path $Root "$Loader\runs\server"
$Report = Join-Path $Out "$Loader-report.txt"
$Stdout = Join-Path $Out "$Loader-stdout.log"

Remove-Item -Recurse -Force (Join-Path $RunDir 'world'), (Join-Path $RunDir 'logs'), $Stdout -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $RunDir | Out-Null
Set-Content (Join-Path $RunDir 'eula.txt') 'eula=true'
Set-Content (Join-Path $RunDir 'server.properties') @"
online-mode=false
view-distance=4
simulation-distance=4
spawn-protection=0
max-tick-time=60000
level-seed=mcmt-smoke
level-type=minecraft\:flat
generate-structures=false
"@
Set-Content $Report ''

function Fail($msg) { Add-Content $Report "FAIL: $msg"; Write-Host "FAIL: $msg"; if ($p -and -not $p.HasExited) { $p.Kill($true) }; exit 1 }

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = Join-Path $Root 'gradlew.bat'
$psi.Arguments = ":$Loader`:runServer --no-daemon --console=plain --quiet"
$psi.WorkingDirectory = $Root
$psi.RedirectStandardInput = $true; $psi.RedirectStandardOutput = $true; $psi.RedirectStandardError = $true
$psi.UseShellExecute = $false
$p = [System.Diagnostics.Process]::Start($psi)
$sw = [System.IO.StreamWriter]::new($Stdout); $sw.AutoFlush = $true
$outHandler = { if ($EventArgs.Data) { $Event.MessageData.WriteLine($EventArgs.Data) } }
Register-ObjectEvent $p OutputDataReceived -Action $outHandler -MessageData $sw | Out-Null
Register-ObjectEvent $p ErrorDataReceived -Action $outHandler -MessageData $sw | Out-Null
$p.BeginOutputReadLine(); $p.BeginErrorReadLine()

$deadline = (Get-Date).AddSeconds(300)
while ((Get-Date) -lt $deadline) {
  if ($p.HasExited) { Fail 'server process exited before Done' }
  if ((Test-Path $Stdout) -and (Select-String -Path $Stdout -Pattern 'Done \(' -Quiet)) { break }
  Start-Sleep 1
}
if (-not (Select-String -Path $Stdout -Pattern 'Done \(' -Quiet)) { Fail 'server did not reach Done within 300s' }
Add-Content $Report 'server started'

function Send($cmd) { $p.StandardInput.WriteLine($cmd) }
foreach ($line in Get-Content (Join-Path $Root 'tests\smoke\commands.txt')) {
  if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) { continue }
  if ($line -match '^sleep (\d+)$') { $s = [int]$Matches[1]; if ($s -eq 120) { $s = $TickSeconds }; Start-Sleep $s }
  elseif ($line -match '^repeat (\d+) (.+)$') { 1..([int]$Matches[1]) | ForEach-Object { Send $Matches[2] } }
  else { Send $line }
}

if (-not $p.WaitForExit(60000)) { Fail 'server did not stop within 60s after /stop' }
Add-Content $Report 'server stopped cleanly'
$sw.Close()

$allow = Get-Content (Join-Path $Root 'tests\smoke\allowlist.txt') | Where-Object { $_ -and -not $_.StartsWith('#') }
$filtered = Get-Content $Stdout | Where-Object { $l = $_; -not ($allow | Where-Object { $l -match $_ }) }
Set-Content (Join-Path $Out "$Loader-filtered.log") $filtered
$bad = $filtered | Select-String -Pattern 'Exception|Error:|ERROR\]|FATAL|Watchdog|A single server tick took'
if ($bad) { Add-Content $Report '--- offending lines ---'; $bad | Select-Object -First 50 | ForEach-Object { Add-Content $Report $_.Line }; Fail 'errors found in server log' }
if (-not (Select-String -Path $Stdout -Pattern 'MCMT is currently' -Quiet)) { Fail '/mcmt config state produced no output' }
if (-not (Select-String -Path $Stdout -Pattern 'Current max threads' -Quiet)) { Fail '/mcmt stats produced no output' }
Add-Content $Report 'PASS'; Write-Host 'PASS'
```

- [ ] **Step 5: `tests/smoke/README.md`**

```markdown
# Smoke tests

Starts a headless dedicated server for one loader through Gradle, feeds the
commands in `commands.txt` over stdin, waits, stops the server and scans the
log for errors. Lines matching a regex in `allowlist.txt` are ignored.

    tests/smoke/run-smoke.sh fabric 120
    tests/smoke/run-smoke.sh neoforge 120
    pwsh tests/smoke/run-smoke.ps1 -Loader fabric -TickSeconds 120

Output: `tests/smoke/out/<loader>-report.txt`, `-stdout.log`, `-filtered.log`.
Exit code 0 means PASS.
```

- [ ] **Step 6: Run both loaders locally with a short tick window**

```bash
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"
bash tests/smoke/run-smoke.sh fabric 30 ; cat tests/smoke/out/fabric-report.txt
bash tests/smoke/run-smoke.sh neoforge 30 ; cat tests/smoke/out/neoforge-report.txt
```

Expected: both reports end with `PASS`. Typical first-run failures and fixes:
- `Done` never appears: check `-stdout.log` for a mod loading error (bad `fabric.mod.json`, missing service file, Mixin config parse error).
- Fabric `runServer` ignores stdin: loom's run task by default does not attach stdin; add `standardInput = System.in` to the server run in `fabric/build.gradle` via `tasks.named('runServer') { standardInput = System.in }`. ModDevGradle run tasks accept stdin already.
- `mcmt` command unknown: command registration callback not firing; confirm `DedicatedServerModInitializer` entrypoint key is `server`.

- [ ] **Step 7: Commit**

```bash
git add tests fabric/build.gradle
git commit -m "Add headless dedicated-server smoke test harness for both loaders

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 12: CI workflow

**Files:**
- Delete: `.github/workflows/*.yml` (old Forge builds), `.travis.yml`
- Create: `.github/workflows/build.yml`

- [ ] **Step 1: Remove old CI**

```bash
git rm -q .github/workflows/*.yml .travis.yml 2>/dev/null || true
```

- [ ] **Step 2: `build.yml`**

```yaml
name: Build and smoke test (1.21.1)

on:
  push:
    branches: ['1.21.1']
  pull_request:
    branches: ['1.21.1']
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v4
      - name: Unit tests
        run: ./gradlew :common:test --no-daemon
      - name: Build jars
        run: ./gradlew buildAll --no-daemon
      - name: Smoke test Fabric
        run: bash tests/smoke/run-smoke.sh fabric 60
      - name: Smoke test NeoForge
        if: always()
        run: bash tests/smoke/run-smoke.sh neoforge 60
      - name: Upload jars
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: jmt_mcmt-1.21.1-jars
          path: |
            fabric/build/libs/*.jar
            neoforge/build/libs/*.jar
      - name: Upload smoke reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: smoke-reports
          path: tests/smoke/out/
```

- [ ] **Step 3: Commit and push, watch the run**

```bash
git add .github .travis.yml 2>/dev/null; git add -A .github
git commit -m "Replace Forge CI with 1.21.1 build and smoke test workflow

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
git push origin 1.21.1
gh run watch --exit-status
```

Expected: workflow green. If the smoke step fails only in CI (timeouts on slower runners), raise the Done timeout in the script to 600 s.

---

### Task 13: Branch README and changelog entry

**Files:**
- Create: `README.md` (branch-level), `CHANGELOG.md`

- [ ] **Step 1: `README.md`**

```markdown
# MCMT for Minecraft 1.21.1 (Fabric + NeoForge)

Development branch for the 1.21.1 port of JMT-MCMT. Both loader jars are
built from this branch:

    ./gradlew buildAll

Jars land in `fabric/build/libs/` and `neoforge/build/libs/`.

Layout: `common/` holds all game logic and Mixins (MojMap names); `fabric/`
and `neoforge/` hold the loader entrypoints and config bridges. See
`docs/superpowers/specs/2026-10-18-port-1.21.1-multiloader-design.md`.

Status: phase 1 (skeleton). The mod loads, config and `/mcmt` commands work,
no parallelisation hooks are active yet.

Tests: `./gradlew :common:test`, `tests/smoke/run-smoke.sh <fabric|neoforge>`.

Licence: BSD-2-Clause, see `LICENCE`.
```

- [ ] **Step 2: `CHANGELOG.md`**

```markdown
# Changelog

## 0.25.0-alpha.1 (unreleased) - 1.21.1 Fabric + NeoForge

- Rebuilt as a multiloader project (`common`/`fabric`/`neoforge`).
- Removed the Forge JS coremods and the `sync-fu` ModLauncher service; hooks will be Mixins.
- Config keys unchanged from 1.18; NeoForge uses `ModConfigSpec`, Fabric uses a bundled TOML file.
- `/mcmt debug test structures` removed (API gone).
- Phase 1: no parallel hooks active yet.
```

- [ ] **Step 3: Commit and push**

```bash
git add README.md CHANGELOG.md
git commit -m "Add branch README and changelog for the 1.21.1 port

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
git push origin 1.21.1
```

---

## Follow-ups discovered during execution

(Record anything deferred here: disabled tests, API gaps, workarounds.)

## Self-review against the spec

- Section 3 layout: Tasks 1, 8, 9, 10, 11, 12 create every listed path except `docs/` (exists) and the `master` landing page (phase 5).
- Section 4.1 `ParallelProcessor`: Task 6 covers pool, phaser, pre/post tick, hook entry points, thread tracker, crash report, inline fallback, exception rethrow at `postTick` (section 9).
- Section 4.2 ported packages: Tasks 4, 5, 7. `DebugHookTerminator` is dropped (its chunk-timeout hooks are phase 4 territory); noted in Task 13 changelog by the "no parallel hooks" line and recorded here.
- Section 4.3 config: Task 3 (common), 9 (Fabric), 10 (NeoForge); spec amended in Task 1 step 13.
- Section 4.4 platform: Task 2.
- Section 7 tests: Tasks 3, 4, 5, 6 (unit), 11 (smoke), 12 (CI).
- Section 10 phase 1 exit criteria: Task 11 step 6 and Task 12 step 3.
