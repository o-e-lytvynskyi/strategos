# ADR-0024: Canopy thinning by vanilla's leaf-distance rule

Date: 2026-10-23

## Status

Accepted (M3 T8, under the owner's approval of ECO-03, which asked for this ADR). Supersedes ADR-0021's leaf rule.

## Context

- ADR-0021 removed a tree's leaves only once the tree was exhausted: until its last log went, the whole canopy hung
  over a stump that shrank log by log.
- The block writer places and removes with `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE` and no neighbour updates (ADR-0021,
  BLD-15), so vanilla never recounts the leaves' `distance` after a log goes; and match chunks hold `ticking=false`
  tickets, so leaves never get the random ticks that decay them. On a map, proving ground included, leaves are also
  `persistent`, which vanilla never decays.
- Vanilla's rule is simple and well known (LeavesBlock.java:29, 105–125): a leaf's distance is one more than the least
  of its six faces' neighbours, a log counting 0 and a leaf its own distance; a leaf at 7 decays.

## Decision

- **The core thins every canopy by vanilla's rule.** When a log of a tree goes, the leaves round that tree (its box
  grown by six) that are farther than six leaf steps from every standing log of any tree go too, whatever their
  `persistent` flag: as cosmetic writes, under the writer's budget, and as air in the core's own terrain.
- **The core computes it from its own data:** the materials of each page's last slice (leaves and logs from the
  `resource_yield` data map), less every voxel it has edited since. Nothing reads the level.
- **Leaves a neighbouring tree still holds stay,** as vanilla would keep them.

## Consequences

- A felled tree's canopy recedes as it shrinks, and no leaf floats over a stump, at every stage (ECO-03.5).
- The rule is the core's: deterministic, journaled through the slices, and checked in L1 against a reference search
  on the shapes of every species (`TreeFellingTest`).
- A leaf vanilla itself decays (a non-persistent leaf in a chunk a player keeps random-ticking) leaves the core's terrain
  through the block-change router like any external edit; it credits nothing, since leaves yield nothing.
