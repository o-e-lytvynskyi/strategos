# ADR-0018: One versioned `SavedData` blob per match, codecs in core, quarantine on failure

Date: 2026-10-21

## Status

Accepted; amended by [ADR-0026](0026-save-template-identities.md) (template identities)

## Context

- **The core owns all match state** (ADR-0008), and bodies are `noSave()`, so the match must
  persist outside chunks.
- **The 1.21.1 `SavedData` API, verified:**
  - `new SavedData.Factory<>(ctor, load, null)` and `DimensionDataStorage#computeIfAbsent`;
  - serialisation runs on the main thread, and NeoForge writes the file on an IO worker
    (SavedData.java:36–57);
  - autosave runs every 6,000 ticks (fact 24).
- **A trap.** `DimensionDataStorage#readSavedData` logs any exception and returns null, and
  `computeIfAbsent` then creates an empty object that overwrites the file at the next save
  (DimensionDataStorage.java:43–76). One bad decode would silently reset a match.
- **Chunk attachments exist only while their chunk is loaded** (fact 24), so they cannot hold
  authoritative data.
- **Chunks and data can disagree.** A crash between the chunk save and the data save can leave the
  world's blocks and the match state out of step.
- **The 1.21.5 and 1.21.6 changes** (`SavedDataType`, `ValueInput`/`ValueOutput`) accept codecs.

## Decision

- **Two blobs:**
  - **`strategos_registry`** in the overworld: schema version, save epoch, arena-world flags, slot
    table and match records (id, level, space, slot, map and version, seed, lifecycle, rules and
    template-set hashes, last epoch).
  - **`strategos_match_<id>`** in the hosting level: schema version, save epoch, the full core
    snapshot (entities and components, factions, queues, explored bitsets, outstanding job results,
    tick), the journal cursor and the nav page content hashes.

  The match blob is self-contained; if the epochs disagree, the blob wins and the mismatch is
  logged.
- **Codecs are DFU `Codec`s defined in core.** The mod converts with `NbtOps`, and L1 round-trips
  with `JsonOps`. Templates are referenced by string id, never by network index.
- **Versions.** Every blob carries `"v"`. `Migrations` walks `v → v+1` steps on a DFU `Dynamic<?>`
  before decoding, tested in L1 with JSON fixtures and in L2 with NBT fixtures of every released
  schema. A missing template fails the load with a named error, unless the operator allows a lossy
  upgrade.
- **Quarantine:**
  1. Our deserializer never throws. On a decode error it returns a quarantined `MatchData` that
     keeps the raw tag, writes it back unchanged and marks the match `FAULTED`.
  2. If `get(factory, name)` returns null while the file exists (located with
     `DimensionType.getStorageFolder`), the file is copied to `<name>.dat.corrupt-<epoch>`, the
     match is `FAULTED`, and nothing is ever registered under that name.

  L3 tests load corrupted fixtures and check that the files survive.
- **At save,** the path service finishes outstanding jobs and stores their results, so a reload
  continues bit-identically. Serialisation is budgeted at ≤ 100 ms (budget G).
- **The nav grid is not saved.** On resume it is rebuilt from the world, and the stored page hashes
  flag differences. `WorldReconciler` writes core-owned blocks (building footprints, depleted node
  blocks) back to the core's state, and turns every other difference into a
  `WorldChange(EXTERNAL)` input on the first tick.
- **Bodies are never saved,** so they never duplicate. They are recreated from core state.
- **Saves** run at vanilla autosave and on `ServerStoppingEvent`. Singleplayer matches resume
  `PAUSED`.

## Consequences

- A corrupted or incompatible save is never silently replaced by an empty match; the operator sees
  a named fault and keeps the file.
- A crash between saves is detected and converged instead of leaving ghost blocks or missing
  buildings.
- Old saves keep loading through tested migrations; every released schema gets a fixture.
- One blob per match means one serialisation pass. A 600-unit snapshot is a few hundred KB to a
  few MB *(estimate)*, written as primitive-array tags.
- The 1.21.5+ ports change only `server.persist`.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Unit state in entity NBT, faction state in `SavedData` (B) | Two stores written separately can mix saves, duplicating or losing resources; entity NBT ties state to vanilla loading |
| Nine `SavedData` files per subsystem (C) | No shared epoch; a crash mid-save can mix files |
| Saving the navigation model (C) | Restored without being checked against the world; a crash between saves leaves it wrong silently |
| Chunk attachments for territory and exploration | Gone while chunks are unloaded; unusable for map-wide data |
| Vanilla `DataFixer`s | Built for vanilla's schema types; DFU `Dynamic` migrations on our own versions are simpler and testable in L1 |
| Trusting `computeIfAbsent` as is | Silently resets a match after any decode error |
