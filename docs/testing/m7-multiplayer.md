# Multiplayer playtest (L7): M7

Run this extension to the [QA-09 playtest](playtest.md) on an accepted M7
candidate. Use one dedicated server and two separate real client connections.
Record the commit, Minecraft and mod profiles, server and client platforms,
map, rules and seat plan. Use stable labels A and B in the report. Both testers
must be able to distinguish their own result from the other tester's observation.

Use the dedicated launch path in [Starting](playtest.md#starting) with a fresh
server world. Set `level-type=strategos\:arena` in `server.properties` before the
first boot. Grant the host operator permissions once that player has joined.
For the LAN check, create a singleplayer world with the **Strategos Arena**
world type before opening it to LAN.

In the multiplayer pause menu, the host opens **Match lobby** and selects
**Create lobby**. The second player opens **Match lobby** and selects **Join lobby**.
Both select **Ready** below the seat table before the host selects **Start match**.
Start separate lobby rounds when changing the seat plan.
Use the same account for the reconnect check. Do the automation-negative
controls through existing evidence, without sending malformed traffic during
this playtest.

| Step | Action and acceptance | Automated evidence |
|---|---|---|
| 1. Start | On dedicated, invite B and start as A with B ready. Both reach RTS mode and see their correct seat, faction colours and roster. Repeat the lobby-entry check on Open to LAN. | Real-client [lobby scenario](https://github.com/o-e-lytvynskyi/strategos/actions/runs/44203471758), [four-player admission and isolation](https://github.com/o-e-lytvynskyi/strategos/actions/runs/44300748747) |
| 2. Opponents | Put A and B on opposing teams. Each changes their treasury or training queue. The other player checks their own panels and then views the opponent under fog. A middle-clicks the minimap and sees their own marker, while B sees neither version. Repeat with roles reversed. | [Recipient matrix](https://github.com/o-e-lytvynskyi/strategos/actions/runs/44746013321), [ping isolation](https://github.com/o-e-lytvynskyi/strategos/actions/runs/45032045463) |
| 3. Allies | Put A and B on distinct allied factions with an opposing AI. Middle-click a minimap location. Both allies identify the same map and world marker and see it expire. Confirm that their economies stay distinct. Defeat the opposing faction and confirm allied victory on both results screens. | [Coalition results](https://github.com/o-e-lytvynskyi/strategos/actions/runs/44296700394), [approved HUD and ping scenario](https://github.com/o-e-lytvynskyi/strategos/actions/runs/45059965221) |
| 4. Grant and co-op | In a fresh allied round use **Share control** to grant, use and revoke a command on the other faction's unit. Check that revocation takes effect and the private panel stays with its primary faction. Then start with both humans in one faction and issue compatible orders from both clients; both can control the shared roster. | [Co-op authority and scoreboard](https://github.com/o-e-lytvynskyi/strategos/actions/runs/44614567652), [co-op UI](https://github.com/o-e-lytvynskyi/strategos/actions/runs/45031953038) |
| 5. Reclaim mid-fight | During an ongoing fight, B saves a control group and camera pose, then disconnects. A stays connected and watches B's existing orders continue. B connects with the same account inside the configured grace period (300 seconds by default). Confirm the same seat, authorised surviving group handles, camera, continuing fight and refreshed private view. | [Reclaim, timeout and transport](https://github.com/o-e-lytvynskyi/strategos/actions/runs/44918199224), [actual camera/group restoration](https://github.com/o-e-lytvynskyi/strategos/actions/runs/44918953137) |
| 6. Commander and UI | Show fog in both views and pan the commander around a map boundary. Confirm body/camera correction and ordinary input recovery after opening chat and pause. In the approved 60-block ART-03 scene identify each silhouette and faction colour. | [Commander compatibility](https://github.com/o-e-lytvynskyi/strategos/actions/runs/44968393924), [actual Sodium movement](https://github.com/o-e-lytvynskyi/strategos/actions/runs/44968610434) and [fog](https://github.com/o-e-lytvynskyi/strategos/actions/runs/44968388209) |

## S11 live capture (T18)

Use an isolated dedicated dev server with dev-only map resources and two real client
connections. An operator starts the processes using the QA-09 launch path with
`-Ps11.live=true` on `:mod:runServer`. Both viewers set render distance to 12.
Use the same Gradle property for the two client launch tasks. This selects
the separate dev game directories under `mod/build/s11-live` and port 25576.
Bind the isolated test server to IPv4 loopback. The server sorts login names
to assign A and B. Record the name-to-role and socket-to-role mappings locally.

Start one JFR recording for each scene. From the server console, run
`jfr start` followed by `s11-live battle`, `s11-live pan`, `s11-live fog`,
or `s11-live construction`. The dev fixture imports the proving ground, admits
both clients as commanders, drives the corresponding mock workload and records
1,201 JFR boundaries for 1,200 core ticks after 200 ticks of warm-up.
The two opt-in clients hold their cameras at the north ford and send ordinary
movement packets across the 96-block route in the pan scene, targeting 40
blocks/s. The report records each eye's actual horizontal travel per
game-second. Record departures from the route or target speed before comparing
that run with the mock pan. Stop the recording with `jfr stop` after the
completion message, and run `s11-live clear` before the next scene.

Export the JFR from JDK 21 and run the local comparison tool. The address
mapping is JSON with exactly the keys A and B and the corresponding opaque
`remoteAddress` values from the export.

```text
jfr print --json --events minecraft.PacketSent,minecraft.NetworkSummary,strategos.S11Tick recording.jfr > events.json
python tools/s11_native.py s11_battle events.json addresses.json perf/s11/s11_battle.json.gz comparison.json
```

For large files, Java 21 can export the same three event types with just
the fields the comparison uses:
`java tools/S11Extract.java recording.jfr > events.json`.

`PacketSent` reports the encoded packet before compression and framing.
`NetworkSummary` is a periodic aggregate from that same encoder, so compare
its totals with the packet events over the whole recording. JFR identifies
custom payloads as a group. QA-19 also identifies their individual Strategos
payload IDs. For the scene window, group each packet by the timestamp between
consecutive output boundaries. Write down scheduling and aggregation differences
that matter to the comparison. Transport bytes would additionally include
compression, framing and overhead. Keep the JFR file, JSON export and address
map out of git. The comparison uses only A and B as peer labels.

After all four scenes, record both clients' gameplay means against 100 KiB/s,
the captured peaks and the evidence supporting the decision on tier 4.

## Recipient and reconnect regression inventory

| Surface | Boundary checked | Engine regression |
|---|---|---|
| Lobby | Connection identity supplies the seat. Private lobby state is restricted to admitted members. Outsiders and cancelled starts leave no partial match or seats. | `LobbyNetworkTests`, `LobbyAdmissionTests`, `LobbyPreparationTests` |
| Economy, queue and unit detail | The primary faction sees its private state, including co-op primaries sharing that faction. Delegation changes issuing authority. Opponents and ordinary spectators have no private view. | `ReplicationTests`, `CoopReplicationTests`, `RecipientAccessTests` |
| Fog, bodies and effects | Visibility gates each entity and effect for that recipient. Stationary cameras still lose sight immediately, including a body's change to commander mode. | `FogTests`, `CommanderPairingTests`, `RecipientFxTests` |
| Public match context and team pings | Match membership bounds the roster and colours. Current team membership bounds ping delivery and lifetime, including co-op and opponents. | `LobbyPreparationTests`, `TeamPingTests` |
| Deferred and per-connection messages | Saturation preserves critical events and other recipients. Revoked or stale contexts cannot recover summaries, queued frames, feedback or detail, even with a reused unit ID. | `RecipientQueueTests`, `RecipientTransferTests`, `RecipientRejoinTests`, `RecipientFxTests` |
| Rejoin, save and observer transition | Reclaim renews private snapshots and sequence windows. Old transport events cannot revoke the new seat. An expired owner cannot reclaim, and a live co-op peer retains command. | `ReconnectLifecycleTests`, `ReconnectRestoreTests`, `RecipientRejoinTests`, `RecipientTransferTests` |
| Commander powers | RTS cameras cannot inherit vanilla spectate or attack powers. Ordinary spectator behaviour and the explicitly admitted observer path have their own checks. | `CommanderPowerTests`, `CommanderBoundsTests`, `RecipientAccessTests` |

Each row uses the actual server adapter and recipient connections. The
compatibility Verify jobs rerun this matrix, and the client scenarios
exercise the lobby and RTS screens under a real client.

## Live coverage gap

The shipped lobby, map and scenario each support two faction slots. Step 3
needs at least three factions. Keep it open until a production route supports
two human allies and an opposing AI. The owner deferred the content expansion
on 2026-11-25. The automated coalition and ping evidence remains recorded above.

## Acceptance record

On 2026-11-25 the owner confirmed that both clients started the match on
Dedicated Strategos Arena without problems and that the reconnect worked. The
server recorded a 219-second disconnect-to-login interval for player B within
the default 300-second grace period. Camera pose, group, continuing orders and
the private-view refresh were not recorded separately.

The opponent check reported visible building blocks during construction and
no other issue. Visibility of physical building blocks above shroud is the
existing FOW-06 limitation. The owner reported that the RTS controls worked
and that pause worked by command. The dedicated server limits match pause to
operator commands under the current MATCH-05 policy. F9 is for the owner of
an unpublished singleplayer world.

In a fresh dedicated arena the owner confirmed that both clients could issue
orders for the same faction. Grant and revoke between separate factions were
not recorded individually. LAN entry and the allied victory round remain open.

Copy the following fields once per session. Add observations from both
clients and a link to evidence for every failed or ambiguous row. Escalate P0
and P1 findings using the [severity and desync procedure](playtest.md#bug-severity).

| Field | Record |
|---|---|
| Candidate, dates, profiles and machine class | |
| Seat plan, match settings, grace seconds and launch route | |
| Steps 1–6 and per-client observations | |
| Reclaim timing, retained order/group/pose and private-view result | |
| S11 scene windows, per-peer measurements and discrepancies | |
| ART-03 silhouette and appearance verdict | |
| Findings, severity and resulting decision | |
| Owner's final acceptance and date | |
