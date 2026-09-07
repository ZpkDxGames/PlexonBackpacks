# Changelog

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
