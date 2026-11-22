# Commander body guards

## Exercise the complete connection tick

An EmbeddedChannel installed with `placeNewPlayer` is not an accepted socket in
`ServerConnectionListener`. Its player gets chunk delivery, but its game packet
listener is not automatically ticked. A test of a player-tick event must drive
the real `player.connection.tick()` once per test tick, rather than call the
guard or post a synthetic event. See also [mock-player setup](minecraft-mod-gametest-arena.md).

In Minecraft 1.21.1, `ServerGamePacketListenerImpl.tick` records `firstGood*`, calls
`player.doTick()`, then restores the recorded position. A teleport during
`PlayerTickEvent.Post` changes the body and pending teleport but does not update
that recorded origin. Call the public listener `resetPosition()` after a
corrective teleport, or the rest of the same listener tick can undo it. This is
distinct from the [ordinary queued camera-jump timing](minecraft-mod-net.md).

## A player-tick event can be skipped

`ServerPlayer.doTick` skips `super.tick()` for a spectator touching an unloaded
neighbour chunk. The check uses the body box inflated by one block
(`Entity.touchingUnloadedChunk`). The player's `Post` event lives in the skipped
`Player.tick`, so it alone cannot guarantee a one-server-tick boundary repair.
Keep a bounded server-post backstop. Test the real unloaded-neighbour condition
and skipped full listener tick before requiring repair at the next server tick.

## Player bodies are not core-unit visibility bits

A stationary player can become fogged without changing any core-unit visibility
bit. Compare current player-body permissions independently of the unit index's
version, including already paired subjects. Refresh using the viewer's primary
faction rather than player slot; co-op bindings make those different identities.
