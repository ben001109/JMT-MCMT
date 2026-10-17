# MCMT - Minecraft Multi-Threading Mod

Server-side mod that parallelises world, entity, block entity and environment
ticks. Originally written by [jediminer543](https://github.com/jediminer543/JMT-MCMT)
for Forge 1.15/1.16; this fork ports it to current Minecraft versions on
several mod loaders.

**This branch holds no code.** Each Minecraft version lives on its own branch.

## Version matrix

| Minecraft | Loaders | Development branch | Single-loader branches (generated, read-only) | Status |
|---|---|---|---|---|
| 1.21.1 | Fabric, NeoForge | [`1.21.1`](../../tree/1.21.1) | [`1.21.1-fabric`](../../tree/1.21.1-fabric), [`1.21.1-neoforge`](../../tree/1.21.1-neoforge) | alpha: all four hook kinds parallel; passes the automated smoke test with a connected player; not yet tested with mods or multiple players |
| 1.18.2 | Forge | [`1.18.2-forge-legacy`](../../tree/1.18.2-forge-legacy) | - | frozen upstream code |
| 1.16.5 / 1.15.2 | Forge | [`1.16.5-forge-legacy`](../../tree/1.16.5-forge-legacy) | - | frozen upstream code |

Planned next: other 1.21.x versions and 1.20.1 from the `1.21.1` branch.

## How it works

The vanilla server ticks each dimension, then every entity, block entity and
chunk in sequence. MCMT runs those loops on a worker pool and waits for them
at the end of the tick, with filters that serialise known-unsafe block
entities (pistons, unknown modded ones via chunk locks). Since 1.21.1 the
hooks are Mixins shared by all loaders; see the branch README and
`docs/superpowers/` on the development branch for the design.

## Important notice

This changes a lot of Minecraft's internal processing. Other mods may break
in ways their authors cannot reproduce. Do not report MCMT-induced issues to
other mod authors; report them here with the crash report (it includes an
MCMT section) and the output of `/mcmt config state`.

## Downloads

Release jars are attached to [GitHub releases](../../releases), one per
loader, named `jmt_mcmt-<mcversion>-<loader>-<version>.jar`.

## Licence

BSD-2-Clause, see `LICENCE`.
