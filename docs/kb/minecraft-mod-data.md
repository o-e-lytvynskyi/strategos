# Minecraft mods: saves (NeoForge 1.21.1)

How `SavedData` and `DimensionDataStorage` write and read files, and where data gets lost on
the way. The general toolchain and tests — [minecraft-mod.md](minecraft-mod.md). The reference is
this repository: [ADR-0018](../adr/0018-persistence-and-versioning.md) and `server/persist/`.

## SavedData is written to disk asynchronously

NeoForge 21.1 hands the `SavedData` write to an IO worker (`IOUtilities.withIOWorker` in
`SavedData#save(File, …)`), and only if the object is dirty. Serialisation to NBT happens on the
main thread, and the file appears later. A test that reads the file right after `storage.save()`
races with the write: after saving, call `IOUtilities.waitUntilIOWorkerComplete()`. A round trip
is easier to check in memory (`save` → `load` of the tag).

## An unreadable file: `get` caches null, `computeIfAbsent` overwrites the file

On a read error `DimensionDataStorage#get` logs it and puts `null` into the cache. The next
`computeIfAbsent` under the same name creates an empty object, and the very first save
overwrites the corrupted file. If the file exists and `get` returned `null`, copy the file aside
and register nothing under that name.

## "The file was not rewritten" cannot be checked by bytes

An object that keeps its original tag (quarantine) produces the same gzip byte for byte on
rewrite: `NbtIo` writes a header with zero time. A byte comparison will not catch an extra
write. The test sets a known modification time on the file (`Files.setLastModifiedTime`) and
checks that it is the same after the save.

## DFU: an exception from `RecordCodecBuilder.apply` escapes from `parse`

A record constructor that throws on bad data (an invariant check) is called inside `apply`. DFU
does not catch it: `parse` throws instead of returning `DataResult.error`. Check invariants in
`flatXmap`/`validate` with an error in the `DataResult`, and wrap `decode` itself in
`try/catch` if loading must never throw.

## DFU: a new `RecordCodecBuilder` field reorders the keys in JSON

`encode` writes keys not in declaration order but in the traversal order of the applicative tree
(`apN` is unfolded into nested ones), and that tree depends on the number of fields. Add an 11th
field — even `optionalFieldOf` with a default that is skipped in the output — and the keys of
the other fields line up differently (`state, z, yaw, …` → `z, yaw, state, …`). Byte references
(JSON snapshots, compressed journals) change with the same content. Compare decode → encode with
the current codec, or compare state hashes, not file bytes.

## A reload listener at server start fires before the server holder exists

The startup datapack load (and the `apply` of your own `AddReloadListenerEvent` listener) runs
before `ServerAboutToStartEvent`. The holder created in that event is still `null` in `apply`,
and the data of the first load is lost. When created, the holder reads the same files itself
from `server.getResourceManager()`, and the listener works only on later `/reload`s. NeoForge
conditions outside the listener: `new ConditionalOps<>(RegistryOps.create(JsonOps.INSTANCE,
registryAccess), server.getServerResources().managers().getConditionContext())`.

## Data with errors for tests does not belong in the gametest resources

Dev runs load the gametest source set as part of the mod, so its `data/` goes into every run,
GameTest and smoke included. Planted broken files will break loading everywhere. They belong in
`world/datapacks/<pack>/` (with `pack.mcmeta`, `pack_format` 48 for 1.21.1) in a separate game
directory of the spike. A new world enables such packs itself, and extra ones are disabled with
`initial-disabled-packs=file/<pack>` in `server.properties`. To enable a pack on the fly:
`server.reloadResources(ids + "file/<pack>")`. On the server thread this call blocks
(`managedBlock`) until it is ready, so the `/reload` time is measured synchronously around it.

## A data map without `synced` is not visible to the client

`DataMapType.builder(...).build()` loads the map on the server only: `Holder#getData` on the
client returns `null` for any block, without an error. For the client to read an entry (a click
on a "resource" block) you need `.synced(networkCodec, mandatory)` (DataMapType.java:152);
`mandatory = false` — a client without the mod can still join.


## `RecordCodecBuilder` takes at most 16 fields

`group(...)` in DFU is overloaded up to 16 arguments (`Products.P16`); a seventeenth field does
not compile. To add a field without changing the format: build the last two fields into
`RecordCodecBuilder.mapCodec(...)` (that is a `MapCodec`, its keys stay on the same level, not
in a nested object) and pass it to `group` as one field through `forGetter(r -> new Pair(...))`;
`apply` then gets a lambda of 16 arguments and unpacks the pair itself. Old saves are read if
both keys are `optionalFieldOf`.

## A load after which the match goes differently: how to look for the cause

The hash at the load tick matches, but a tick or two later it diverges — so something that is
not in the hash is restored differently from how it was. The check: an L1 twin — the same match
up to T, a snapshot, a restore, then both step on and the hashes are compared every tick. At the
first divergence compare the hash by parts (storages, then the unit fields: position, velocity,
the automaton's data words, the queue), find the differing unit and see what it reads from the
unhashed state: occupied columns, the neighbour grid, system lists. Two traps found this way
(Strategos M4 T18): on restore the columns of all buildings were blocked, although the field does
not block them, and the farmers ended up in a "wall"; the system's alarm list was considered "just
for show", but a new alarm woke an AI whose state is in the hash — after the load the AI was woken
again. A third (M4 T22): the deposits had a `restoreFootprints` method, but nobody called it —
after a load a deposit did not block the columns and units walked through it. The navigation
grid is not in the hash, so it shows only as a different path: the live one queues a request, the
restored one goes directly. Look by enumerating columns and surfaces around the place where the
unit diverged; and for each "something blocks the columns" check that the restore does the same.
The rule: a restore that derives the state anew takes the same conditions as the step-by-step
logic; any "temporary" list that hashed state depends on is saved.
