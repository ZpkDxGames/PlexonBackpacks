# PlexonBackpacks 2.0.0-rc.1

Phase 2 release candidate built from the published PlexonBackpacks 1.2.1 rollback baseline.

## Candidate status

**RC RELEASED / RUNTIME PENDING**

This release is intentionally a prerelease. It must not be promoted to stable `2.0.0` until the frozen artifact passes the PlexonCraft runtime certification gate.

## Phase 2 changes

- Stable UUID/PDC identity remains authoritative; display names, lore, GUI titles and slot positions are never identity.
- New Phase 2 backpack references carry an item schema marker so missing current-generation state cannot be silently recreated as empty storage.
- Backpack creation and crafting register/persist the UUID before physical custody transfer.
- Existing exact Paper `ItemStack` byte serialization remains the persistence format, preserving custom item metadata/components/PDC without manual lossy mapping.
- Persistence schema adoption creates a mandatory pre-2.0 backup and fails closed on malformed legacy/CSV state.
- One authoritative session is enforced per backpack UUID and per player.
- Premium storage-first GUI adds status, sorting, quick deposit, pagination and safe close controls without reducing configured capacity.
- 54-slot backpacks retain all 54 storage slots through two pages (45 + 9).
- Direct backpack nesting and common container-mediated nesting are blocked using PDC identity.
- Shift-click, number-key, offhand swap, drag, cursor and active-reference movement paths are hardened.
- Sorting and quick-deposit use snapshot/commit/rollback custody transactions.
- Tier upgrades are deterministic, bounded to the next configured tier and transactionally charged through a Vault-compatible authoritative economy provider. No internal economy is introduced.
- Full-inventory admin give/recovery no longer silently drops candidate items.
- Admin tooling adds UUID inspection, stale/active session force-close, confirmed reference recovery and proven metadata repair.
- Diagnostics expose persistence schema, dirty state, async writer state, journal size, last persistence failure, economy provider and source publication readiness.
- Public API adds backpack-scoped active-session lookup; committed upgrades emit `PlexonBackpackUpgradedEvent`.

## Migration from 1.x

1. Stop the server cleanly and retain a copy of the entire `plugins/PlexonBackpacks/` directory.
2. Install this RC without deleting the existing CSV/YAML storage.
3. On first Phase 2 startup, PlexonBackpacks creates `backups/pre-2.0/` before adopting legacy persistence.
4. Existing valid CSV is read without lossy transformation. Legacy YAML is migrated only when every record validates.
5. Any malformed/unknown persisted state aborts startup instead of being skipped or reset.
6. Existing 1.x physical references are adopted on first open only when no Phase 2 item-schema marker is present; current-generation references with missing state are rejected.

Rollback: stop the server, restore the complete pre-upgrade plugin data directory (including the 1.2.1 configuration/data) and reinstall the 1.2.1 JAR. Do not mix Phase 2-written state with an older binary.

## Required PlexonCraft runtime gate before stable 2.0.0

- create/open/close backpack
- exact item round-trip
- custom Plexon/plugin items
- duplicate-session attempts
- death/logout/reconnect
- restart persistence
- full inventory behavior
- tier upgrade and economy rollback/refund
- migration rehearsal from representative 1.2.1 state
- Spark/MSPT comparison against the Phase 1 baseline
- minimum 30-minute soak
- zero HIGH/CRITICAL defects

Until all runtime gates pass against this exact frozen artifact, stable promotion is prohibited.
