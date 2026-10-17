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
- Phase 1: no parallel hooks active yet.
