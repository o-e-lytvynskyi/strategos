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
34,341.80 B/s against 102,400 B/s. The native comparison remains the
evidence gate for the tier-4 decision.
