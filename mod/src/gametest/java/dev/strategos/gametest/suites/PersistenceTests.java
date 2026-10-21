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

    /** CORE-07.5, ORD-01.5 (spec 1.6 row 30): saved, unloaded and restored in the same level, the hash holds. */
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
                .thenExecuteAfter(20, () -> {
                    MatchRuntime back = state.back;
                    check(back.bodies().count() == 5, back.bodies().count() + " bodies after the restore, not 5");
                    for (int handle : handles(back)) {
                        UnitView unit = unit(back, handle);
                        check(
                                unit.state() == UnitState.IDLE && unit.x() == 16.5 && unit.z() == 16.5,
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
}
