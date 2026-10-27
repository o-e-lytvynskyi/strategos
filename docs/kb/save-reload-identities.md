# Save/reload identities and terminal lifecycle

## A content hash does not identify a session table

`TemplateSet` hashes flattened content by stable id; `TemplateIdTable` assigns network indices
append-only within a session. Fresh sessions can give the same custom id different indices
when another id is inserted earlier. A persisted building or production queue must carry
stable identity alongside numeric indices and resolve before footprint/stat/production reads.
A content-hash match is insufficient evidence for recovering a missing old table.

The built-in table is seeded before custom ids on the server. Legacy built-in references are
recoverable through that fixed table; a legacy custom numeric reference is ambiguous and must
fault explicitly, preserving the loaded blob for recovery with its original session table.
Schema 3 makes older builds refuse new saves. Versions 1 and 2 migrate without inventing
missing tables, and the same legacy guard applies. Released fixtures remain unchanged.

## Validate every reference before publishing a reload

Checking only live units misses buildings, queued products that have not spawned, and resource
nodes whose stats still use templates. Use the same guard on the server before publishing the
new set/table and in core INGEST before swapping stats. Keep ids, indices and kinds intact for
all in-use references, including paused matches. A refusal must leave queries usable.

## A completed match is terminal after load too

Restoring every healthy blob as PAUSED revives ENDED matches. Preserve an ENDED record and a
terminal saved victory outcome even when the registry is older. Such a match must neither
resume nor accept commands; active matches still restore PAUSED. FAULTED and quarantined saves
retain their existing fault paths.
