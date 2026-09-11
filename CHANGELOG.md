# Changelog

## 2.0.0 - 2026-09-11

### Premium storage and player UX
- Promoted the accepted Phase 2 storage/custody architecture and Phase 3 player UX line to stable.
- Preserved UUID/PDC identity, exact ItemStack serialization, one-session-per-player/backpack locking, cursor custody, anti-nesting, safe crafting, quick deposit, deterministic sorting, tier upgrades, recovery/repair tooling and public API/event contracts.
- Preserved fail-closed behavior for missing current-generation authoritative state and ambiguous force-close/custody conditions.

### Reliability fixes from final source audit
- Changed CSV compaction authority so captured/pending rows cannot enter the compacted authoritative state until their append has completed successfully.
- Added regression coverage proving compaction keeps the prior committed row visible until the candidate write succeeds.
- Made configuration/runtime reload rollback-safe: a rejected candidate restores last-known-good live configuration and rebuilds admin menu, recipes and autosave scheduling.
- Externally edited rejected config files remain on disk for correction, while runtime settings and tier definitions remain last-good.
- Added regression coverage for failed reload rollback followed by a corrected successful reload.

### Repository/release closure
- Promoted Gradle, plugin descriptor and compatibility POM metadata from `2.0.0-rc.2` to `2.0.0`.
- Replaced RC/one-shot publication paths with canonical Build + exact-current-`main` stable Release workflows.
- Stable publication rebuilds/retests exact source, publishes JAR/checksum/test/provenance evidence, downloads the public assets, and verifies checksum/source/Phase3/RC2 provenance before success.
- Historical prerelease/stable tags remain immutable.
- Stable rollback remains `v1.2.1` at `488faf508d0708e60b2caab64a97a7e63b06a7c8`, JAR SHA-256 `1a0eb1f3a60ac3c1b1a213281876a0026124f6d54190d158c77134b4c2cec018`.
- Final accepted RC2 lineage remains `fdc88427bbfbaa5a517a0847def6e9617120695f`, historical JAR SHA-256 `fd804063ca04aef34be7cc30d5e1672b65bb02b0755477cdbabb8bcc1f0cbc3c`.
- Live PlexonCraft runtime certification remains a deployment follow-up and may be recorded as `NOT_EXECUTED` in stable release provenance.

## 1.2.0 - 2026-09-07

- Added PlexonCore 1.0.0 module registration with CORE/STANDALONE fallback
- Added the public Bukkit `PlexonBackpacksAPI` and immutable metadata/session views
- Added opened, closed and first-bind public lifecycle events with stable event/session IDs
- Hardened first-open binding so denied or cancelled opens cannot claim an unbound backpack
- Added `/backpack diagnostics` and `plexonbackpacks.diagnostics`
- Added compile-only Core dependency provisioning with a pinned SHA-256 and no Core shading
- Added Java 25 Gradle CI plus tag-driven `v1.2.0` release automation and checksums
- Preserved the 1.1.0 CSV journal, PDC keys, UUIDs, ownership, anti-nesting and locking behavior

## 1.1.0 - 2026-07-24

- Replaced full YAML snapshots with dirty-record-only append-only CSV storage
- Added asynchronous CSV writes, atomic compaction, and legacy YAML migration
- Added open-inventory change detection to skip unchanged autosave work
- Added an OP/admin giver GUI for all five tiers
- Added the ZpkDxGames author skin/profile easter egg
- Ensured right-click-air interactions open backpacks
- Changed all command and GUI gives to remain unbound until first opening
- Updated all five default custom-head textures

## 1.0.0 - 2026-07-24

- Initial Paper 26.2 release
- Added five configurable backpack tiers from 9 to 54 slots
- Added custom player-head visuals and configurable crafting recipes
- Added UUID-backed persistent contents and optional owner binding
- Added simultaneous-open locking and backpack nesting prevention
- Added write-behind autosaves with atomic file replacement
- Added `/backpack` player and administrative commands
