# PlexonBackpacks 2.0.0-rc.2

Phase 3 player-UX release candidate built from accepted Phase 3 source `594c773e4cc4a696ce790a61b70dc8074f5f0c05`.

## Publication status

**PHASE 3 RC / RUNTIME CERTIFICATION NOT_EXECUTED**

This prerelease contains the accepted Phase 3 player-facing UX work on top of the frozen Phase 2 storage, session, exact ItemStack, cursor-custody, persistence, and upgrade transaction authorities. No stable promotion, production installation, runtime certification, or main-branch merge is part of this publication.

## Frozen product boundaries

- Exact ItemStack serialization and persisted backpack storage remain authoritative.
- Session, cursor custody, nesting controls, persistence, and tier-upgrade transaction/refund behavior remain unchanged from the accepted source.
- PlexonCore and Vault remain external runtime dependencies and are not shaded into the production JAR.
- Paper target remains 26.2 and Java target remains 25 / class major 69.
- Database/schema compatibility is unchanged by this release-only descendant.

## Provenance

Accepted Phase 3 ancestor: `594c773e4cc4a696ce790a61b70dc8074f5f0c05`.
The release-only descendant changes version/provenance/workflow metadata only. Runtime certification remains `NOT_EXECUTED`.
