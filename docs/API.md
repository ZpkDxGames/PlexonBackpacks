# PlexonBackpacks 2.0 Public API

PlexonBackpacks registers `com.zpkdxgames.plexonbackpacks.api.PlexonBackpacksAPI` through Bukkit `ServicesManager` in both CORE and STANDALONE modes.

## Lookup

```java
var registration = Bukkit.getServicesManager().getRegistration(PlexonBackpacksAPI.class);
if (registration == null) return;
PlexonBackpacksAPI backpacks = registration.getProvider();
```

## Safe surface

The API exposes item identification, backpack/tier/owner identity, immutable `BackpackView` metadata, immutable `TierView` configuration summaries, immutable `BackpackSessionView` session summaries, unique backpack creation and normal player opening.

It does not expose `BackpackRecord`, `BackpackDataStore`, `BackpackHolder`, lock maps or live content arrays.

## Threading

- `create(String)` and `open(Player, ItemStack)` are primary-thread-only and reject asynchronous calls.
- Item/PDC inspection and metadata view methods return snapshots; callers must still respect Bukkit/Paper item-threading rules.
- Persistence and CSV writer operations are internal implementation details.

## Identity and state authority

Backpack UUID/PDC linkage is identity. A current-generation physical reference whose authoritative persisted record is missing is not silently recreated as an empty backpack. Physical display metadata is never a substitute for the authoritative UUID-backed record.

Session ownership is exclusive: one player can hold at most one authoritative backpack session, and one backpack UUID can have at most one authoritative viewer.

## Events

All public lifecycle events are synchronous Bukkit events:

- `PlexonBackpackOpenedEvent` — after validation, lock/session registration and successful inventory open.
- `PlexonBackpackBoundEvent` — once when an unbound backpack gains its owner after the first successful open and that owner change has crossed the required persistence boundary.
- `PlexonBackpackClosedEvent` — after current visible contents have been synchronized, synchronous persistence has succeeded, and the authoritative session/lock has been released.
- `PlexonBackpackUpgradedEvent` — after the authoritative tier/capacity transition has committed successfully.

Each successful open uses one UUID `sessionId`. Rejected opens emit no open/bind event. A close event therefore represents a successfully persisted close boundary, not merely an in-memory dirty mark.

## Failure semantics

Persistence-sensitive operations fail closed:

- failed close persistence keeps the session authoritative rather than releasing a stale lock;
- failed quick-deposit/sort persistence restores the prior in-memory/player-inventory snapshot;
- failed upgrades restore the previous backpack tier/state and refund charged economy funds where applicable;
- force-close refuses ambiguous holder/view/session conditions instead of guessing custody state.

CSV compaction only treats rows as authoritative after their append has completed successfully, so a captured-but-unwritten transaction candidate cannot become durable through compaction ahead of its commit.
