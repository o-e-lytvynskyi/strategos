# ADR-0028: Preserve match authority in snapshots and journaled setup

Date: 2026-10-29

## Status

Accepted for the M7 authority foundation; amends ADR-0018. Runtime command routing, lobby adapters,
recipient privacy and multiplayer acceptance retain their separate task dependencies.

## Context

Player seats and army/economy factions are separate domains. Multiple humans can own the same
faction, and a faction can grant an ally explicit control. Losing those bindings on restore, or
reconstructing them from equal numeric slots, changes authority. The single-writer core must own
the same relations during live play, restore and replay (ADR-0008/0009).

PR #30 supplies schema 3 and stable template references (ADR-0027 in this composition). M7 must
extend that format without changing its conservative legacy template policy or overwriting an
unmerged predecessor's version number.

## Decision

- `DiplomacyState` is an immutable value implementing `DiplomacyView`. Match state replaces the
  value only through its authoritative input path. A captured query/snapshot retains the previous
  value. UUIDs and network connections stay in the adapter's roster, outside core.
- `MatchSnapshot.Play` retains this immutable value directly. The codec serializes a bounded,
  canonical slot representation: 1..16 team entries, at most 16 primary bindings and 256 grants,
  with a team-lock flag. Arrays remain private; malformed, duplicate, enemy, self and absent-seat
  grants are rejected before a value is published.
- Grants are directional and faction-owned. An issuer can grant only its primary faction,
  never authority inherited from another grant. They authorize control, not treasury, queues,
  research or visibility. Binding/team changes revoke stale grants; disconnect is not unbinding.
- Schema 4 requires the diplomacy block. Missing or malformed authority in a schema-4 save is
  an error, never an implicit identity fallback. Versions 1/2/3 migrate through the existing
  chain and receive explicit legacy identity bindings, distinct teams, no grants and unlocked
  teams. The existing schema-1/2 template-index restrictions remain in force.
- Compatibility constructors normalize an older typed snapshot to explicit identity authority
  using its configured faction count. A persisted block's faction count must match the match
  configuration. Older schema-3 readers refuse version 4 rather than silently dropping rights.
- `DiplomacySetup` is a trusted server input, not a client payload. Its validated, canonical,
  locked configuration can be installed once, on tick 1, before other inputs. Late, duplicate
  and wrong-faction-count setups are ignored and logged. Runtime player grant/revoke and command
  authorization remain T8 work; no co-op command acceptance is certified by this foundation.
- The binary journal appends input tag 9; existing tags and command fingerprints do not change.
  The setup has bounded counts and validates its complete authority before use. Format-2 journal
  framing remains valid; an older reader rejects the schema-4 start snapshot or unknown input.
- Non-default authority contributes to the state hash in fixed slot order through an
  allocation-free mixer. Exact legacy identity authority contributes no extra values, preserving
  the hashes of released fixtures/journals. The legacy predicate is cached in the immutable value.

## Consequences

Restore/replay cannot acquire a different player/faction interpretation by losing optional data.
Immutable sharing avoids rebuilding authority at every snapshot construction; the decoder validates
one model and the encoder derives its canonical fields. There is no new runtime dependency.

The change requires current/legacy JSON and NBT tests, malformed-authority tests, input/journal
round trips, hash compatibility checks, slow/soak gates and same-host JMH comparison. Native budget
acceptance and Linux CI evidence are reported separately; no baseline or threshold is promoted.

M6 may extend outcome/statistics persistence. Its delivery must reconcile schema versions and keep
diplomacy when composing `Play`; it must not reuse schema 4 for another interpretation.

## Alternatives considered

Numeric identity fallback for a current save can silently grant different control. Adapter-owned
bindings violate core authority and replay. Mutable shared views can change after capture. Saving
grants without primary bindings or teams loses the context needed to validate them.
