# Finding a tick's tail (budget A's p99)

## A p99 at the limit is a bug, not noise

A soak that passes one batch and breaks budget A's p99 by a few per cent in the next, on the same code
and a quiet machine, sits at its limit: the next feature tips it. Treat it as a bug (AGENTS.md, never a
retry until green). M9 T20: `territory_soak` 10.19 ms and `mvp_soak` 10.22 ms against 10, after 9.89 ms
the batch before.

## Find the systems of the slowest ticks, not the hottest methods

The batch's JFR (`mod/build/gametest-perf/perf.jfr`, profile settings) shows where the server thread
spends its time, not which work makes the slow 1 % of ticks. Two cheap steps did:

1. **GC first.** `jfr print --events jdk.GarbageCollection` and count pauses over 5 ms a minute; a
   p99 needs about 1 % of ticks, so one pause a minute is not it. Never export the samples as JSON (12 GB
   for a 2.5-hour batch); stream `jfr print --events jdk.ExecutionSample --stack-depth 64` through a
   script that counts project frames on the `Server thread` in the scene's time window.
2. **Per-system times of the slowest ticks, without Minecraft.** In a scratch worktree, time each
   `system.run` in `Scheduler.runPhase` into a static map, play a headless AI match
   (`HeadlessMatch.run` on MAP-02, as `OpeningBalance` does), and for the slowest 1 % of ticks sum each
   system's time and print the ticks' residues modulo the cadences. Run the same on `main` to tell a
   regression from an old tail. Anonymous systems need `getName()`, not `getSimpleName()`.

The terrain rebuild (every `pageK` = 20 ticks) was the tail, the same on `main`.

## An immutable value's constructor must not hash what nobody reads

`VoxelSlice` hashed every voxel in its constructor. A rebuilt page decodes its slice and edits it (a
second slice), and neither hash was ever read: about three quarters of the rebuild's server-thread
samples. Hashing on first use (a plain field published by a volatile flag; every thread computes the same
value) took the headless p99 from 2.48 to 1.90 ms and the two soaks to 7.19 and 6.98 ms.
