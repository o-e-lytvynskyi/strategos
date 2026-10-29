# Diplomacy authority across restore and replay

- A player slot is not a faction slot. Persist the primary mapping and direct faction grants with teams and their lock; grants do not imply private economy or vision access.
- A current schema-4 save without diplomacy is malformed. Apply identity fallback only through the schema-1/2/3 migration or explicit compatibility construction, never after losing fields from a current persisted record.
- Keep the immutable value in `Play` rather than reconstructing per-seat persistent copies during every snapshot construction. The codec validates one bounded model; the writer derives canonical slot fields.
- New live `Play` writers must retain the existing diplomacy value. Seven-argument compatibility constructors represent older typed snapshots and normalize to identity; they must not be used to replace fields of a live non-identity snapshot.
- Exact legacy identity deliberately adds no new state-hash words. Check released fixtures/journals and non-identity primitive hash parity before changing this predicate.
- Trusted first-tick setup is not a client command. Reconnect does not unbind a seat. Runtime player grant/revoke and ownership validation are separate T8 work.
- Coordinate the M6 outcome/statistics schema with ADR-0028; do not reuse version 4 for a different interpretation. The source marker on `Play` tracks that integration.
