# ADR-0026: Persist template identities independently of session indices

Date: 2026-10-29

## Status

Accepted; amends ADR-0018 (save identities) and ADR-0012 (reload reference validation).

## Context

Buildings and production queues persist numeric template indices. A session's append-only
network table is not stable across sessions or releases. A content hash describes flattened
content by id, not index assignment. Even the current built-in table cannot reconstruct an
old one: early M3 used index 5 for the house, later M3 used it for the field, and M4/M5
added builtins where older sessions could have assigned custom templates.

## Decision

- Schema 3 writes `templateBindings` (saved index to stable string id) alongside the numeric
  building/queue layout. Resolve every live building and queued product by id and kind before
  footprint, stats or production reads. Missing, duplicate or conflicting bindings fail the
  load. A schema-3 snapshot with live buildings and no bindings is a decode error.
- Schema-1/2 migration supplies only the common reserved prefix of every table since numeric
  building persistence first shipped: indices 0..4 (debug walker, infantry, worker, barracks,
  civic centre). All other live building/queue indices are ambiguous and fail explicitly,
  including later builtins. Never grow this migration table when adding new builtins. Persist
  these conservative bindings during migration so re-saving cannot reinterpret them later.
- The original blob is preserved by the existing fault/quarantine path. Recovery of an ambiguous
  legacy reference needs its original table and an explicit conversion; neither equal content
  hashes nor the current table justify guessing. Schema-3 saves from before M5 keep their
  recorded bindings and remain resolvable. Released fixtures are unchanged.
- One guard checks units, buildings, every queued product and template-backed resource nodes
  before server publication and core INGEST. Active and paused matches must retain each used
  id, index and kind. ENDED/FAULTED/DISPOSED matches keep their frozen set and do not block reloads
  or receive further swaps.
- Revalidate a queued swap at the adapter's tick boundary. If references changed since publication,
  throw through the match fault barrier before advancing simulation. A running match must not
  silently retain the old set after the server has published the new one. Normal reload refusals
  preserve the server set/table and match state.
- Restore preserves a terminal registry lifecycle or saved victory outcome; it never revives a
  completed match as PAUSED.

## Consequences

- New custom-template saves survive session-table changes, and older readers reject schema 3.
- Legacy saves with buildings other than barracks/civic centres, or queue indices above 4,
  may require explicit recovery even when their original templates were builtins. This is a
  deliberate compatibility limit: their identity cannot be established from the saved fields.
- Migration bindings are fixed historical data, not a runtime lookup into `TemplateSet.builtin()`.
- Terminal matches remain queryable with their original content. A boundary race stops only its
  affected match and leaves its fault report for diagnosis.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Infer legacy ids from today's built-in table or a content hash | Can silently load another template with the same kind |
| Treat empty schema-3 bindings as legacy | Malformed new saves bypass identity validation |
| Reject every legacy save | Units/nodes use ids already; the common reserved prefix is recoverable |
| Warn and let a published swap be refused in core | Leaves a running match and the server on different sets |
