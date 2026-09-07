# PlexonCore Integration

PlexonBackpacks 1.2.0 supports PlexonCore API `>=1.0 <2.0` and registers module ID `backpacks` when a compatible Core service is available.

## Runtime modes

- `CORE`: PlexonCore is enabled, its API service resolves, the API range is compatible and the module registration belongs to this plugin instance.
- `STANDALONE`: Core is absent, disabled, unavailable or incompatible. Backpack gameplay, persistence and the public Backpack API/events remain available.

## Capabilities

The module publishes capabilities for the backpack engine, persistent backpacks, public Backpack API, open/close/bind events, tiered backpacks, CSV journal storage, custom-head items and anti-nesting.

## Lifecycle

Startup registers `STARTING`, initializes configuration/storage/services/listeners/commands/recipes/API/autosave, then marks the module `READY`. Recoverable reload issues may publish `DEGRADED`; critical startup failures publish `FAILED` before safe disable where possible. Shutdown closes sessions, flushes CSV synchronously, unregisters the Backpack API and finally unregisters the Core module.

## Dependency policy

PlexonCore is compile-only/provided and must never be shaded into `PlexonBackpacks-1.2.0.jar`. `scripts/provision-core.sh` pins the official 1.0.0 release SHA-256 and creates a temporary local Maven repository under `.deps/` for Gradle resolution.
