# PlexonBackpacks

PlexonBackpacks is a lightweight, standalone tiered-backpack plugin for
Paper 26.2. Every backpack is a custom player-head item with its own persistent
storage ID.

## Included in 1.1.0

- Five configurable tiers: Basic (9), Iron (18), Gold (27), Diamond (36), and
  Netherite (54 slots)
- The requested custom-head texture for every default tier
- OP/admin giver GUI with one-click unbound backpacks and an author easter egg
- Right-click opening against blocks or while looking at the air
- Ownership applied only when an unbound backpack is first opened
- Fully configurable MiniMessage names, lore, GUI titles, messages, recipes,
  sizes, textures, permissions, and custom model data
- Unique storage per physical backpack
- Simultaneous-open locking
- Backpack-in-backpack protection for clicks, shift-clicks, hotbar swaps,
  offhand swaps, and drags
- Placement, armor-dispenser, and active-backpack movement protection
- In-memory contents with dirty-record-only CSV write-behind
- Asynchronous append-only writes and infrequent atomic CSV compaction
- Automatic migration from the 1.0.0 YAML data file
- Change detection for open inventories, avoiding unchanged autosave work
- Final synchronous shutdown save and corrupt-data preservation
- Recipe discovery and safe one-at-a-time crafting

## Requirements

- Paper 26.2 or a compatible fork
- Java 25 or newer

## Build

The project includes Maven and Gradle Kotlin DSL build files:

```bash
mvn clean package
```

Or:

```bash
gradle clean build
```

The release JAR is generated at either:

```text
target/PlexonBackpacks-1.1.0.jar
build/libs/PlexonBackpacks-1.1.0.jar
```

## Install

1. Put `PlexonBackpacks-1.1.0.jar` in the server's `plugins/` directory.
2. Restart the server.
3. Edit `plugins/PlexonBackpacks/config.yml` if desired.
4. Run `/backpack reload` after configuration changes.

Backpack data is stored in
`plugins/PlexonBackpacks/backpacks-data.csv`. Do not edit that file while the
server is running.

When upgrading from 1.0.0, `backpacks-data.yml` is migrated automatically and
kept as `backpacks-data.migrated.yml`.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/backpack` | Open the backpack in either hand | `plexonbackpacks.use` |
| `/backpack tiers` | List configured tiers | `plexonbackpacks.use` |
| `/backpack inspect` | Show the held backpack's identity | `plexonbackpacks.use` |
| `/backpack gui` | Open the OP/admin tier giver | `plexonbackpacks.admin-gui` |
| `/backpack give <player> <tier> [amount]` | Give unique, unbound backpacks | `plexonbackpacks.give` |
| `/backpack reload` | Reload tiers, messages, recipes, and GUI items | `plexonbackpacks.reload` |
| `/backpack save` | Force a complete data save | `plexonbackpacks.save` |

Aliases: `/backpacks`, `/bp`

## Performance design

The CSV file is an append-only journal. Normal autosaves serialize only records
that changed since the previous pass, then append them on Paper's asynchronous
scheduler. If multiple pending snapshots exist for one backpack, only the
newest is queued.

The current row for each backpack is compacted into a fresh CSV file after
`settings.csv-compaction-threshold-updates` superseded rows. Compaction runs
off the main thread and finishes with atomic replacement when supported by the
host filesystem.

Open backpack inventories use a content hash so an unchanged backpack is not
cloned, serialized, or queued every autosave cycle. A final synchronous save
protects the latest state during a clean shutdown.

## Configuration notes

- Tier sizes must be multiples of 9 from 9 through 54.
- `texture` accepts a `textures.minecraft.net` hash, a complete URL, or a
  standard base64 head texture value.
- Set a tier's `permission` to an empty string to make it available to
  everyone.
- `plexonbackpacks.admin-gui` defaults to OP and can be granted explicitly.
- Shift-click crafting is intentionally blocked because every crafted
  backpack needs a different storage UUID.
- Backpacks received from recipes, commands, or the GUI are always unbound.
  With ownership enabled, the first successful opening claims the backpack.
- Change `settings.autosave-interval-ticks` to tune the write-behind interval.

## Author

Created by [ZpkDxGames](https://namemc.com/profile/ZpkDxGames.1), related to
PlexonChats and GhostBlocks.

## License

MIT
