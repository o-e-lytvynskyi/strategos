# ADR-0031: Persist and replicate surviving coalition results

Date: 2026-11-18

## Status

Accepted; T7 cloud functional/replay, complete slow/soak, inspected L4, matched JMH/allocation and all 24 L6 scenes passed (2026-11-18). Evidence: M7 specification T7 acceptance; immutable production source `4dd51afa`.

## Context

Both existing victory rules evaluate defeats but represent only one winning faction.
M7 locked teams require the whole surviving allied coalition, including after save/reload
or a later client admission. A winner chosen inside the defeat loop could incorrectly
announce victory before processing a simultaneous elimination.

## Decision

- Evaluate every defeat first. After a defeat has occurred, finish when no faction remains
  (explicit draw), one faction remains, or all surviving factions are allied in locked teams.
  Never include a defeated ally. Unlocked legacy matches retain individual victory rules.
- Winner sets contain ascending unique faction slots, bounded to 0..15. Empty means draw.
  The existing single-winner accessor remains a compatibility view of the first member.
  End exactly once; an already terminal result cannot be replaced.
- Core schema 6 appends a winner mask to nonempty outcome rows. Migration 5→6 converts
  the old five fields without changing individual/draw state hashes. Multi-winner hashes
  add a tagged extension. Current missing, inconsistent, defeated or out-of-match winner
  metadata fails closed. The outer adapter blob and registry versions do not change.
- Match-end news and terminal state results carry the same bounded set. Single-winner and
  draw wire bytes remain unchanged; zigzag -2 introduces a mandatory bounded coalition mask
  containing at least two members. Malformed extensions fail, never become legacy results.
  Network registration version 2 refuses older peers that cannot understand coalitions.
- Client state reconstructs the terminal banner on admission/reload. The banner recognizes
  every winner. T8 must replace its current identity-seat perspective with the authenticated
  primary faction when admitting co-op seats; production co-op admission remains refused
  until that authority/recipient integration is ready.
- Terminal core state overrides an older PAUSED registry record, remains ENDED and cannot
  resume. Shared final T7 slow/soak and JMH gates cover both combat and outcome changes.

## Consequences and reversal

Old single-winner simulations keep their results and hashes. Old binaries refuse schema 6
and network version 2 instead of silently dropping allied winners. To reverse, revert this
implementation before creating schema-6 saves; already written coalition saves require an
explicit conversion policy and cannot safely be read as single-winner results.
