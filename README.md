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

Phase 1 (skeleton): the mod loads on both loaders, config and `/mcmt`
commands work, the thread pool starts. No parallelisation hooks are active
yet; those arrive in phase 2 (world + entity), phase 3 (block entity +
environment) and phase 4 (chunk cache).

## Tests

    ./gradlew :common:test
    bash tests/smoke/run-smoke.sh fabric 120
    bash tests/smoke/run-smoke.sh neoforge 120

## Licence

BSD-2-Clause, see `LICENCE`. Original author: jediminer543.
