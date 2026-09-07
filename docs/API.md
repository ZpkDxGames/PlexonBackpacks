# PlexonBackpacks Public API

PlexonBackpacks 1.2.0 registers `com.zpkdxgames.plexonbackpacks.api.PlexonBackpacksAPI` through Bukkit `ServicesManager` in both CORE and STANDALONE modes.

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

## Events

All three public events are synchronous Bukkit events:

- `PlexonBackpackOpenedEvent` — after validation, lock/session registration and successful inventory open.
- `PlexonBackpackBoundEvent` — once when an unbound backpack gains its owner after the first successful open.
- `PlexonBackpackClosedEvent` — after the session and lock are released and current contents are committed to the in-memory record.

Each successful open uses one UUID `sessionId`. Event IDs are `sessionId + :open`, `:bind` or `:close`. Rejected opens emit no open/bind event.

A close event means memory state was committed and marked dirty; it does not promise that the asynchronous CSV writer has already flushed that row to disk.
