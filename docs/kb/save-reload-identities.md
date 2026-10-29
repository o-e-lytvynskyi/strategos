# Save/reload identities and terminal lifecycle

## A content hash does not identify a session table

`TemplateSet` hashes flattened content by stable id; `TemplateIdTable` assigns network indices
append-only within a session. Fresh sessions can give the same custom id different indices
when another id is inserted earlier. A persisted building or production queue must carry
stable identity alongside numeric indices and resolve before footprint/stat/production reads.
A content-hash match is insufficient evidence for recovering a missing old table.

The built-in table is seeded before custom ids, but grows across releases. Early M3 assigned
index 5 to the house (47d7cbf, 1a2f675); adding the field shifted that identity. M4/M5 then
appended builtins where older custom ids could reside. Never resolve legacy indices through
`TemplateSet.builtin()` or accept a content-hash match as proof of index assignment.

[ADR-0026](../adr/0026-save-template-identities.md) limits schema-1/2 migration to the common
reserved prefix 0..4 (debug walker, infantry, worker, barracks, civic centre). Later live
building/queue references fail explicitly, even if they could have been builtins. This loses
some legacy compatibility deliberately; recovery requires the original session table and an
explicit conversion. The fault/quarantine path preserves the original blob. Migration supplies
fixed bindings so re-saving cannot bypass the guard. Do not extend that historical table when
adding new builtins. Schema 3 requires bindings for live buildings; no empty-list legacy fallback.
Older readers refuse schema 3. Released fixtures and existing schema-3 bindings remain unchanged.

## Validate every reference before publishing a reload

Checking only live units misses buildings, queued products that have not spawned, and resource
nodes whose stats still use templates. Use the same guard on the server before publishing the
new set/table and in core INGEST before swapping stats. Keep ids, indices and kinds intact for
all in-use references, including paused matches. A refusal must leave queries usable.
ENDED/FAULTED/DISPOSED matches retain their original set; exclude them from publication guards
and queued swaps. Revalidate queued swaps at the adapter tick boundary: if new references make
the published set invalid, throw into the match fault barrier before advancing simulation,
rather than leaving a running match silently diverged from the server.

## A completed match is terminal after load too

Restoring every healthy blob as PAUSED revives ENDED matches. Preserve an ENDED record and a
terminal saved victory outcome even when the registry is older. Such a match must neither
resume nor accept commands; active matches still restore PAUSED. FAULTED and quarantined saves
retain their existing fault paths.
