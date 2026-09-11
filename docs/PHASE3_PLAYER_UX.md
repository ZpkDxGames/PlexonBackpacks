# PlexonBackpacks Phase 3 — Player UX Overhaul

## Base and release boundary

- Exact base: `ddaa3034e310467a9c9f2351f5ff6fb0cd99dbe5`
- Base branch: `phase2/2.0.0-premium-rebuild`
- Phase 3 branch: `phase3/player-ux-overhaul`
- Runtime certification: **PENDING**
- No Phase 3 RC or stable release is published by this work.

## Product boundary

Phase 3 changes presentation and player routing only. The Phase 2 authorities remain authoritative for UUID/PDC identity, exact ItemStack storage, persistence, capacity normalization, session ownership, stale-view rejection, cursor custody, nested-backpack prevention, save/reload behavior, force-close recovery, and upgrade charge/refund semantics.

The GUI never owns an economy balance and never withdraws or refunds money. `BackpackService.upgrade` remains the only upgrade transaction entry point.

## Storage experience

The storage area remains entirely usable storage. A single fixed final row is reserved for controls, as in Phase 2:

`[ Info ] [ ] [ Previous ] [ Sort ] [ Quick Deposit ] [ Close ] [ Next ] [ ] [ ]`

The Info control summarizes tier, owner, used/free capacity, page, storage state, and admin-override access when applicable. It never exposes UUID, PDC, schema, revision, or session identifiers.

Sort accurately describes the existing implementation: one explicit **Material → Amount** sort across the authoritative backpack contents. No new sort engine or render-time sorting is introduced.

Quick Deposit accurately describes the existing implementation: it attempts to move all eligible items from the player's storage inventory into available backpack space, while excluding backpacks (including backpacks contained in protected container items). It remains transactional and uses the existing rollback/persistence path.

## Backpack Info / Upgrade

The dedicated 27-slot surface uses:

- slot 4 — Backpack summary
- slot 11 — Current tier
- slot 13 — Next tier
- slot 15 — Upgrade status / primary action
- slot 20 — Storage rules
- slot 22 — Back to storage
- slot 26 — Close

The upgrade preview shows current tier/capacity, next tier/capacity, exact configured cost, and one explicit state: READY, MAX TIER, MISSING REQUIREMENT, INSUFFICIENT FUNDS, ECONOMY UNAVAILABLE, UPGRADES DISABLED, CLOSE STORAGE FIRST, NOT YOUR BACKPACK, or BACKPACK ITEM REQUIRED.

Opening this preview is non-mutating. A READY click is the confirmation action. The holder accepts one submission only, then `BackpackService.upgrade` revalidates and performs the authoritative transaction. A changed tier invalidates the old preview and restores current information.

## Navigation and custody

Opening Info from a live backpack first closes the authoritative storage inventory. Phase 2 close persistence and session release must succeed before the Info GUI opens. Back reopens the same physical backpack reference and returns to the originating page when that page still exists.

Page changes, Sort, Quick Deposit, and Info require an empty cursor. Existing nested-backpack extraction compatibility and force-close cursor restoration remain unchanged.

## Compatibility

No database/storage schema change is introduced. `config-version` remains 3. Existing Phase 2 tier, recipe, upgrade-cost, storage, session, and economy configuration remains valid. New player-facing message defaults are additive; old server configurations continue to use Bukkit resource defaults for missing keys rather than being destructively reset.

Administrative inspection and recovery surfaces may still contain diagnostic identifiers. These are intentionally separate from ordinary player GUI presentation.

## Performance contract

Phase 3 adds no per-player repeating GUI task, no database query in cosmetic rendering, no PlaceholderAPI pass across storage items, and no independent ItemStack serialization. The only scheduling added to player UX is a one-shot transition after an authoritative storage close before opening Backpack Info.

Storage pages continue to render exact live ItemStacks only when opened or when a real page/sort/deposit state change requires rerendering. Static GUI controls contain only presentation data and do not become persistence authority.
