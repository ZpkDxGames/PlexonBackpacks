# Changelog

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
