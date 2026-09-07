# PlexonBackpacks 1.2.0

Core-native integration release built conservatively on the production `1.1.0-Release` source.

### Added

- PlexonCore 1.0.0 `backpacks` module registration with CORE/STANDALONE modes
- Public `PlexonBackpacksAPI` via Bukkit ServicesManager
- Immutable backpack/tier/open-session views
- Opened, Closed and Bound public events with stable session/event IDs
- `/backpack diagnostics`
- Verified Core API provisioning and no-shading checks
- Java 25 CI and tag-driven release checksums

### Preserved

Existing backpack UUIDs, PDC keys, tiers, owners, contents, CSV journal format, legacy YAML migration, anti-nesting, simultaneous-open locks, unique crafting/give IDs and final synchronous shutdown save are unchanged by the Core migration.

### Upgrade

Stop the server, replace the 1.1.0 JAR with `PlexonBackpacks-1.2.0.jar`, keep the entire `plugins/PlexonBackpacks/` directory, then start and validate `/plexon modules` plus `/backpack diagnostics`.
