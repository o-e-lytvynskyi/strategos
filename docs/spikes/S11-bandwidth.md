# S11: multiplayer bandwidth

## Mock measurement

The cloud `s11` performance job runs the four S11 scenes on the proving
ground using four acknowledging QA-19 peers. Two peers share each primary
faction. Each scene waits for 600 units, then warms up for 200 core ticks and
samples 1,200 consecutive core ticks at the existing 20 TPS pace. The report
also records elapsed wall time, moving samples and population extrema.

| Scene | Workload control |
|---|---|
| Moving battle | Existing mixed BattleScene armies, with movement and damage in the measured window |
| Commander pan | The same battle, with all four commanders accepting 2 blocks of travel each tick and streaming actual terrain |
| Fog edge | The same battle under fog, with both visible and hidden enemy units observed at sampled ticks |
| Construction | Existing EconomyScene placement, construction and repair workload with 600 workers and fighters, and accepted placements in the window |

QA-19 re-encodes each unbundled packet through the game protocol before
compression and framing. Reports separate full chunk/light delivery, the
chunk cache controls and the batch boundaries from gameplay. Block changes,
entity tracking and other vanilla gameplay count towards E alongside
Strategos payloads. This is an encoded-packet count before compression.
Framing, compression, bundle and transport overhead are reconciled in T18.

Every peer must pass the existing 102,400 gameplay bytes per game-second
mean. The job retains the per-tick bytes, maxima over every sliding 20-tick
window, per-type bytes and packets, and every scene's validation result.
The report guard checks the four scenes and four distinct recipients per
scene and recomputes the window statistics and channel totals. The native
comparison and tier-4 decision are T18.
