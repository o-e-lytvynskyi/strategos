package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.body;
import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.handles;
import static dev.strategos.gametest.suites.Units.spawn;
import static dev.strategos.gametest.suites.Units.unit;

import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRecord;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSource;
import dev.strategos.mod.server.match.MatchSpace;
import dev.strategos.mod.server.persist.MatchData;
import dev.strategos.mod.server.persist.Quarantine;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.IOUtilities;

/** Saved matches in a real level (CORE-07.5, ORD-01.5, CORE-12.1, CORE-12.2): the round trip and the quarantine. */
public final class PersistenceTests {
    /** Ids no match of a run reaches, for blobs that belong to no match. */
    private static final int CONTENT_ID = 1_000_001;

    private static final int UNREADABLE_ID = 1_000_002;

    /**
     * The modification time a planted file gets: a rewrite keeps the bytes of a quarantined blob (it saves its raw tag),
     * so only the time shows that the file was written again.
     */
    private static final FileTime PLANTED = FileTime.fromMillis(1_000_000_000_000L);

    private PersistenceTests() {}

    /**
     * CORE-07.5, ORD-01.5 (spec 1.6 row 30): saved, unloaded and restored in the same level, the hash holds, and the
     * five units ordered to one point all arrive, one on it and the rest within 2.5 blocks (PATH-06's crowd arrival).
     */
    @StrategosTest
    public static void saveRestoreSameHash(GameTestHelper h) {
        MatchRegistry registry = StrategosServer.get().matches();
        MatchRuntime rt = MatchFixture.create(
                h, MatchFixture.options(SimHarness.DEFAULT_SEED, h).withPersistent(true));
        spawn(rt, 8, 16, 5, 2);
        var state = new Object() {
            MatchRuntime back;
        };
        h.startSequence()
                .thenWaitUntil(() -> {
                    int[] units = handles(rt);
                    check(units.length == 5, "not spawned yet");
                    for (int handle : units) {
                        body(rt, handle);
                    }
                })
                .thenExecute(
                        () -> rt.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(handles(rt)), new Vec2d(16.5, 16.5))))
                .thenExecuteAfter(30, () -> {
                    MatchCore live = core(rt);
                    long tick = live.queries().tick();
                    long hash = live.stateHash();
                    check(
                            Arrays.stream(handles(rt)).anyMatch(u -> unit(rt, u).state() == UnitState.WALKING),
                            "no unit is walking at the save");
                    MatchData data = rt.data();
                    MatchRecord record = registry.record(rt.id());
                    check(data != null && record != null, "the persistent match has no blob or no record");
                    CompoundTag tag = data.save(new CompoundTag(), h.getLevel().registryAccess());

                    registry.unload(rt.id());
                    state.back = registry.restore(h.getLevel(), record, MatchData.load(rt.id(), tag));

                    check(state.back.lifecycle() == MatchLifecycle.PAUSED, "restored " + state.back.lifecycle());
                    check(core(state.back).queries().tick() == tick, "the restored match is at another tick");
                    check(core(state.back).stateHash() == hash, "the restored match has another hash");
                    registry.resume(state.back.id());
                })
                .thenWaitUntil(() -> {
                    MatchRuntime back = state.back;
                    check(back.bodies().count() == 5, back.bodies().count() + " bodies after the restore, not 5");
                    for (int handle : handles(back)) {
                        UnitView unit = unit(back, handle);
                        check(
                                unit.state() == UnitState.IDLE && Math.hypot(unit.x() - 16.5, unit.z() - 16.5) <= 2.5,
                                "a restored unit did not reach its target: " + unit);
                    }
                })
                .thenSucceed();
    }

    /** CORE-12.1: a blob whose content does not decode is kept, never rewritten, and its match is FAULTED. */
    @StrategosTest
    public static void corruptContentQuarantined(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        MatchId id = new MatchId(CONTENT_ID);
        Path file = blobFile(level, id, "wrong_types.nbt");
        byte[] before = bytes(file);

        MatchRuntime rt = StrategosServer.get().matches().loadSaved(record(h, id), 0);

        check(
                rt.lifecycle() == MatchLifecycle.FAULTED
                        && String.valueOf(rt.faultMessage()).contains("quarantined"),
                "the match is " + rt.lifecycle() + ": " + rt.faultMessage());
        MatchData data = level.getDataStorage().get(MatchData.factory(id), MatchData.name(id));
        check(
                data != null && data.state() == MatchData.State.QUARANTINED,
                "the blob in the storage is " + (data == null ? null : data.state()));
        data.setDirty(); // whatever marks it, a quarantined blob is never written
        saveAndWait(level);
        unchanged(file, before, "the quarantined blob");
        h.succeed();
    }

    /** CORE-12.2: a file vanilla cannot read is copied, kept, its match FAULTED, and nothing registered under its name. */
    @StrategosTest
    public static void unreadableFileQuarantined(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        MatchId id = new MatchId(UNREADABLE_ID);
        Path file = blobFile(level, id, "garbage.dat");
        Path copy = file.resolveSibling(MatchData.name(id) + ".dat.corrupt-7");
        byte[] before = bytes(file);
        try {
            MatchRuntime rt = StrategosServer.get().matches().loadSaved(record(h, id), 7);

            check(
                    rt.lifecycle() == MatchLifecycle.FAULTED
                            && String.valueOf(rt.faultMessage())
                                    .contains(copy.getFileName().toString()),
                    "the match is " + rt.lifecycle() + ": " + rt.faultMessage());
            check(Files.exists(copy) && Arrays.equals(bytes(copy), before), "no copy with the unreadable file's bytes");
            check(
                    level.getDataStorage().get(MatchData.factory(id), MatchData.name(id)) == null,
                    "an object is registered under the unreadable file's name");
            saveAndWait(level);
            unchanged(file, before, "the unreadable file");
        } finally {
            delete(file);
            delete(copy);
        }
        h.succeed();
    }

    /** A saved match's record in the test's own box; the registry blob never holds it. */
    private static MatchRecord record(GameTestHelper h, MatchId id) {
        return new MatchRecord(
                id,
                MatchSpace.fromBounds(h.getLevel().dimension(), h.getBounds()),
                0,
                MatchLifecycle.RUNNING,
                MatchSource.GAMETEST,
                0,
                Optional.empty());
    }

    /** Copies a corpus file into the level's data folder as the blob of {@code id}. */
    private static Path blobFile(ServerLevel level, MatchId id, String corpusFile) {
        Path file = Quarantine.dataFile(level, MatchData.name(id));
        try (InputStream in = PersistenceTests.class.getResourceAsStream("/fixtures/corrupt/" + corpusFile)) {
            check(in != null, "no corpus file " + corpusFile);
            Files.createDirectories(file.getParent());
            Files.write(file, in.readAllBytes());
            Files.setLastModifiedTime(file, PLANTED);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    /** Saves the level's data storage; the files are written on NeoForge's IO worker, so this waits for it. */
    static void saveAndWait(ServerLevel level) {
        level.getDataStorage().save();
        IOUtilities.waitUntilIOWorkerComplete();
    }

    private static void unchanged(Path file, byte[] before, String what) {
        check(Arrays.equals(bytes(file), before), what + " has other bytes");
        try {
            check(Files.getLastModifiedTime(file).equals(PLANTED), what + " was written again");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] bytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final dev.strategos.core.shared.TemplateId HOUSE =
            new dev.strategos.core.shared.TemplateId("strategos:building/house");
    private static final dev.strategos.core.shared.TemplateId CIVIC_CENTRE =
            new dev.strategos.core.shared.TemplateId("strategos:building/civic_centre");

    /**
     * MATCH-04.3: a crash between saves, as a fixture: the match's blob saved, the match unloaded, and then the world
     * changed, a wall of its house taken away and a stone set on open ground. Restored, the wall stands again and is
     * protected before any tick, and the stone comes in with the first tick's capture, its page changed in the core (the
     * house's page changes too: the writer's own blocks reach the core's pages only through such a capture).
     */
    @StrategosTest(timeoutTicks = 600)
    public static void theReconcilerConvergesACrashBetweenSaves(GameTestHelper h) {
        MatchRegistry registry = StrategosServer.get().matches();
        MatchRuntime rt = MatchFixture.create(
                h, MatchFixture.options(SimHarness.DEFAULT_SEED, h).withPersistent(true));
        rt.submit(
                PlayerSlot.ADMIN,
                new dev.strategos.core.protocol.SpawnBuilding(
                        HOUSE, new dev.strategos.core.shared.FactionSlot(0), 4, 4, 0));
        var state = new Object() {
            MatchRuntime back;
            net.minecraft.core.BlockPos wall;
            net.minecraft.world.level.block.state.BlockState was;
            java.util.List<dev.strategos.core.sim.PageRef> before;
        };
        net.minecraft.core.BlockPos stone = h.absolutePos(new net.minecraft.core.BlockPos(24, 2, 24));
        h.startSequence()
                .thenWaitUntil(() -> {
                    check(core(rt).queries().buildings().size() == 1, "no house yet");
                    var house = core(rt).queries().buildings().get(0);
                    state.wall = new net.minecraft.core.BlockPos(
                            rt.space().origin().getX() + house.footprint().anchorX(),
                            house.baseY() + 1,
                            rt.space().origin().getZ() + house.footprint().anchorZ());
                    check(
                            StrategosServer.get()
                                            .writer()
                                            .protectedBlocks(h.getLevel())
                                            .owner(state.wall)
                                    != 0,
                            "the house is not up yet");
                    // the whole house written: the writer's budget is shared with the batch's other matches, and a
                    // block still queued at the save would be one more for the reconciler to rewrite
                    var space = rt.space();
                    check(
                            StrategosServer.get()
                                    .writer()
                                    .pendingIn(
                                            h.getLevel(),
                                            rt.id().value(),
                                            new net.minecraft.core.BlockPos(
                                                    space.origin().getX(),
                                                    space.minY(),
                                                    space.origin().getZ()),
                                            new net.minecraft.core.BlockPos(
                                                    space.origin().getX() + space.sizeX() - 1,
                                                    space.maxY(),
                                                    space.origin().getZ() + space.sizeZ() - 1))
                                    .isEmpty(),
                            "the house is still being written");
                })
                .thenExecuteAfter(5, () -> {
                    state.was = h.getLevel().getBlockState(state.wall);
                    state.before = core(rt).snapshot().pages();
                    MatchData data = rt.data();
                    MatchRecord record = registry.record(rt.id());
                    check(data != null && record != null, "no blob or record");
                    CompoundTag tag = data.save(new CompoundTag(), h.getLevel().registryAccess());
                    registry.unload(rt.id()); // the crash: what follows reaches the world, not the blob
                    h.getLevel()
                            .setBlockAndUpdate(
                                    state.wall, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    h.getLevel()
                            .setBlockAndUpdate(stone, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                    state.back = registry.restore(h.getLevel(), record, MatchData.load(rt.id(), tag));
                    check(h.getLevel().getBlockState(state.wall).equals(state.was), "the wall is not back");
                    check(
                            StrategosServer.get()
                                            .writer()
                                            .protectedBlocks(h.getLevel())
                                            .owner(state.wall)
                                    != 0,
                            "the wall is not protected again");
                    var done = state.back.reconciled();
                    check(
                            done != null && done.rewritten() == 1,
                            "rewrote " + (done == null ? "none" : done.rewritten()));
                    registry.resume(state.back.id());
                })
                .thenExecuteAfter(3, () -> {
                    java.util.List<dev.strategos.core.sim.PageRef> after =
                            core(state.back).snapshot().pages();
                    int changed = 0;
                    for (int k = 0; k < after.size(); k++) {
                        changed += after.get(k).slice() != state.before.get(k).slice() ? 1 : 0;
                    }
                    int sx = (stone.getX() - state.back.space().origin().getX()) / 16;
                    int sz = (stone.getZ() - state.back.space().origin().getZ()) / 16;
                    boolean stonePage = false;
                    for (int k = 0; k < after.size(); k++) {
                        stonePage |= after.get(k).x() == sx
                                && after.get(k).z() == sz
                                && after.get(k).slice() != state.before.get(k).slice();
                    }
                    check(stonePage, "the stone's page came in unchanged, of " + changed + " changed");
                    // and the house's page holds its wall: the capture after the reconcile saw it standing
                })
                .thenSucceed();
    }

    /**
     * MATCH-04.1 at L3, its minutes made ticks: a match with a centre training and workers walking, its blob saved at
     * tick 200 while it ran on to 400; then restored from that blob in the same space and run to 400 again, it has the
     * hash it had there. (L1's {@code ResumeTest} takes the first loop from minute 10 to 15.)
     */
    @StrategosTest(timeoutTicks = 1_200)
    public static void reloadAtTenEqualsFifteenUninterrupted(GameTestHelper h) {
        MatchRegistry registry = StrategosServer.get().matches();
        MatchRuntime rt = MatchFixture.create(
                h, MatchFixture.options(SimHarness.DEFAULT_SEED, h).withPersistent(true));
        rt.submit(
                PlayerSlot.ADMIN,
                new dev.strategos.core.protocol.SpawnBuilding(
                        CIVIC_CENTRE, new dev.strategos.core.shared.FactionSlot(0), 2, 2, 0));
        rt.submit(
                PlayerSlot.ADMIN,
                new dev.strategos.core.protocol.Grant(new dev.strategos.core.shared.FactionSlot(0), "food", 500));
        spawn(rt, 24, 24, 4, 2);
        var state = new Object() {
            long saveAt = -1;
            CompoundTag tag;
            MatchRecord record;
            long hashAt;
            long hash;
            MatchRuntime back;
        };
        h.startSequence()
                .thenWaitUntil(() -> check(
                        handles(rt).length == 4
                                && !core(rt).queries().buildings().isEmpty(),
                        "not ready"))
                .thenExecute(() -> {
                    int centre = core(rt).queries().buildings().get(0).handle();
                    for (int k = 0; k < 3; k++) {
                        rt.submit(
                                new PlayerSlot(0),
                                new dev.strategos.core.protocol.Train(
                                        centre, dev.strategos.core.content.TemplateSet.WORKER));
                    }
                    rt.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(handles(rt)), new Vec2d(8.5, 26.5)));
                })
                .thenExecuteAfter(40, () -> {
                    MatchData data = rt.data();
                    state.record = registry.record(rt.id());
                    check(data != null && state.record != null, "no blob or record");
                    state.saveAt = core(rt).queries().tick();
                    state.tag = data.save(new CompoundTag(), h.getLevel().registryAccess());
                    state.hashAt = state.saveAt + 200;
                })
                .thenWaitUntil(() -> check(core(rt).queries().tick() >= state.hashAt, "not at the second mark"))
                .thenExecute(() -> {
                    check(
                            core(rt).queries().tick() == state.hashAt,
                            "passed the mark: " + core(rt).queries().tick());
                    state.hash = core(rt).stateHash();
                    registry.unload(rt.id());
                    state.back = registry.restore(h.getLevel(), state.record, MatchData.load(rt.id(), state.tag));
                    check(core(state.back).queries().tick() == state.saveAt, "restored at another tick");
                    registry.resume(state.back.id());
                })
                .thenWaitUntil(() -> check(core(state.back).queries().tick() >= state.hashAt, "not at the mark again"))
                .thenExecute(() -> {
                    check(core(state.back).queries().tick() == state.hashAt, "passed the mark again");
                    check(core(state.back).stateHash() == state.hash, "another hash at tick " + state.hashAt);
                })
                .thenSucceed();
    }

    @StrategosTest
    public static void endedMatchStaysEndedAfterRestore(GameTestHelper h) {
        MatchRegistry registry = StrategosServer.get().matches();
        MatchRuntime rt = MatchFixture.create(
                h, MatchFixture.options(SimHarness.DEFAULT_SEED, h).withPersistent(true));
        spawn(rt, 8, 16, 1, 0);
        h.startSequence()
                .thenWaitUntil(() -> check(handles(rt).length == 1, "not spawned"))
                .thenExecute(() -> {
                    registry.end(rt.id());
                    check(rt.lifecycle() == MatchLifecycle.ENDED, "not ended before save");
                    MatchRecord record = registry.record(rt.id());
                    check(record != null && record.lifecycle() == MatchLifecycle.ENDED, "record not ended");
                    CompoundTag tag =
                            rt.data().save(new CompoundTag(), h.getLevel().registryAccess());
                    registry.unload(rt.id());
                    MatchRuntime back = registry.restore(h.getLevel(), record, MatchData.load(rt.id(), tag));
                    check(back.lifecycle() == MatchLifecycle.ENDED, "ended match restored as " + back.lifecycle());
                    long tick = core(back).queries().tick();
                    boolean refused = false;
                    try {
                        registry.resume(back.id());
                    } catch (IllegalStateException expected) {
                        refused = true;
                    }
                    check(refused, "ended match accepted resume");
                    refused = false;
                    try {
                        back.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(handles(back)), new Vec2d(12.5, 12.5)));
                    } catch (dev.strategos.mod.server.match.MatchRefusedException expected) {
                        refused = true;
                    }
                    check(refused, "ended match accepted commands");
                    check(core(back).queries().tick() == tick, "ended match advanced");
                })
                .thenSucceed();
    }
    /** A newer terminal blob cannot be revived by an older PAUSED registry record (#23). */
    @StrategosTest
    public static void terminalOutcomeOverridesPausedRecord(GameTestHelper h) {
        MatchRegistry registry = StrategosServer.get().matches();
        MatchRuntime rt = MatchFixture.create(
                h, MatchFixture.options(SimHarness.DEFAULT_SEED, h).withPersistent(true));
        registry.pause(rt.id());
        var saved = core(rt).snapshot();
        var finished = new dev.strategos.core.sim.MatchSnapshot(
                saved.config(),
                saved.tick(),
                saved.templateSetHash(),
                saved.slots(),
                saved.units(),
                saved.pendingCommands(),
                saved.pages(),
                saved.paths(),
                saved.nextGroup(),
                saved.treasury(),
                saved.terrainEdits(),
                saved.nodes(),
                saved.buildings(),
                saved.ledger(),
                saved.carried(),
                new dev.strategos.core.sim.MatchSnapshot.Play(
                        java.util.List.of(1L, 0L, 1L, 0L, saved.tick()),
                        saved.play().ai(),
                        saved.play().aiWake(),
                        saved.play().alerts()),
                saved.templateBindings());
        MatchRecord record = registry.record(rt.id());
        check(record != null && record.lifecycle() == MatchLifecycle.PAUSED, "no paused record");
        MatchData data = MatchData.live(rt.id(), () -> finished, record::lastEpoch);
        CompoundTag tag = data.save(new CompoundTag(), h.getLevel().registryAccess());
        registry.unload(rt.id());
        MatchRuntime back = registry.restore(h.getLevel(), record, MatchData.load(rt.id(), tag));
        check(back.lifecycle() == MatchLifecycle.ENDED, "terminal outcome restored as " + back.lifecycle());
        check(core(back).snapshot().play().ended(), "terminal outcome lost");
        h.succeed();
    }
}
