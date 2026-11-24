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

## "Not saved, changes nothing else" is a replay divergence

Any field a system reads on the next step belongs in the snapshot and the state hash, even a
cooldown whose loss "only costs one retry". A journal is a ring: a long match replays from a
snapshot taken mid-match, so the replay restores and the live run does not, and the retry it
allows moves units by a fraction of a block. M9's `BreachLog` (wall-breach cooldowns) was left
out and the AI soak's seed 13 diverged 20 ticks after its journal start at tick 31200; short L1
replays start at tick 0 and never saw it. For a new per-unit store, add an L1 test that saves
mid-behaviour, round-trips through the codec, restores and steps both runs to the same hash
(`GateMovementTest.aBreachCooldownSurvivesASave…`). To find such a divergence, record each
snapshot component's `toString` at the live checkpoints and compare them in a stepped replay:
the first differing component names the store.
