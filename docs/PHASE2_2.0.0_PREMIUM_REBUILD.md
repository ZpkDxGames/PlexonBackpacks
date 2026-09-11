# PlexonBackpacks 2.0.0 — Phase 2 Premium Rebuild

## Baseline and release boundary

- Rollback baseline: `v1.2.1` (`488faf508d0708e60b2caab64a97a7e63b06a7c8`).
- The repository default branch is legacy `1.0.0-Beta`; `main` is behind the published 1.2.1 baseline and is not a safe Phase 2 base.
- Phase 2 branch: `phase2/2.0.0-premium-rebuild`.
- Target: `2.0.0-rc.1` until PlexonCraft runtime certification is completed.
- Stable `2.0.0` MUST NOT be published without the runtime gate in this document.

## Existing architecture to preserve

The 1.2.1 implementation already contains mature, safety-relevant infrastructure that must not be replaced without a defect:

1. `BackpackItemFactory` uses authoritative UUID identity in PDC (`backpack_id`) plus tier/owner metadata. Display names, lore, titles and slots are not identity.
2. `BackpackRecord` is the authoritative in-memory record and clones `ItemStack` contents at its boundary.
3. `BackpackDataStore` is a main-thread record cache backed by an append-only CSV journal, with coalesced async writes and periodic compaction.
4. Item payloads use Paper `ItemStack.serializeItemsAsBytes` / `deserializeItemsFromBytes`, preserving complete Bukkit/Paper item state instead of lossy manual serialization.
5. `BackpackService` is the authoritative state/session service. Storage mutation must continue to route through it.
6. Existing session locks prevent a second player from opening the same UUID and prevent one player from opening multiple backpack sessions.
7. Existing listener protections block direct backpack nesting, block active-backpack movement/drop/swap and persist on close/quit.
8. PlexonCore integration and public Bukkit service API introduced in 1.2.x remain intact.

## Defects / Phase 2 gaps confirmed in source

- Backpack GUI is a raw storage inventory with no dedicated control/status row, sorting, quick deposit or pagination.
- The 54-slot tier leaves no safe space for controls without pagination.
- Admin GUI is primarily a backpack giver rather than an inspection/recovery console.
- `/backpack give` may drop overflow on the ground, which conflicts with Phase 2 custody requirements.
- 1.x CSV rows have no explicit schema marker/version.
- CSV load currently skips malformed rows; Phase 2 must fail closed instead of silently accepting partial state.
- Legacy YAML migration skips malformed records; Phase 2 must preserve the source and refuse ambiguous migration.
- Automated test coverage is currently limited to Core compatibility.
- Build/release workflows are pinned to 1.2.1 filenames/tags.
- The compatibility POM is stale relative to the canonical Gradle build.

## Phase 2 architecture

### Identity and authoritative state

- UUID/PDC remains the single stable backpack identity.
- A backpack item is a reference to persisted state; item lore/tier metadata may be refreshed from authoritative state but never becomes the database.
- No database write API may mutate backpack contents, owner, tier or lifecycle without `BackpackService` authorization.

### Session registry

Introduce an explicit session registry abstraction with atomic main-thread reservation semantics:

- one authoritative session per backpack UUID;
- one authoritative backpack session per player;
- deterministic rejection reason for player-already-open and backpack-in-use;
- session UUID generated once and retained through close/force-close;
- stale-session recovery through administrator force-close;
- admin inspection refuses an authoritative mutable view while the owner session is active unless the action is explicitly force-close first.

All Bukkit inventory/player access remains primary-thread only.

### Premium storage GUI

Use a storage-first paginated inventory:

- up to 45 storage slots per page;
- final row reserved for status/actions;
- capacity remains the configured tier capacity (9–54 in the initial configuration);
- 54-slot backpacks therefore use two pages rather than sacrificing nine storage slots;
- controls: information/status, previous page, sort, quick deposit, next page, close;
- controls are immutable GUI items and never persisted as backpack contents;
- title and lore use MiniMessage and the restrained Plexon Phase 2 visual language.

The holder tracks page number, capacity and visible storage span so GUI slots map deterministically to authoritative record slots.

### Item custody transaction rules

Any plugin-driven move follows:

`validate -> snapshot/reserve -> mutate -> commit/persist -> rollback on failure`

Operations covered:

- quick deposit;
- controlled withdrawal paths;
- sorting;
- tier upgrade;
- administrative recovery/metadata repair.

Normal GUI click handling must never allow a control item, the active backpack itself, or any backpack UUID to enter storage.

Protection cases:

- shift click;
- number-key/hotbar swap;
- offhand swap;
- double click/collect-to-cursor where it can cross the top inventory;
- drag into the storage region;
- cursor backpack;
- active-backpack movement/drop/swap.

Nested backpacks are blocked by PDC identity, not display text. Direct and plugin-driven insertion paths both enforce the rule.

### Persistence and schema

Retain the append-only CSV journal and Paper byte serialization.

Add:

- explicit Phase 2 schema marker (`schema-version.txt`);
- automatic backup of 1.x persistence before first successful Phase 2 schema adoption;
- idempotent adoption/migration;
- fail-closed load on malformed CSV/YAML instead of silently skipping unknown state;
- diagnostics exposing schema version, dirty count, writer status, journal row count and last persistence failure;
- synchronous final flush remains mandatory on disable.

The CSV item payload remains exact Paper ItemStack bytes. No manual item field mapping is introduced.

### Tier progression / upgrades

Tier ordering follows configuration order. A backpack may upgrade only to the immediate next tier.

- bounded by configured tier list;
- deterministic target/capacity;
- atomic record tier update;
- backpack item metadata refresh only after authoritative state update succeeds;
- rollback restores record tier and payment on failure;
- optional economy charging is provided through a small gateway. Vault/TheosisEconomy remains authoritative when available; no internal economy balance is created.
- if economy integration is unavailable or an upgrade cost is zero, no charge is attempted.

Default configuration keeps upgrade costs explicit and bounded per target tier.

### Administration

Commands / GUI must support:

- inspect held backpack and inspect by UUID;
- owner, tier, capacity, last access, UUID, session state;
- force-close stale/active session with permission;
- recover an existing backpack reference by UUID only when the authoritative record exists;
- repair item metadata only when UUID linkage is already proven;
- destructive/ownership-changing recovery is not automatic.

### API/events

Maintain existing API compatibility and add read-only/admin-safe Phase 2 surfaces where useful:

- lookup active session by backpack UUID;
- request force-close through service API only;
- expose capacity/page-independent record metadata;
- emit an upgrade event after committed upgrade.

### Diagnostics and optional integrations

`/backpack diagnostics` expands with persistence/session/schema state and publication-readiness checks.

PlaceholderAPI integration is optional and should only be registered when the dependency is present. Useful player-scoped values are active backpack UUID/tier/capacity and active session state; no placeholder may cause synchronous disk IO.

## Automated verification contract

Tests must cover, using pure unit tests where possible and Paper/MockBukkit-compatible tests where server objects are necessary:

- UUID identity parsing/validation;
- session reservation and duplicate-open rejection;
- race-style reservation ordering;
- close/logout/shutdown snapshot path contracts;
- exact ItemStack byte serialization round trip;
- custom PDC preservation;
- nested backpack rejection predicate;
- click policy for shift-click, number key, offhand swap, drag and cursor backpack;
- full inventory / no-space custody result;
- upgrade success and max-tier rejection;
- upgrade rollback and payment refund contract;
- migration backup/adoption idempotency;
- malformed legacy/CSV rejection;
- API compatibility contracts;
- built JAR contents/version;
- PlexonCore not shaded into distribution.

CI failures are fixed in source/tests; checks are not bypassed or weakened.

## Release workflow

1. Build/test on Java 25 against Paper `26.2.build.121-stable` and PlexonCore 2.0.4.
2. Produce `PlexonBackpacks-2.0.0-rc.1.jar`.
3. Produce and verify `SHA256SUMS.txt`.
4. Open/update draft PR from `phase2/2.0.0-premium-rebuild`.
5. Keep PR unmerged while runtime certification is unavailable.
6. Freeze the exact candidate commit.
7. Tag that exact commit `v2.0.0-rc.1`.
8. Publish GitHub prerelease with the candidate JAR and checksum.

## PlexonCraft runtime certification gate

Stable promotion requires all of the following against the frozen RC artifact:

- create/open/close backpack;
- exact item round-trip;
- custom Plexon/plugin items;
- duplicate-session attempts;
- death/logout/reconnect;
- restart persistence;
- full inventory behavior;
- tier upgrade;
- migration rehearsal from representative 1.2.1 data;
- Spark/MSPT comparison against the Phase 1 baseline;
- minimum 30-minute soak;
- zero HIGH/CRITICAL defects.

If PlexonCraft runtime access is unavailable, status is **RC RELEASED / RUNTIME PENDING** and stable `2.0.0` is forbidden.