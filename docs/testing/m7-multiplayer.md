# Multiplayer playtest (L7): M7

Run this extension to the [QA-09 playtest](playtest.md) on an accepted M7
candidate. Use one dedicated server and two separate real client connections.
Record the commit, Minecraft and mod profiles, server and client platforms,
map, rules and seat plan. Use stable labels A and B in the report. Both testers
must be able to distinguish their own result from the other tester's observation.

Use the dedicated launch path in [Starting](playtest.md#starting), then open
**Match lobby** from the multiplayer pause menu. Both players join and mark
their seats ready. Start separate lobby rounds when changing the seat plan.
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
The server sorts their login names to assign the first connection to A and the other
to B. Record the name-to-role and socket-to-role mappings locally.

Start one JFR recording for each scene. From the server console, run
`jfr start` followed by `s11-live battle`, `s11-live pan`, `s11-live fog`,
or `s11-live construction`. The dev fixture imports the proving ground, admits
both clients as commanders, drives the corresponding mock workload and records
1,201 JFR boundaries for 1,200 core ticks after 200 ticks of warm-up.
Keep cameras still except in the pan scene. For pan, start at the north ford and
use normal RTS horizontal movement back and forth across the 96-block route,
targeting 40 blocks/s. The report records each eye's actual horizontal travel per
game-second. Record departures from the route or target speed before comparing
that run with the mock pan. Stop the recording with `jfr stop` after the
completion message, and run `s11-live clear` before the next scene.

Export the JFR from JDK 21 and run the local comparison tool. The address
mapping is JSON with exactly the keys A and B and the corresponding opaque
`remoteAddress` values from the export.

```text
jfr print --json --events minecraft.PacketSent,minecraft.NetworkSummary,strategos.S11Tick recording.jfr > events.json
python tools/s11_native.py s11_battle events.json addresses.json s11_battle.json comparison.json
```

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

## Acceptance record

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
