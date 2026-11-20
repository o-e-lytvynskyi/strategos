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

A paused core still has an authoritative current view. Gate snapshot publication
on RUNNING or PAUSED, without stepping or resuming the simulation; otherwise a
full join clears the old view but never receives unchanged initial snapshots.

FX pages are 16-block batching buckets, while vision uses 4-block cells. A
visible event never authorizes another event in the same batch. Project the
individual eighth-block event coordinates before encoding. Shots retain their
existing either-end policy; grants and alliance membership do not confer M8
shared vision. A missing authoritative grid must not authorize hidden effects.

Replay validation consumes a whole non-overlapping command-number range before
submission, including commands the core may refuse semantically. Reject range
overflow with wide arithmetic; permit gaps from failed client sends. Bind the
window to the authenticated live recipient view, reset it on a fresh view, and
prune invalid contexts so disposed matches are not retained by idle players.
The client must share one sequence source across orders and placement: the old
independent placement range would cause every later ordinary order to fail a
global high-water check. Protocol 6 makes that semantic change explicit to old
clients; the binary command layout and saved schema remain unchanged.
