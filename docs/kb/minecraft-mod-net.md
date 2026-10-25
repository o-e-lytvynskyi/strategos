# Minecraft mods: networking (NeoForge 1.21.1)

Your own NeoForge payloads, sending packets to players and how to check this without a client.
The mock player in GameTest — [minecraft-mod-gametest-arena.md](minecraft-mod-gametest-arena.md).
The reference is this repository: `mod/server/net/`, `gametest/net/MockViewer`.

## An exception in a payload codec is an immediate disconnect

If the `StreamCodec` of your own C2S payload throws while reading, NeoForge drops the
connection at once. To count bad packets and disconnect by your own rule (say, on the 101st
violation in a minute), the codec reads softly: whatever is left in the buffer is its bytes;
above the limit — `skipBytes` and a "reject"; broken data — catch it and also return a
"reject", and the handler on the server thread counts it.

## Client S2C handlers cannot be named in common code

21.1 has no separate event for registering client handlers: `playToClient(type, codec, handler)`
is called in common code, which on a dedicated server cannot refer to client classes. A working
workaround: the handler in common code publishes its own event on the game bus
(`NeoForge.EVENT_BUS.post(...)`), and a client class subscribes to it.

## Sending to a player without the channel throws

`PacketDistributor.sendToPlayer` with a mod payload to a player whose client did not declare the
channel (a vanilla client, a connection that was not negotiated) fails with
`UnsupportedOperationException` in `NetworkRegistry.checkPacket`. Check
`player.connection.hasChannel(payload)` before sending.

## A mock connection: `NetworkRegistry.configureMockConnection`

A connection without channel negotiation can neither send our payloads (see above) nor receive
them: receiving disconnects with "No Payload Setup". `NetworkRegistry.configureMockConnection(connection)`
(`@VisibleForTesting`) sets it up as a full NeoForge client with all registered channels; call
it right after `new EmbeddedChannel(connection)`, before `placeNewPlayer`.

## Tick packets lie unflushed until the chunks are sent

At the start of a tick `MinecraftServer#tickChildren` suspends flushing on all player
connections (`suspendFlushing`) and resumes it in "send chunks" — after the levels, after the
GameTest ticker. A test that reads a mock player's `EmbeddedChannel` in its callback sees the
packets of the previous tick. Before reading call `channel.flushOutbound()`, and only while the
channel is open: on a closed one it throws, and an exception in a GameTest step brings down the
whole server.

Your own payloads that the mod accumulates during a tick and sends from `ServerTickEvent.Post`
go out even later — after the GameTest ticker; a flush will not reach them. A test step that
waits for such a packet reads one tick after the event.

## A new `LivingEntity` has dirty health

The `LivingEntity` constructor sets the health, and the change flag of the synced value stays
set: the spawn packet carries the value, and the first tracker update sends it again — a
`SetEntityData` for every new entity. If the body only displays someone else's state, reset it
before `addFreshEntity`: `entity.getEntityData().packDirty()`.

## A player who enters next to standing entities is not paired with them

In 1.21.1 `TrackedEntity#updatePlayer` pairs an entity with a player only if its chunk has
already been sent (`ChunkMap#isChunkTracked`: tracked and not pending). On entry the chunks are
still pending, and sending a chunk (`PlayerChunkSender#sendChunk`) pairs nobody. The next
`ChunkMap#move(player)` pairs them: it is called by the client's position packet, and a real
client sends one at least once a second. A mock player sends no positions and will never see
standing entities, although it receives all the chunks. In a test call
`level.getChunkSource().move(player)` once per tick. An entity that appeared after the entry, or
one that changed section, is paired without it.

## A player teleport after the connection tick gives "moved too quickly"

At the start of every tick `ServerGamePacketListenerImpl#tick` sets `firstGoodX/Y/Z` to the
player's current position, and every movement packet is checked against exactly it. If the
teleport is done after the connection tick (`ServerTickEvent.Post`, a packet handler between
ticks), the client's answer — the confirmation and the first packet with the new position —
arrives in the same pause between ticks and is checked against the old position. The server
writes "moved too quickly" and teleports again, sometimes two or three times in a row. A
teleport in `ServerTickEvent.Pre`, before the connection tick, goes through cleanly. So a jump
command from the client must be queued and executed at the start of the next tick.

The speed limit is in the same place: a packet may travel `10 × √k` blocks from the tick-start
position, where k is the number of packets since the server's last tick. A client that ran two
ticks in one frame (a heavy frame, Iris and Sodium) sends two packets in a row. At 8 blocks per
tick the second goes 16 > 14.1 and is rolled back. 5 blocks per tick survive even four packets
in a row.

## Tracking range follows the player's range, not the server's

`TrackedEntity#updatePlayer` takes `min(type range, getPlayerViewDistance(player) × 16)`, and
that is `clamp(requestedViewDistance, 2, server range)` — what the client sent. A client with a
render distance of 5 sees entities only within 80 blocks, even if the server has 10. Checks of
the "is everything paired within the radius" kind count by `player.requestedViewDistance()`.

## Packet bytes: JFR `minecraft.PacketSent` with your own recording

`PacketEncoder` calls `JvmProfiler.INSTANCE.onPacketSent`, and the event is written if any JFR
recording enabled it (`PacketSentEvent.TYPE.isEnabled()`), not only `/jfr start`. Measure from
code: `new jdk.jfr.Recording()`, `enable("minecraft.PacketSent")`, `start`/`stop`, `dump(path)`,
then `RecordingFile.readAllEvents` — the dump also holds service events, so filter by
`getEventType().getName()`, otherwise `getString("remoteAddress")` throws. Fields: `protocolId`
(`play`), `packetDirection` (`clientbound`), `packetId`, `remoteAddress` (as
`connection.getRemoteAddress().toString()`), `bytes` — the body before compression and framing.
Cross-check in this repository (M2 §1.6 line 38): the mock viewer's encoder counter and the JFR
of a real client at the same point over 60 s differed by 0.006 %. An integrated server has an
in-memory connection and no encoder — you need a dedicated server.

## Entity sound on the server: the `AtPosition` event, not `AtEntity`

`Entity#playSound` (and `LivingEntity#makeSound`: hit, death, splash, fire hiss) calls
`level.playSound(null, x, y, z, …)`. That is a positional `playSeededSound`, and NeoForge posts
`PlayLevelSoundEvent.AtPosition` without an entity. `AtEntity` exists only for
`playSound(player, entity, …)`. A listener for `AtEntity` alone does not see such sounds. The
sound goes to everyone in range, regardless of the fog of war. It is more reliable to silence
before the event: `isSilent()` → `true` aborts `Entity#playSound` at once. The test listens to
both events, and with a pig nearby checks that the listener hears anything at all. Reference —
this repository, `combattests.no_server_sounds`.

## Entity equipment without a server tick does not reach clients by itself

`LivingEntity#detectEquipmentUpdates` (LivingEntity.java:2581) notices a change of hand items
and sends `ClientboundSetEquipmentPacket` only from the server `LivingEntity#tick`. For a
kinematic body whose server `tick()` does not call `super`, a change of `getItemBySlot` never
reaches clients. A new tracker does get the items: pairing (`ServerEntity#sendPairingData`,
ServerEntity.java:274) sends everything non-empty from `getItemBySlot`. Send the change
yourself: `ServerChunkCache#broadcast(entity, new ClientboundSetEquipmentPacket(id,
List.of(Pair.of(slot, stack))))` — the audience is the same as for tracking. The client handler
calls `setItemSlot` (ClientPacketListener.java:1398), so the body's `setItemSlot` must store the
hands on the client too.
