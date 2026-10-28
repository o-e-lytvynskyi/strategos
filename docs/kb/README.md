# Knowledge base

Facts and pitfalls that cost a debugging session and may recur: how Minecraft 1.21.1, NeoForge,
the toolchain and an RTS simulation actually behave. Project decisions live in the specs, the
architecture and the ADRs; this is what the engine and the tools do. Moved here from the owner's
private notes on 2026-10-25, so every developer has it.

Read only the file the task needs. A file past ~150 lines is split, not extended. A new pitfall is
written in the same session, into the file of its area (a branch does it on the branch).

| Question | File |
|---|---|
| Versions and toolchain, server config, block batches, chunk unloading, Brigadier roots and `word()` against `=`, protecting blocks with events (where cancelling does not work), licences | [minecraft-mod.md](minecraft-mod.md) |
| Build: sources jar, Spotless, the dev runtime, MDG, the Spotless/compile race, `:mod:test` as a server, ErrorProne, `build/` in `.gitignore` and a package named `build` | [minecraft-mod-build.md](minecraft-mod-build.md) |
| Save/reload: stable template identities, legacy custom indices, atomic reload guards and terminal lifecycle | [save-reload-identities.md](save-reload-identities.md) |
| Data: SavedData, an unreadable file, the IO worker, DFU, a reload listener at start-up, test datapacks with errors, a data map without `synced` is invisible to the client, the 16-field limit of `RecordCodecBuilder`, finding a mismatch after loading | [minecraft-mod-data.md](minecraft-mod-data.md) |
| GameTest and measurements: verdict from the report, shared per-tick budgets (wait, no fixed delay), retries, chunks, tickets, mass generation, CPU load, blocks next to a moving unit, tick pacing for measurements, work in Post outside MSPT | [minecraft-mod-gametest.md](minecraft-mod-gametest.md) |
| GameTest arena and players: coordinates, barriers, the roof and vanilla trees, the heightmap, a large arena, FakePlayer, a mock player with packets, names ≤ 16, operator level 0, batches on one spot and shared services, the heightmap of an unloaded chunk, blocks above the arena | [minecraft-mod-gametest-arena.md](minecraft-mod-gametest-arena.md) |
| Client: window focus, repeatable screenshots and frozen bodies, freezing on a condition, the cursor from code, `renderDistance` not reaching the server, frame and tick timing, JFR on a Mac, a client command from code, Xvfb on CI, step interpolation | [minecraft-mod-client.md](minecraft-mod-client.md) |
| Client rendering: an own GUI layer and F1, particles past 32 blocks, camera vectors, armour and hand layers of a renderer and their cost in a crowd (vanilla and Sodium), `GuiGraphics` fills over `blit`, "long ago" as `Long.MIN_VALUE`, vanilla animal models for an own entity, scenes "in stages" with `/tick sprint` | [minecraft-mod-client-render.md](minecraft-mod-client-render.md) |
| Network: NeoForge payloads, a disconnect from a codec, a player's channel, a mock connection, flushing packets in a tick, dirty data of a new entity, pairing a joining player with standing entities, teleport after the connection tick, tracking range by client, packet bytes via JFR PacketSent, server-side entity sounds, body equipment without a server tick | [minecraft-mod-net.md](minecraft-mod-net.md) |
| RTS simulation in Java: ORCA (head-on pairs, standing and walking units), crowd arrival, group formation and slot checks, large formations, the stuck ladder, measuring the job pool, `Arrays.sort` allocations, JMH `-prof gc` background, an exception in `@Setup` silently dropping a benchmark | [rts-sim.md](rts-sim.md) |
| Large-unit clearance: adjacent construction eviction, mixed formation recovery, ordered changed-page notifications | [rts-sim-large.md](rts-sim-large.md) |
| Analytic combat: used-empty shot allocators, release/movement phase alignment, circular and layered hit footprints | [rts-sim-combat.md](rts-sim-combat.md) |
| RTS economy and construction: buildable ground ≠ walkable ground, demolition in the grid with a `pageK` delay, a target on a blocked cell, order news for an empty queue, AI slots, worker shares and the silence watchdog, expansion with storehouses and territory | [rts-sim-economy.md](rts-sim-economy.md) |
| Performance fixtures: first asynchronous tick interval, idle warm-up and CPU sampling, equal probe working sets, invalid measurements | [perf-tick-spacing.md](perf-tick-spacing.md) |
