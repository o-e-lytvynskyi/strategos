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

### Four-peer result

[Cloud run 45062182584](https://github.com/o-e-lytvynskyi/strategos/actions/runs/45062182584)
measured commit `1267fbce`. Artifact 14770610886 contains every per-tick and
per-type count, the GameTest evidence and the independently recomputed report.
The four complete raw mock reports and their summary are retained in
[`perf/s11`](../../perf/s11) for the later native comparison.
The SHA-256 of `s11_complete.json` is
`4c4c9466659c7e1a8b7001c2ce15b4b49ca0756ed39722dd505aa1bf9ae82a92`.

| Scene | Units | Gameplay mean A/B (B/s) | Largest rolling second (B) | Largest tick (B) | Terrain per peer (B) |
|---|---:|---:|---:|---:|---:|
| Battle | 531–600 | 14,249.62 / 14,275.90 | 15,894 | 4,492 | 513,044 |
| Pan | 531–600 | 34,313.78 / 34,341.80 | 51,947 | 7,976 | 12,649,729 |
| Fog edge | 531–600 | 29,727.10 / 29,921.72 | 38,041 | 7,403 | 0 |
| Construction | 461–600 | 11,172.57 / 11,101.02 | 12,314 | 5,117 | 513,029 |

The second co-op viewer of each faction had the same mean and peaks as its
first. Wall durations ranged from 60.17 to 60.60 seconds. All 60 activity
samples per scene included movement. The fog scene had visible and hidden
enemy units in each sample. Construction accepted five placements during its
window. Every pan peer accepted all 1,200 two-block moves.

All four peers are within E in every mock scene. The highest mean was
34,341.80 B/s against 102,400 B/s.


### Dedicated server and two real clients

On 2026-11-23 the owner authorised the native session on Windows 10 (19045)
with an Intel Core i5-9400F, six logical CPUs, and Java 21. Two separate
Minecraft 1.21.1 NeoForge clients joined the development dedicated server over
IPv4 loopback at view distance 12. The remote-address mapping, exported packet
events and raw recordings remain local. The four
[normalised reports](../../perf/s11/native) retain all 1,200 gameplay and
terrain samples per client, packet counts, the exact reference comparisons
and the per-connection summaries.

| Scene | Wall seconds | Gameplay mean A/B (B/s) | Peak rolling second A/B (B) | Peak tick A/B (B) | Terrain A/B (B) |
|---|---:|---:|---:|---:|---:|
| Battle | 56.95 | 58,238.27 / 58,260.58 | 69,360 / 69,476 | 12,503 / 12,552 | 182,610 / 182,610 |
| Pan | 60.08 | 62,567.80 / 62,639.93 | 73,684 / 73,861 | 11,085 / 11,223 | 36,378,412 / 36,702,738 |
| Fog edge | 60.08 | 57,952.63 / 58,179.97 | 67,565 / 70,110 | 10,923 / 11,886 | 0 / 0 |
| Construction | 60.08 | 37,148.33 / 37,082.12 | 62,528 / 62,445 | 9,529 / 9,247 | 0 / 0 |

All four windows cover the 1,200 ticks between boundaries 203 and 1,403.
Every scene sampled 600 units at its maximum. Battle, pan and fog each
registered 8,979 hits. Fog observed visible and hidden units in all 60
activity samples. Construction registered 8,364 hits and five accepted
placements. Both cameras stood still in the static scenes. The pan travelled
2,352 and 2,388 blocks, or 39.2 and 39.8 blocks per game-second. Local scheduling
produced occasional movement corrections and a battle start catch-up. Even
using wall duration, the highest gameplay mean was below 62,555 B/s.

Real clients receive vanilla movement and head-rotation packets that the
mock receivers miss or see less often. Those three packet types account for
37,055 B/s in the real battle and about 37,799 B/s in the real pan. That
explains most of the extra gameplay bytes. JFR groups custom payloads by
their vanilla packet ID. The mock counters additionally break out each
Strategos payload. Pan streamed about 36–37 MB of terrain per real peer
against 12.65 MB per mock peer, counted separately in both cases.

The whole-recording summary can omit bytes between its periodic aggregation
intervals. On pan those gaps account for 5,797,623 and 5,798,179 B. Counting
the packet events only inside the declared summary periods reconciles all
eight peer and scene combinations to within 24–146 B, consistent with
boundary timing around event commits. Packet events and network summaries
both use the encoded size before compression and framing.

**Tier-4 decision (2026-11-23): keep the reserve inactive.** All eight real
peer measurements pass the 102,400 B/s gameplay ceiling, including the actual
roughly 40-block/s pan. The greatest gameplay mean was 62,639.93 B/s.
