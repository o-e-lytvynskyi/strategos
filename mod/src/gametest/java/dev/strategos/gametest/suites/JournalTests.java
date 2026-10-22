package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.handles;
import static dev.strategos.gametest.suites.Units.match;
import static dev.strategos.gametest.suites.Units.move;
import static dev.strategos.gametest.suites.Units.spawn;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.testkit.JournalFiles;
import dev.strategos.core.testkit.ReplayRunner;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.server.match.MatchRuntime;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/** The binary journal in a real server (CORE-11.4). */
public final class JournalTests {
    private JournalTests() {}

    /**
     * CORE-11.4: {@code /strategos journal save} writes the match's journal as a {@code .strj} file that
     * {@code ReplayRunner} replays to the live tick and hash.
     */
    @StrategosTest
    public static void journalSaveReplays(GameTestHelper h) {
        MatchRuntime rt = match(h);
        spawn(rt, 8, 8, 3, 2.0);
        h.startSequence()
                .thenWaitUntil(() -> check(handles(rt).length == 3, "not spawned yet"))
                .thenExecute(() -> {
                    for (int handle : handles(rt)) {
                        move(rt, handle, 24, 20);
                    }
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    String name = "journaltest_" + rt.id();
                    CommandTests.Recorder recorder = new CommandTests.Recorder();
                    CommandSourceStack source = recorder.at(h, new Vec3(16.5, 1, 16.5));
                    CommandTests.perform(h, source, "strategos journal save " + name + " " + rt.id());
                    Path file = h.getLevel()
                            .getServer()
                            .getWorldPath(LevelResource.ROOT)
                            .resolve("strategos")
                            .resolve("journals")
                            .resolve(name + JournalFiles.EXTENSION);
                    check(Files.exists(file), "no journal file after the command: " + recorder.lines);
                    JournalExport export = JournalFiles.read(file);
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    MatchCore core = core(rt);
                    check(
                            export.liveTick() == core.queries().tick() && export.liveHash() == core.stateHash(),
                            "the saved journal ends at tick " + export.liveTick() + ", the match is at "
                                    + core.queries().tick());
                    ReplayRunner.ReplayReport report =
                            ReplayRunner.check(export.journal(), TemplateSet.builtin(), export.liveHash());
                    check(report.ok(), "the saved journal does not replay: " + report.message());
                })
                .thenSucceed();
    }
}
