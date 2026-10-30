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
