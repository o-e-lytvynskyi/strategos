# ADR-0032: Durable public faction presentation

- **Status:** Proposed; implementation and cloud acceptance pending (M7 T8.4a).
- **Date:** 2026-11-19

## Context

Lobby civilisation and colour choices must survive match restoration. Player identity,
primary faction, delegated command rights and visual presentation have different meanings.
Neither a numeric player slot nor a transient client colour table can represent all four.
Released saves and deterministic fixtures must retain their previous appearance and hashes.

## Decision

Store an immutable public `FactionPresentation` in the core, in faction order, with one
bounded resource-id civilisation and colour per faction. Explicit colours use the lobby's
0..15 palette; -1 means the released appearance. A placeholder civilisation with colour -1
for every faction is the explicit legacy value. No human names, UUIDs, private economy or
authority data enter this value.

A trusted `PresentationSetup` installs the value once at tick 1, before commands. Wrong-size,
duplicate and late setups are ignored without replacing the first valid value. Append-only
journal tag 11 carries it; tags 1..10 retain their meanings, including damage provenance.
A cached canonical presentation hash contributes only for non-legacy presentation.

Core schema 7 requires `play.presentation`. Migration 6→7 supplies explicit legacy values
from the saved faction count. Current saves missing presentation, malformed identifiers,
invalid palette indices and inconsistent faction counts fail rather than silently resetting
chosen colours. Compatibility constructors normalize older typed snapshots to legacy values.
Snapshot copies that replace diplomacy retain presentation.

## Consequences

Adapters can publish the same public presentation after an initial join or reconnect and
apply a consistent palette. Human names and scoreboard membership remain adapter concerns;
units never become scoreboard team members. T8.4b installs those presentation paths and
T8.4c supplies named grant controls. This ADR chooses no civilisation theme or art asset.

Cloud acceptance must cover bounded codecs, first-tick setup, exact legacy hashes, schema
migration/refusal, binary journal replay, and identical post-restore continuation, plus the
existing complete verification suite. Acceptance is not claimed by this proposed ADR.
