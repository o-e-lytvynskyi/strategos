# S8: terrain census and surface encoding

*Written with M1 T3 on 2026-10-22. Spec: [M1 §8.1](../milestones/M1-movement.md#81-s8-terrain-census-and-surface-encoding-t3--confirms-adr-0015s-grid);
card PATH-01.*

## Question and exit criterion

Does [ADR-0015](../adr/0015-pathfinding-approach.md)'s layered grid hold on real Minecraft
terrain? Specifically:
- how many standable surfaces a column has;
- which Y range the terrain uses;
- how many bytes a page takes in the candidate encoding;
- how long a page takes to build.

Exit (spec §8.1): **≥ 99.9 % of columns need at most 4 surfaces**; **a page builds in ≤ 0.5 ms**;
**nav fits 32 MB at 512²**. If the exit fails, the report names the change: a layer cap of 6, a
narrower band per map, or another layout.

## Setup

- **Machine:** Apple M4 Pro, 12 cores; "Mac OS X 27.0.1" on aarch64 as the JVM reports it.
- **Software:** Temurin 21.0.12.1, heap pinned at 4 GB; NeoForge 21.1.264.
- **World:** the dedicated spike server on a fresh vanilla world: `level-type=minecraft:normal`,
  seed 20261022, structures on, no mobs.
- **Tree:** 2026-10-22, M1 T11 (commit `6ed42cc`) with T3 uncommitted.
- **Command:** `./gradlew :mod:runSpike -Pspike=s8`, which takes 1 min 30 s and ends with its
  `DONE` marker.

## Method

`S8Census` (`mod/src/gametest/.../spike/`) reads `census/s8-regions.json`: the seed, an origin
(0, 0) and 11 biomes (plains, forest, taiga, desert, savanna, swamp, jungle, windswept hills,
badlands, river, beach). For each biome it does the following.

1. **Finds a square.** `ServerLevel#findClosestBiome3d` (ServerLevel.java:1348) finds the nearest
   place of the biome. The census takes the 512² square around it, 1,024 chunks. The found
   coordinates are in the data, so a rerun measures the same squares.
2. **Loads the chunks.** It generates them to `FULL` through region tickets of its own
   `TicketType`. At most 128 chunks are in flight, and the census polls `getChunkNow` each tick.
3. **Records the terrain height** of every column: the top motion-blocking block that is not
   leaves (`Heightmap.Types.MOTION_BLOCKING_NO_LEAVES`).
4. **Classifies and builds, under three band rules.** For each rule, every page is classified
   through the production `BlockClassTable` over a 128-block band, then built with
   `PageBuilder(INFANTRY)`. The build is timed on the server thread, after 6,000 warm-up builds.
   The band starts 16 blocks below one of three heights:
   - **`square_min`:** the square's lowest terrain (the spec's rule);
   - **`square_p1`:** the square's 1st percentile of terrain heights;
   - **`page_min`:** the page's own lowest terrain.
5. **Counts each column's surfaces again** with `PageBuilder`'s rule, without the cap of 4. It
   checks that the topmost four and the overflow count equal the page's own; all 33,792 page
   builds matched. Per column it also counts:
   - surfaces at most 16 blocks below the terrain, which units can reach from the surface;
   - surfaces at most 2 blocks below the terrain, the ground a unit stands on;
   - whether the terrain lies outside the band.
6. **Measures the fixtures:** the L1 bridge, wall walkway and tower.

The squares overlap, so the totals are taken over the **6,800 distinct pages** (1,740,800
columns) of the 11,264 measured.

## Results

The data are in [data/S8-census.csv](data/S8-census.csv) (per square and rule, plus `all` rows over
the distinct pages) and [data/S8-fixtures.csv](data/S8-fixtures.csv).

**Over the distinct columns**, by band rule:

| Band starts 16 blocks below | ≤ 4 surfaces (worst square) | ≤ 6 | Lost near the terrain | Terrain outside the band | Page bytes mean / max | Nav per 512² (max) | Build p50 / p95 (worst square) |
|---|---|---|---|---|---|---|---|
| the square's lowest terrain (spec) | **98.18 %** (95.87 %, badlands) | 99.909 % | **0** | 69,826 (4.0 %), all above | 2,826 / 5,136 | 3.38 MB | 84 / 366 µs |
| the square's 1st percentile | **99.991 %** (99.960 %, jungle) | 99.9999 % | **0** | 2,967 (0.17 %): 1,505 below, 1,462 above | 2,101 / 4,364 | 2.26 MB | 49 / 67 µs |
| the page's lowest terrain | **99.992 %** (99.977 %, jungle) | 99.9999 % | **0** | 345 (0.02 %), all above | 2,067 / 3,852 | 2.21 MB | 48 / 63 µs |

"Lost near the terrain" counts columns where the cap of 4 would drop a surface no deeper than 16
blocks below the terrain. It is 0 under every rule, and so is ground lost to the cap.

**Surfaces per column** under the page band, over the distinct columns:

| Surfaces | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7+ |
|---|---|---|---|---|---|---|---|---|
| Columns | 135,521 | 1,479,571 | 117,759 | 7,153 | 658 | 128 | 9 | 1 |

The columns with no surface (7.8 %) are trunks and ground under low leaves. Leaves are an
obstacle, never a floor, so units cannot stand there.

**Per square:**

| Square (biome found at) | Terrain min / p1 / p50 / p99 / max | Spec band: ≤ 4 | Spec band: terrain above it | Page band: ≤ 4 | Page band: terrain above it |
|---|---|---|---|---|---|
| plains (64, 32) | −17 / 62 / 62 / 95 / 106 | 96.66 % | 2,683 | 99.997 % | 0 |
| forest (−320, 320) | −17 / 61 / 68 / 109 / 129 | 97.86 % | 28,009 | 99.996 % | 0 |
| taiga (−352, −256) | 9 / 61 / 62 / 95 / 114 | 98.58 % | 0 | 99.994 % | 0 |
| desert (1760, 2080) | −32 / 61 / 63 / 118 / 127 | 96.07 % | 28,780 | 99.988 % | 3 |
| savanna (768, 800) | 53 / 62 / 71 / 139 / 209 | 99.95 % | 1,163 | 99.984 % | 290 |
| swamp (640, 512) | 21 / 62 / 69 / 111 / 126 | 99.41 % | 0 | 100 % | 0 |
| jungle (1024, 960) | 42 / 62 / 62 / 162 / 209 | 99.92 % | 3,650 | 99.977 % | 342 |
| windswept hills (544, 544) | 38 / 62 / 68 / 115 / 126 | 99.90 % | 0 | 99.997 % | 0 |
| badlands (1792, 2112) | −32 / 62 / 64 / 118 / 127 | 95.87 % | 33,378 | 99.988 % | 3 |
| river (128, 32) | −17 / 62 / 62 / 95 / 106 | 96.74 % | 2,683 | 99.998 % | 0 |
| beach (96, 0) | −17 / 62 / 62 / 91 / 106 | 96.74 % | 1,795 | 99.9996 % | 0 |

**Fixtures:**

| Fixture | Columns by surface count | Page bytes |
|---|---|---|
| bridge | 192 with 1, 64 with 2 | 2,324 |
| wall walkway | 256 with 1 | 2,068 |
| tower | 220 with 1, 36 with 3 | 2,356 |

All fixtures stay within 4 surfaces.

## Analysis

- **The cap of 4 holds.** Across 1.74 million columns, under any band, the cap never drops a
  surface within 16 blocks of the terrain, and never ground. The builder keeps the topmost four,
  so what overflows is cave floors deeper down, stacked by 1.18+ cave generation.
- **The spec's band rule fails the exit, and the band is why.** One pit or cave mouth sets the
  bottom of the whole square's band: terrain down to Y −17 in plains, −32 in desert and badlands
  gives a band from −33 or −48. That band has two effects:
  - 95–110 blocks of underground enter it, with their stacked caves;
  - its top, at 80–95, cuts off the hills. 10.7 % of forest columns, 11.0 % of desert and 12.7 % of
    badlands have their terrain above the band: no walkable surface at all.
  A cap of 6 does not rescue this rule: 99.75 % in the worst squares.
- **A band that follows the terrain meets the exit in every square.**
  - **Per square, from the 1st percentile:** at least 99.96 %. It loses 0.17 % of columns: pits
    below the band, jungle and savanna peaks above it.
  - **Per page:** at least 99.977 %. It loses 0.02 %, on pages with more than 112 blocks of relief
    (savanna plateaus, jungle).
- **Memory:** nav takes at most 3.4 MB for a 512² square under any band, against 32 MB. A page
  averages 2.0–2.8 KB, as ADR-0015 estimated.
- **Page build:** the median is 42–84 µs on real terrain, where `PageBuilderBench`'s synthetic hilly
  page takes 27 µs. The worst p95 is 366 µs, in the first square measured (JIT recompilation on
  128-block columns and GC around fresh chunks); every other square stays at or below 126 µs.
  Against 0.5 ms: met.

## What it decides

- **ADR-0015's grid stands:** 4 surfaces a column, topmost first, in the layout of `Surface`:
  - floor: 12 bits;
  - headroom: 7 bits, capped at 127 sixteenths;
  - kind: 3 bits;
  - cost code: 2 bits, capped at 3;
  - links: 8 bits.

  The floor needed a 12th bit, which the census's first run found: a full block on a 128-block
  band's top voxel stands at 2,048 sixteenths. The cost code shrank to 2 bits to make room.
- **The Y band:**
  - **Packaged maps** (M1–M12) keep the manifest's band, at most 128 blocks. Their terrain is built
    to fit it.
  - **Terrain taken from an existing world** (the `generated` source and M13's open world) must
    not get one band from the region's lowest terrain. Its band follows each page: 16 blocks below
    the page's lowest terrain, up to 128 high. Each page records its base Y, and links across pages
    compare absolute floors. PATH-13 carries this change; its card says so.
- **R2 of the M1 spec** (more than 4 surfaces in too many columns) does not occur within a band
  that follows the terrain.
- **ADR-0015 stays Proposed** until S7, which completes its exit criteria.

## Raw data

[data/S8-census.csv](data/S8-census.csv), [data/S8-fixtures.csv](data/S8-fixtures.csv),
[data/S8-env.json](data/S8-env.json).

## Deviations and follow-ups

- **The squares overlap.** The spec's rule, the nearest square to one origin, put the river and
  beach squares on the plains square (88 % shared), badlands on desert (88 %) and windswept hills
  on swamp (76 %). Each row describes a square, not a biome, and the totals count each page once.
- **Chunks load through the census's own region tickets,** not the spike arena's
  `TicketController` as the spec says. `forceChunk` generates synchronously
  (ForcedChunkManager.java:96), and forcing squares that way hung the server once: a light worker
  failed on a ProtoChunk, and the server thread waited for the chunk forever.
- **Build times are single samples** on the server thread between ticks. `PageBuilderBench` (JMH)
  stays the reference.
- **The router's cost per block change** (PATH-15.6), measured with the router in T7: 37 ns a change
  outside any match (`perftests.page_capture_and_publication`, 100,000 calls on the M4 Pro). Inside
  a match, a change that keeps the block's nav class costs one more table lookup; one that changes
  it dirties its page, which is rebuilt within 40 ticks. S8 gives PATH-08 (HPA*) no trigger
  either way; the long crossings did (`CrossingBench`, T8: 29,293 and 30,930 expansions for the
  384² and 512² crossings, above the 20,000 cap, ADR-0015's second revision).
