# Changelog

## 0.25.0-alpha.1 (unreleased) - 1.21.1 Fabric + NeoForge

- Rebuilt as a multiloader project (`common` / `fabric` / `neoforge`).
- Removed the Forge JS coremods and the `sync-fu` ModLauncher service.
  Hooks will be Mixins, shared by both loaders.
- Config keys are unchanged from 1.18. NeoForge uses `ModConfigSpec`,
  Fabric uses a bundled night-config TOML file (`config/jmt_mcmt-common.toml`).
- `/mcmt debug test structures` removed (the API it used no longer exists).
- `/mcmt config temanage list` now prints the lists instead of "NYI".
- Fixed `SerDesRegistry.initLookup` never populating the shared whitelist.
- Phase 2: level ticks and entity ticks run on the worker pool (Mixins on
  `MinecraftServer.tickChildren` and `ServerLevel.tickNonPassenger`); MCMT
  worker threads count as the server thread for task scheduling.
- Concurrent chunk cache in front of `ServerChunkCache.getChunk/getChunkNow`
  for worker threads (replaces the 1.18 `ParaServerChunkProvider`).
- Thread-safe replacements for the entity storage collections
  (`ClassInstanceMultiMap`, `EntitySectionStorage`, `EntityLookup`,
  `PersistentEntitySectionManager`, `ChunkMap.entityMap`, `EntityTickList`)
  and for `Level.random`, replacing the `sync-fu` fastutil transformer.
- `/mcmt stats` prints cumulative parallel task counts.
- Phase 3: block entity ticks (`Level.tickBlockEntities`) and per-chunk
  environment ticks (`ServerChunkCache.tickChunks`) run on the worker pool.
  SerDes filters key on the real `BlockEntity` class behind the 1.17+ ticker
  wrappers; `TETick` now targets `BlockEntity`.
- Vanilla's `ThreadingDetector` (PalettedContainer, LegacyRandomSource) is a
  real reentrant lock instead of a crash.
- Thread-safe `ChunkAccess.blockEntities`, `LevelChunk.tickersInLevel`,
  `ChunkHolder` change tracking, `LevelTicks` scheduling and
  `ServerLevel.onBlockStateChange`.
