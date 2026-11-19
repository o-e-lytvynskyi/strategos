# Recipient boundaries and full joins

A UUID identifies an account, not the origin of a queued response. Capture the
actual server player and connection, runtime, seat, primary faction and live
recipient generation when producing a frame. Revalidate before sending it.
An asynchronous result must retain its submission origin: resolving the UUID
only when the callback runs silently rebinds an old result to a later seat.

Use a per-player generation. A global roster revision invalidates the first
participant's valid initial frames when another participant joins. Full joins
also start a new view, even when the UUID, match id, seat and primary are equal;
renew only the live transport generation, not membership or reservation time.
Never serialize this generation as authoritative game state.

Reset recipient-specific replication memories and query subscriptions before
producing for the new view. Unchanged treasury and empty building/deposit tables
still need initial snapshots after a full join. A faction-wide vision delta
tracker may already be primed by another co-op primary: the new participant
needs a full bitmap, not an unchanged empty delta. Re-prime only the primary
faction's tracker; control grants do not confer its vision.

On the client, clear private model/state/debug views on every full join.
Matching only the match id retains stale private fields and may reject a fresh
snapshot as older than the previous view. Preserve separate camera/group and
reconnect-policy responsibilities rather than inventing them in replication.

Minecraft 1.21.1 Entity.equals compares numeric entity ids. Recipient identity
requires the exact ServerPlayer object and its ServerGamePacketListenerImpl,
not that value equality. Document the intentional identity comparison locally.

Tests must establish the old view, change context, and require a fresh positive
frame and an unaffected peer's delivery before asserting absence of old data.
The queue, delayed-result and full-join engine contracts exercise separate
boundaries; the client DTO-replay scenario is not a real reconnection test.
