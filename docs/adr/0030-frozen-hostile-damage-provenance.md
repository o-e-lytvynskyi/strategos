# ADR-0030: Freeze hostile damage provenance for locked-team combat

Date: 2026-11-18

## Status

Accepted; T7 cloud functional/replay, complete slow/soak, inspected L4, matched JMH/allocation and all 24 L6 scenes passed (2026-11-18). Evidence: M7 specification T7 acceptance; immutable production source `4dd51afa`.

## Context

M5 permits projectile impact after the shooter dies. Handles include generations and freed
indices are reused; a dead source's faction is absent from a later live-unit snapshot. Looking
up the source at impact either loses valid shots or attributes them to a replacement. Final
damage must use a frozen source faction and the current target relations. Structural deaths
and world damage also use the damage queue and must retain their non-hostile meaning.

## Decision

- Add a bounded source-faction value to frozen `Damage`. Unknown/world provenance remains the
  compatibility default. Capture an actual live unit/building owner when a hostile hit is queued,
  and always at projectile release. Retargeting preserves this frozen value after source death
  or index reuse; it never reinterprets the source handle at impact.
- Every production M7 lobby installs locked teams before admission. Apply the allied filter at
  final unit/building damage in this mode, using current relations rather than a cached ally bit.
  Own and allied hostile hits cause no damage or retaliation. Grants are not team membership.
  An unresolved non-world hostile source fails closed. Explicit world damage and unattributed
  trusted structural lethal consequences retain their established meaning.
- Retain pre-M7 unlocked combat behavior, including its frozen collateral rules. Do not rewrite
  released M5/M6 fixtures or journals to make a new multiplayer rule pass. Locking is a trusted
  first-tick setup; clients cannot opt out of it for an M7 lobby match.
- Advance core snapshots from schema 4 to 5; adapter blob 2 and registry 1 do not change. A
  current locked-team shot with a non-world source requires its explicit source faction.
  Source faction participates in the hash; the unknown compatibility value contributes no
  new word, preserving legacy unlocked hashes.
- Migration 4 to 5 preserves unlocked legacy shots. For an old locked-team shot, recover its
  owner only from a matching full live-unit handle or live building generation in the saved
  snapshot. A dead/unrecoverable source is an explicit migration error, never a guessed owner
  or a dropped shot. Current data cannot use this legacy inference to repair a missing field.
- Extend trusted external damage with the same bounded source faction. The adapter derives it
  from server-owned unit identity or an authenticated same-match player's primary binding,
  never a client field, display name, scoreboard team or control grant. Existing environment
  sources remain unaffiliated. Preserve journal tag 6 byte-for-byte for legacy/unattributed
  damage; append tag 10 for attributed damage. Old readers reject the new schema/tag.
- Coalition victory/outcome representation is T7.2 and receives its own versioned change.
  Source provenance does not certify AI targeting, command ownership, recipient privacy or
  commander hardening; those retain their named tasks and gates.

## Consequences

Delayed/collateral hits remain attributable across death, slot reuse and save/restart. The
last authoritative damage path protects allies without dropping valid hostile hits. Tests
must include both friendly rejection and hostile/environmental/consequence positive controls,
unit and building shooters/targets, changed relations, malformed/current/legacy snapshots,
binary input round trips and replay/hash continuity. Kernel/hasher changes require the
unchanged slow/soak and matched JMH/allocation gates. No threshold or fixture is waived.

The conservative migration can refuse an unfinished M7 schema-4 locked match with an
unrecoverable airborne source. Released unlocked saves retain their behavior and hashes.
Removing the compatibility boundary would require an explicit legacy behavior/migration
decision and evidence; changing a handle lookup cannot supply missing provenance.
