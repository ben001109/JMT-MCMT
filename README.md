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

Phase 4 complete: level, entity, block entity and environment ticks run on the
MCMT worker pool on both loaders. The smoke test runs a headless dedicated
server with a real protocol bot (mineflayer) connected, 200 entities, block
entities, farmland under a raised random tick speed and a thunderstorm, for
60 s, and passes on Fabric and NeoForge with zero errors.

Known limitations at this stage: one player only, vanilla only; mod
compatibility and multi-player load are untested.

## Tests

    ./gradlew :common:test
    bash tests/smoke/run-smoke.sh fabric 120      # needs Node.js 18+ for the player bot
    bash tests/smoke/run-smoke.sh neoforge 120

## Licence

BSD-2-Clause, see `LICENCE`. Original author: jediminer543.
