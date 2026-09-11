# PlexonBackpacks 2.0.0

Stable GitHub repository closure of the accepted PlexonBackpacks Phase 2 storage/custody architecture and Phase 3 player UX line, plus two evidence-backed reliability fixes from the final source audit.

## Stable product boundary

- Stable UUID/PDC identity remains authoritative; names, lore, GUI titles and slots are not identity.
- Exact Paper `ItemStack` byte serialization preserves metadata, components and foreign PDC.
- One authoritative session is enforced per player and per backpack UUID.
- Session close persists visible contents before releasing the authoritative lock.
- Cursor custody, active-reference movement protection and anti-nesting rules remain intact.
- Quick deposit and sorting retain snapshot/rollback behavior on persistence failure.
- Tier upgrades retain deterministic capacity, persisted state update, item refresh and economy refund on failure.
- Database/persistence schema remains `2`; pre-2.0 adoption creates a mandatory safety backup.
- PlexonCore 2.0.4 and Vault remain external/provided integrations and are not shaded.

## Final stability fixes

### Compaction commit boundary

CSV compaction previously consumed `latestRows`, but candidate rows entered that map when they were captured rather than after a successful append. Under a narrow overlap between an older asynchronous writer and a synchronous transactional save, compaction could therefore expose a newer candidate before `saveSync()` had committed.

Stable 2.0 advances compaction authority only after a row completes a successful append. Captured, queued and active-but-unwritten rows are never eligible for compaction. Regression coverage forces the pre-commit ordering and proves disk remains on the last committed row until the candidate write succeeds.

### Failed reload rollback

A failed configuration reload could leave dynamic settings from the rejected file active through Bukkit's live configuration object while the last-good tier map remained in memory.

Stable 2.0 treats reload plus runtime application as one boundary. On failure, the last-known-good in-memory configuration is restored and admin menu, recipes and autosave scheduling are rebuilt from it. The externally edited candidate file remains on disk for administrator correction. Regression coverage proves dynamic runtime settings and tier definitions remain last-good after rejection and that a corrected file can reload normally afterward.

## Compatibility and provenance

- Paper `26.2.build.121-stable`
- Java 25 / class major 69
- PlexonCore 2.0.4, checksum `61d625a717da9f46ee9231e1970d84b4c317ae12cf4090cdf7c9d39b6a1a9baf`
- accepted Phase 3 source: `594c773e4cc4a696ce790a61b70dc8074f5f0c05`
- accepted final RC2 lineage: `fdc88427bbfbaa5a517a0847def6e9617120695f`
- historical RC2 JAR SHA-256: `fd804063ca04aef34be7cc30d5e1672b65bb02b0755477cdbabb8bcc1f0cbc3c`
- stable rollback: `v1.2.1` at `488faf508d0708e60b2caab64a97a7e63b06a7c8`
- rollback JAR SHA-256: `1a0eb1f3a60ac3c1b1a213281876a0026124f6d54190d158c77134b4c2cec018`

## Stable publication verification

Stable publication runs only from `release/stable` when it points to exact current `main`. It rebuilds and retests exact source, verifies the installable JAR and Java bytecode, publishes the JAR plus checksum/test/provenance evidence, downloads all public release assets, and verifies their checksum and exact-source/lineage invariants before the Release workflow can succeed.

Live PlexonCraft migration, custody, restart, cross-plugin, MSPT and soak validation remains a deployment follow-up. Stable GitHub provenance may record `runtime_certification=NOT_EXECUTED`; live evidence is never inferred from CI.
