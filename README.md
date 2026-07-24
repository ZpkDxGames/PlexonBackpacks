# PlexonBackpacks

PlexonBackpacks is a lightweight, standalone tiered-backpack plugin for
Paper 26.2. Every backpack is a custom player-head item with its own persistent storage ID.
## Included in 1.0.0

- Five configurable tiers: Basic (9), Iron (18), Gold (27), Diamond (36), and
  Netherite (54 slots)
- Distinct custom-head textures for every default tier
- Fully configurable MiniMessage names, lore, GUI titles, messages, recipes,
  sizes, textures, permissions, and custom model data
- Unique storage per physical backpack
- Optional owner binding, including bind-on-first-open
- Simultaneous-open locking
- Backpack-in-backpack protection for clicks, shift-clicks, hotbar swaps,
  offhand swaps, and drags
- Placement, armor-dispenser, and active-backpack movement protection
- In-memory contents with batched asynchronous, atomic disk writes
- Final synchronous shutdown save and corrupt-data preservation
- Recipe discovery and safe one-at-a-time crafting

## Requirements

- Paper 26.2 or a compatible fork

## Install
1. Put `PlexonBackpacks-1.0.0.jar` in the server's `plugins/` directory.
2. Restart the server.
3. Edit `plugins/PlexonBackpacks/config.yml` if desired.
4. Run `/backpack reload` after configuration changes.

Backpack data is stored in
`plugins/PlexonBackpacks/backpacks-data.yml`. Do not edit that file while the
server is running.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/backpack` | Open the backpack in either hand | `plexonbackpacks.use` |
| `/backpack tiers` | List configured tiers | `plexonbackpacks.use` |
| `/backpack inspect` | Show the held backpack's identity | `plexonbackpacks.use` |
| `/backpack give <player> <tier> [amount]` | Give unique backpacks | `plexonbackpacks.give` |
| `/backpack reload` | Reload tiers, messages, and recipes | `plexonbackpacks.reload` |
| `/backpack save` | Force a complete data save | `plexonbackpacks.save` |

Aliases: `/backpacks`, `/bp`

## Configuration notes

- Tier sizes must be multiples of 9 from 9 through 54.
- `texture` accepts a `textures.minecraft.net` hash, a complete URL, or a
  standard base64 head texture value.
- Set a tier's `permission` to an empty string to make it available to
  everyone.
- Shift-click crafting is intentionally blocked because every crafted
  backpack needs a different storage UUID.
- Backpack contents are cached and saved every 200 ticks by default. Change
  `settings.autosave-interval-ticks` to tune the write-behind interval.

## Default head credits

The default textures use community heads from
[Minecraft-Heads.com](https://minecraft-heads.com/):

- Basic: Backpack (brown), head 874
- Iron: Backpack (white), head 1886
- Gold: Backpack (yellow), head 40205
- Diamond: Diamond Backpack, head 51739
- Netherite: Netherite Backpack, head 51738

Each texture can be replaced in `config.yml`.
