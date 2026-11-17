# Lobby readiness is not a match start

MatchLobby is an immutable pre-match aggregate. Its seats, UUID membership and ready set are
detached values; it performs no network authentication, asset lookup, world preparation or
match creation. LobbyService must derive the sender from the connection and validate its
membership/host/operator authority before invoking it. A UUID supplied by a payload is not
an authenticated identity.

Keep two revisions: state revision advances on ready toggles too; configuration revision
advances on actual settings changes and failed-preparation rollback. Ready intents name the
configuration revision. Otherwise a queued old acknowledgement can approve new settings,
or using the state revision can unnecessarily reject peers confirming the same settings.
No-op updates retain both revisions/readiness; real configuration changes clear every ready
acknowledgement. Preparation freezes edits, and rollback requires fresh acknowledgements.

One faction has one team, civilisation and colour even when several player seats control it.
An identifier passing syntax/length validation does not prove that its asset exists. The
service must validate the actual M6 setup/map and supported assets before freezing setup
atomically with the lobby value. allHumansReady is only a prerequisite; it does not prove
opponents, valid map starts, network admission or command/privacy integration. Rolling back
the aggregate does not dispose a partial match in the world. Those are T3b integration gates.

## Runtime admission must wait for installed authority

A player slot is a network/command identity; a faction is an economy and simulation owner.
Comparing a player slot number with the faction count rejects valid co-op seats (four players,
two factions). Validate the installed primary binding instead, while preserving the existing
administrator behavior and legacy rejection of unbound slots.

Queued first-tick authority is not installed authority. Refuse admission during that queue so
no initial recipient snapshot observes the legacy identity mapping. Install the trusted setup
through the normal journaled tick input before admitting any member. This transient adapter
queue is not persistence: production preparation must cancel on server stop until the launch
transaction is covered by save/restore tests.

## Prepare factions once, not once per co-op member

Build initial scenario commands by economy faction, then bind each player seat separately.
Iterating human seats to grant/spawn starting assets duplicates a co-op faction's economy.
Use the actual scenario command builder before allocation, reject unsupported controller/asset
configurations, and freeze the template-set version for the preparation backend to check again.

## Initial empty tables are snapshots

Do not suppress the first building/deposit frame merely because its table is empty. A valid
four-player worker-only setup has no buildings; waiting for its initial building snapshot
then never completes. Send the first projected table, cache it and suppress unchanged repeats.
Apply the same recipient audience to empty frames; they must not become server-wide notices.

## Inspect map metadata without decoding all tiles

Lobby validation and arena bounds need the manifest. Full `MapPackageLoader.load` also
inflates/parses every NBT tile and resolves block states; repeating it before creation adds
avoidable synchronous start work. Manifest-only inspection is not full content validation:
actual creation still loads every tile before allocating a runtime, and admission still checks
starts, template version and imported terrain hash. Keep world-export precedence, reject a
malformed export rather than falling back, and never substitute resource tiles for an
incomplete export. A short-lived metadata read needs no cache/invalidation policy.
