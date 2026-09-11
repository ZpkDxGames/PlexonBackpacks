# PlexonBackpacks 2.0.0

PlexonBackpacks is a tiered, item-safe backpack plugin for **Paper 26.2 / Java 25**. Every physical backpack is a custom item reference with a stable UUID backed by one authoritative persisted storage record.

## Stable 2.0 architecture

PlexonBackpacks treats UUID/PDC linkage as identity. Display name, lore, material, GUI title and slot position are presentation only.

The storage/custody boundary is deliberately conservative:

- exact Paper `ItemStack` byte serialization preserves metadata, components and foreign PDC;
- one authoritative session is allowed per player and per backpack UUID;
- an opened session persists current visible contents before its lock is released;
- failed close persistence keeps the session authoritative instead of silently unlocking stale state;
- cursor custody and active-reference protections prevent closing, dropping, swapping or nesting around an unresolved backpack session;
- quick deposit and sorting snapshot both sides of their mutation and roll back if synchronous persistence fails;
- upgrades persist authoritative tier/capacity changes and refund economy payment if the transaction fails;
- current-generation item references whose persisted record is missing fail closed instead of being recreated as empty storage.

## Persistence

Backpack data is stored under:

```text
plugins/PlexonBackpacks/backpacks-data.csv
plugins/PlexonBackpacks/schema-version.txt
```

Persistence schema `2` uses an append-only CSV journal with dirty-record snapshots, newest-row coalescing, asynchronous writes, bounded main-thread serialization, and atomic compaction. Pre-2.0 adoption creates a mandatory backup under `plugins/PlexonBackpacks/backups/pre-2.0/` before changing persistence state.

Compaction authority advances **only after a row completes a successful append**. Captured, queued or active-but-unwritten candidates cannot become the compacted authoritative row before their write succeeds.

Autosave snapshots open sessions on the primary thread, then hands immutable encoded rows to the asynchronous writer. Shutdown closes/snapshots sessions and performs a final synchronous flush.

## Configuration reload safety

`/backpack reload` first closes all authoritative sessions. The new file is then parsed and runtime subsystems are rebuilt as one logical boundary.

If parsing or runtime application fails:

- last-known-good in-memory configuration is restored;
- tier definitions remain last-good;
- admin-menu state, recipes and autosave scheduling are rebuilt from the restored configuration;
- the externally edited candidate file remains on disk so the administrator can correct it and retry.

A failed reload therefore cannot leave a mixed runtime with old tier definitions but new dynamic settings.

## Player and administration features

- Five configurable tiers from 9 to 54 slots.
- Custom player-head textures and custom model data.
- Ownership on first successful open, optional owner enforcement and bypass permission.
- Right-click opening from main/off hand.
- Simultaneous-open locking.
- Anti-nesting for cursor, shift-click, hotbar, offhand, drag and double-click paths.
- Placement, armor-dispenser, drop/swap and active-backpack movement protection.
- Quick deposit and deterministic sorting.
- Paid tier upgrades with safe refund semantics.
- Administrative giver, inspect, recover, repair and force-close tooling.
- Public read-safe Bukkit API and lifecycle events.
- `/backpack diagnostics` publication/session/storage visibility.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/backpack` | Open the backpack in either hand | `plexonbackpacks.use` |
| `/backpack upgrade` | Upgrade the held backpack when eligible | `plexonbackpacks.upgrade` |
| `/backpack tiers` | List configured tiers | `plexonbackpacks.use` |
| `/backpack inspect [uuid]` | Inspect held/authoritative backpack state | `plexonbackpacks.use` / `plexonbackpacks.inspect-any` |
| `/backpack gui` | Open the administrative tier giver | `plexonbackpacks.admin-gui` |
| `/backpack give <player> <tier> [amount]` | Give unique unbound backpacks | `plexonbackpacks.give` |
| `/backpack recover <uuid>` | Recover a reference only for existing state | `plexonbackpacks.recover` |
| `/backpack repair` | Repair proven held-reference metadata | `plexonbackpacks.repair` |
| `/backpack forceclose <uuid>` | Safely close an authoritative session | `plexonbackpacks.force-close` |
| `/backpack reload` | Reload configuration/runtime atomically | `plexonbackpacks.reload` |
| `/backpack save` | Force synchronous backpack persistence | `plexonbackpacks.save` |
| `/backpack diagnostics` | Show Core/API/session/storage summary | `plexonbackpacks.diagnostics` |

Aliases: `/backpacks`, `/bp`.

## Requirements

- Paper `26.2.build.121-stable` or compatible fork
- Java 25
- PlexonCore 2.0.4 is optional at runtime but recommended for ecosystem registration
- Vault/economy provider is optional for paid upgrades
- PlaceholderAPI is optional where its integration is enabled

PlexonCore is compile-only/provided and never shaded. If Core cannot be linked safely, backpack gameplay and the public API continue through standalone compatibility mode.

## Build and stable release

Gradle is the canonical build path:

```bash
bash scripts/provision-core.sh
gradle clean test check jar --no-daemon
```

The provisioning script downloads PlexonCore 2.0.4, verifies SHA-256 `61d625a717da9f46ee9231e1970d84b4c317ae12cf4090cdf7c9d39b6a1a9baf`, and installs it only into the temporary `.deps/` repository.

Stable CI proves accepted Phase 3 source `594c773e4cc4a696ce790a61b70dc8074f5f0c05` and final RC2 lineage `fdc88427bbfbaa5a517a0847def6e9617120695f`, requires a non-empty zero-failure/error/skip test suite, verifies Java class major 69 and dependency isolation, and emits exact checksum/provenance evidence.

Stable publication runs only from `release/stable` when it equals exact current `main`. It rebuilds/retests, publishes `PlexonBackpacks-2.0.0.jar` plus `SHA256SUMS.txt`, `TEST_SUMMARY.txt` and `PROVENANCE.txt`, then downloads and verifies those public assets before the workflow succeeds.

Live PlexonCraft migration, custody, restart, MSPT and soak certification is a deployment follow-up. Missing live evidence is recorded as `runtime_certification=NOT_EXECUTED`; it does not block reproducible GitHub source/release closure.

Stable rollback baseline: `v1.2.1` at `488faf508d0708e60b2caab64a97a7e63b06a7c8`, JAR SHA-256 `1a0eb1f3a60ac3c1b1a213281876a0026124f6d54190d158c77134b4c2cec018`.
