# MCMT for Minecraft 1.21.1 (Fabric + NeoForge)

Development branch for the 1.21.1 port of JMT-MCMT, the server-side
Minecraft multithreading mod. Both loader jars are built from this branch:

    ./gradlew buildAll

Jars land in `fabric/build/libs/` and `neoforge/build/libs/`. Java 21 is
required.

## Layout

- `common/` all game logic and Mixins, written against MojMap names
- `fabric/` Fabric entrypoint, TOML config bridge, command registration
- `neoforge/` NeoForge entrypoint, `ModConfigSpec` bridge, event wiring
- `tests/smoke/` headless dedicated-server smoke test (both loaders)
- `docs/superpowers/` design spec and implementation plans

## Status

Phase 3 complete: level, entity, block entity and environment ticks run on the
MCMT worker pool on both loaders, with the concurrent chunk cache and
thread-safe entity/block-entity storage in place. The headless smoke test
(200 entities plus block entities, 60 s) passes on Fabric and NeoForge.
Environment ticks only run near players, so they are not exercised headless yet.

Known limitations at this stage: only tested headless with no players; mod
compatibility untested.

## Tests

    ./gradlew :common:test
    bash tests/smoke/run-smoke.sh fabric 120
    bash tests/smoke/run-smoke.sh neoforge 120

## Licence

BSD-2-Clause, see `LICENCE`. Original author: jediminer543.
