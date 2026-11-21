# ADR-0029: Version adapter membership and remaining reconnect duration

Date: 2026-11-18

## Status

Accepted for the M7 persistence implementation; T5's cloud acceptance is tracked in the branch record.
Reconnect authentication and expiry actions remain T10.

## Context

Core schema 4 already preserves primary player/faction bindings, teams and grants (ADR-0028).
UUID membership belongs to the adapter. Restoring a UUID must not restore a connection or its
transport queues, and restarting a paused match must not reset a reserved player's remaining
reclaim duration. Wall-clock timestamps can jump; process-local monotonic origins change at restart.

## Decision

- Keep core schema 4 and its hashes unchanged. Advance only `MatchData`'s outer blob from 1 to 2;
  the registry stays at version 1. Old builds refuse blob 2 instead of silently losing metadata.
- Blob 2 requires an explicit roster list, including an empty list. Each UUID row contains its
  player slot and a typed long `remainingNanos`. It never stores a connected flag, an absolute
  clock origin, session credentials, queued deltas or previously projected private frames.
- Blob 2 also requires a typed boolean `launchPending`. The production backend sets it before
  trusted authority/setup is queued and clears it only after all members are admitted and the
  initial replication period completes. A saved pending launch is quarantined on load; it is
  never reconstructed as a complete legacy-identity match. Normal shutdown still cancels an
  in-flight launch before saving. The actual terrain-import case tests interruption before
  setup and after authority installation, then verifies the completed roster can load.
- A live seat saves a full reclaim window. A disconnected seat saves its remaining monotonic
  duration, clamped at zero. Restoring any row creates a disconnected reservation relative to
  the new monotonic clock. Repeated disconnect notifications do not restart the timer.
- Keep the specified five-minute default. Permit an injected monotonic clock and reclaim duration
  for deterministic tests and the future server policy. The storage bound is zero to 24 hours;
  configured live windows must be positive and fit that bound. This is an arithmetic/storage
  limit, not a change to the default player-facing window. A later policy can narrow it.
- Version 1 accepts valid legacy rows or an absent roster and supplies the configured full window
  to each legacy member. Both versions reject malformed list shapes, duplicate UUIDs and
  non-administrator seats without a primary binding in the decoded core authority. Blob 2 also
  rejects missing, wrongly typed, negative or oversized durations. Invalid files retain the existing
  raw-preserving quarantine.
- Before a restored roster enters the membership index, reject UUIDs already held by another
  match. Keep a faulted diagnostic runtime without publishing its untrusted membership or
  reconciling its world state. A valid unrelated roster still restores paused.
- Deadline persistence does not authenticate reconnects, revoke connection tokens, choose an
  expiry action or execute that action. T10 must install those transitions around the existing
  authenticated ingress and apply expiry exactly once, including while the simulation is paused.
  Existing authenticated login/logout events update connection metadata and mark even a paused
  blob dirty; this does not replace T10's authorization or once-only expiry policy.

## Consequences

Legacy saves remain readable without reinterpreting player/faction identity. Current saves cannot
silently fall back to legacy metadata after losing required fields. Remaining durations survive
restart independently of clock origin, including monotonic counter wrap. No simulation dependency
or network protocol is added. Duration round trips, malformed/legacy blobs and actual co-op/grant
restoration require cloud unit and engine evidence before T5 is accepted.

Absolute wall-clock expiry would depend on clock corrections; persisting a monotonic timestamp
would compare unrelated process origins. Restoring a saved live-connection flag would trust an
unauthenticated session. Increasing or removing the storage bound requires the same duration
overflow/boundary tests; reverting the schema requires an explicit compatible migration.
