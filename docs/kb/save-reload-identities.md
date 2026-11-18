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

[ADR-0027](../adr/0027-save-template-identities.md) limits schema-1/2 migration to the common
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

## Validate persisted membership before publishing its index

A UUID occurs once in a saved roster, even if duplicate entries name the same seat. Do
not use normal live reseating to decode duplicates: it silently replaces the first row.
Distinguish an absent legacy roster from a present value with the wrong NBT type;
`getList(key, TAG_COMPOUND)` can turn an incompatible list into an empty one. Validate
the original tag and element type before decoding. Each non-administrator player seat
must have a primary binding in the decoded snapshot authority; a numeric slot alone
does not establish one. Invalid blobs retain the ordinary raw-preserving quarantine.

Check cross-match UUID conflicts before restoring the core or reconciling world state.
Retain a refused restore as a faulted diagnostic runtime, but never publish its roster
into the server membership index. Otherwise even the fault path can replace another
match's valid UUID mapping. An unrelated saved roster must still restore normally.

## Save remaining time, and keep incomplete launches distinguishable

Store a disconnected reservation's remaining monotonic duration, not `nanoTime()`'s
process-local origin or a wall-clock timestamp. On load, anchor that duration to the new
clock and keep the connection disconnected. Subtract elapsed time so counter wrap is
handled correctly; repeated disconnect notifications must not refill the budget. Login
and logout must mark a paused blob dirty too. Connection metadata does not replace
authenticated rejoin, session invalidation or the once-only expiry action.

A core snapshot at tick zero does not contain its adapter's queued diplomacy setup.
Persist a launch-pending marker before queuing setup and keep it until complete admission
and the initial replication period. Refuse an interrupted saved launch without rewriting
its raw tag; never infer completed identity authority merely because the snapshot itself
decodes. Normal shutdown cancellation and crash-time detection cover different cases.

## Compare completed ticks, and preserve the full terminal coalition

A snapshot represents a completed simulation tick. A test that removes an entity directly
between ticks can leave vision/hysteresis derived from the previous tick; comparing that
mutated live state's hash with a restored snapshot tests an invalid boundary. Queue the
intended death through ExternalDamage and step normally before snapshotting. Keep the exact
hash comparison and compare identical continuation inputs; do not drop the assertion.

Terminal results are also state. Persist the full ordered surviving winner set, not only
its first faction. Validate the mask against held/defeated factions, match bounds and locked
allied relations. A newer terminal blob must override an older paused registry record and
refuse resume. Rejoining clients reconstruct the banner from terminal state instead of
requiring an old one-time end notification. See ADR-0031.
