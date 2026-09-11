# PlexonBackpacks 1.2.1

PlexonCore 2 lifecycle and production-target compatibility patch.

## Fixed
- Expands the supported Core API range to `>=1.0 <3.0`, resolving successful startup being stranded in `STARTING` under PlexonCore 2.
- Uses owner-aware Core 2 state transitions and unregister semantics, with Core 1 fallback retained.
- Pins builds to PlexonCore 2.0.4 with verified SHA-256 `61d625a717da9f46ee9231e1970d84b4c317ae12cf4090cdf7c9d39b6a1a9baf`.
- Normalizes the Paper API target from the older `26.2.build.65-beta` to production `26.2.build.121-stable`.

Backpack persistence, GUI behavior, ownership, anti-nesting, item identity and storage semantics are otherwise unchanged.