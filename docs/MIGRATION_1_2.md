# Migration to PlexonBackpacks 1.2.0

## Baseline

1.2.0 is based on `1.1.0-Release`, not the repository's historical `1.0.0-Beta` default branch.

## Preserved data contract

No CSV schema migration is performed. The following remain unchanged:

- `plugins/PlexonBackpacks/backpacks-data.csv`
- CSV columns `id,tier,owner,created_at_ms,last_access_ms,contents_base64`
- PDC keys `backpack_id`, `tier`, `owner`
- stable physical backpack UUIDs
- stored owners, tier IDs and contents
- legacy YAML migration support
- append-only dirty-write/coalescing/compaction behavior

## Upgrade

1. Stop the server.
2. Back up `plugins/PlexonBackpacks/`.
3. Replace `PlexonBackpacks-1.1.0.jar` with `PlexonBackpacks-1.2.0.jar`.
4. Keep `config.yml` and `backpacks-data.csv` unchanged.
5. Start the server with PlexonCore 1.0.0 installed when CORE mode is desired.
6. Run `/plexon modules`, `/plexon diagnostics`, `/backpack diagnostics`, `/backpack tiers` and `/backpack inspect`.
7. Open an existing 1.1.0 backpack and verify the same UUID, tier, owner, contents and size before and after restart.

## Required live checks before production promotion

- First-open binding updates PDC and record owner once.
- Wrong-owner and locked opens emit no open/bind event.
- Cursor, shift-click, number-key, offhand and drag nesting all fail safely.
- Two players cannot open one backpack UUID simultaneously.
- Craft/give operations produce distinct UUIDs.
- Reload and quit close active sessions without content loss.
- Core-absent startup remains fully functional.
- At least two restarts preserve records and contents without duplicate tasks/listeners/API registrations.

## Rollback

Stop the server and restore `PlexonBackpacks-1.1.0.jar`. Restore a data backup only if the CSV itself was externally damaged; 1.2.0 intentionally keeps the 1.1.0 storage format rollback-compatible.
