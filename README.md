# PlexonBackpacks

PlexonBackpacks is a lightweight tiered-backpack plugin for Paper 26.2. Every physical backpack is a custom player-head item with its own stable UUID and persistent storage record.

## 1.2.0

PlexonBackpacks 1.2.0 is the Core-native integration release. Gameplay, PDC identity, ownership rules and the append-only CSV journal remain owned by PlexonBackpacks.

- PlexonCore 1.0.0 integration with `CORE` and `STANDALONE` runtime modes
- `backpacks` module registration with Core API range `>=1.0 <2.0`
- Public `PlexonBackpacksAPI` through Bukkit `ServicesManager`
- Immutable backpack, tier and open-session views
- `PlexonBackpackOpenedEvent`, `PlexonBackpackClosedEvent` and `PlexonBackpackBoundEvent`
- Stable per-session and per-event IDs
- `/backpack diagnostics`
- Verified Core dependency provisioning without shading Core into the plugin JAR
- Tag-driven `v1.2.0` release workflow with SHA-256 checksums

PlexonCore is a soft dependency. If Core is absent or cannot be linked safely, backpack gameplay and the public Backpack API/events continue in standalone compatibility mode.

## Preserved backpack features

- Five configurable tiers: Basic (9), Iron (18), Gold (27), Diamond (36), Netherite (54)
- Custom player-head textures and custom model data
- Unique storage UUID per physical backpack
- Ownership applied only on the first successful opening
- Optional owner enforcement and bypass permission
- Right-click block/air opening from main or off hand
- Administrative giver GUI and unique unbound give/craft output
- Simultaneous-open locking
- Complete anti-nesting protection for cursor, shift-click, hotbar, offhand and drag paths
- Placement, armor-dispenser and active-backpack movement/drop protection
- Dirty-record-only append-only CSV write-behind
- Asynchronous writes, newest-row coalescing and atomic compaction
- Legacy YAML migration, corrupt-file preservation and final synchronous shutdown save
- Change detection for open inventories

## Requirements

- Paper 26.2 or compatible fork
- Java 25
- PlexonCore 1.0.0 is optional at runtime but recommended for ecosystem registration

Backpack data remains at `plugins/PlexonBackpacks/backpacks-data.csv`. Do not replace or edit this file during the 1.2.0 upgrade.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/backpack` | Open the backpack in either hand | `plexonbackpacks.use` |
| `/backpack tiers` | List configured tiers | `plexonbackpacks.use` |
| `/backpack inspect` | Show the held backpack identity | `plexonbackpacks.use` |
| `/backpack gui` | Open the administrative tier giver | `plexonbackpacks.admin-gui` |
| `/backpack give <player> <tier> [amount]` | Give unique, unbound backpacks | `plexonbackpacks.give` |
| `/backpack reload` | Reload tiers, messages, recipes and GUI items | `plexonbackpacks.reload` |
| `/backpack save` | Force a synchronous backpack save | `plexonbackpacks.save` |
| `/backpack diagnostics` | Show Core/API/session/storage summary | `plexonbackpacks.diagnostics` |

Aliases: `/backpacks`, `/bp`

## Building

Gradle is the canonical build path. PlexonCore is compile-only and is never committed or shaded.

```bash
bash scripts/provision-core.sh
gradle clean test check jar
```

The provisioning script downloads the official `PlexonCore-1.0.0.jar`, verifies its pinned SHA-256, and places it in a temporary local Maven repository under `.deps/`.

## Upgrade from 1.1.0

Stop the server, replace `PlexonBackpacks-1.1.0.jar` with `PlexonBackpacks-1.2.0.jar`, keep the entire `plugins/PlexonBackpacks/` directory unchanged, and start the server. Existing backpack UUIDs, owners, tiers and contents require no data migration.

See `docs/MIGRATION_1_2.md`, `docs/API.md` and `docs/PLEXONCORE.md` for the integration contracts and validation checklist.

## Author

Created and maintained by Tonim / ZpkDxGames.
