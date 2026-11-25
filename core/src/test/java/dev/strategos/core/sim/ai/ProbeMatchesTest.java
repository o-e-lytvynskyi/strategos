package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.TechResearched;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.journal.ReplayResult;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.JournalFiles;
import dev.strategos.core.testkit.SimHarness;
import java.nio.file.Path;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * CIV-05: seeded headless matches of the probe civilisation against the default, AI against AI on the first skirmish map
 * (Conquest, Fog, both at Normal). Each ends without failure, and its journal, written to a file and read back, replays
 * to the live hash. On some seeds a match saved mid-run through the save codec and restored steps to the live hashes
 * (the {@code RestoreEqualsLiveTest} pattern): the probe's rules, bonus and kit survive a save.
 *
 * <p>The long variant plays 20 seeds for 20 game minutes: the probe reaches Town in the tenth to thirteenth minute (the
 * default, whose skirmish script spends longer on its economy, in the thirteenth to sixteenth when it is not raided, so
 * it is reported and not asserted: it is the control); a match that a conquest ends sooner ends. The fast subset plays
 * three seeds for eight minutes, where the probe has its barracks and its first soldiers.
 */
class ProbeMatchesTest {
    private static final long MINUTE = 1_200;
    private static final long FAST = 8 * MINUTE;
    private static final long LONG = 20 * MINUTE;
    private static final String TOWN = "strategos:phase_town";

    /** Faction's Town ticks, and the mid-match restore: stepped beside the live match to the same hashes. */
    private static final class Check implements HeadlessMatch.Watcher {
        final long[] town = {-1, -1};
        final long restoreAt;
        SimHarness restored;
        long compared;

        Check(long restoreAt) {
            this.restoreAt = restoreAt;
        }

        @Override
        public void tick(SimHarness h, TickOutputs out) {
            for (var e : out.events()) {
                if (e instanceof TechResearched t
                        && t.tech().value().equals(TOWN)
                        && t.faction().value() < 2) {
                    town[t.faction().value()] = out.tick();
                }
            }
            if (restored != null) {
                restored.step();
                compared++;
                assertThat(restored.stateHash())
                        .withFailMessage("the restored match diverged at tick %d", h.tick())
                        .isEqualTo(h.stateHash());
            } else if (restoreAt > 0 && h.tick() == restoreAt) {
                restored = SimHarness.restore(
                        roundTrip(h.core().snapshot()),
                        ProbeMatch.SET,
                        h.core().journal().segments().get(0)::slice);
                assertThat(restored.stateHash())
                        .withFailMessage("the restored match hashes differently at tick %d", h.tick())
                        .isEqualTo(h.stateHash());
            }
        }
    }

    /** A save and a load: the snapshot through its codec, encoded to JSON and decoded again. */
    private static MatchSnapshot roundTrip(MatchSnapshot saved) {
        return MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, MatchSnapshotCodec.encode(JsonOps.INSTANCE, saved)),
                        Migrations.production())
                .getOrThrow();
    }

    /** Plays {@code seed}; the match has not failed and its journal, through a file, replays to its live hash. */
    private static Check play(long seed, long limit, long restoreAt, Path dir) throws Exception {
        Check check = new Check(restoreAt);
        HeadlessMatch.Result r = ProbeMatch.run(seed, limit, check);
        assertThat(r.summary().failure()).as("seed %d", seed).isEmpty();
        Path file = dir.resolve("probe-seed-" + seed + JournalFiles.EXTENSION);
        JournalFiles.write(file, r.journal());
        JournalExport back = JournalFiles.read(file);
        ReplayResult replay = ReplayDriver.replay(back.journal(), ProbeMatch.SET);
        assertThat(replay.matches()).as("seed %d: the replay", seed).isTrue();
        assertThat(replay.finalHash())
                .as("seed %d: the replay ends at the live hash", seed)
                .isEqualTo(r.summary().journalHash())
                .isEqualTo(back.liveHash());
        if (restoreAt > 0 && restoreAt < r.summary().ticks()) {
            assertThat(check.compared)
                    .as("seed %d: ticks stepped after the restore", seed)
                    .isPositive();
        }
        return check;
    }

    @Test
    void threeFastSeedsReplayAndTwoRestoreMidMatch(@TempDir Path dir) throws Exception {
        for (long seed : new long[] {1, 2, 3}) {
            play(seed, FAST, seed <= 2 ? 4 * MINUTE : 0, dir);
        }
    }

    @Test
    @Tag("slow")
    void twentySeedsReplayAndFourRestoreMidMatch(@TempDir Path dir) throws Exception {
        int probeTown = 0;
        long[] seeds = LongStream.rangeClosed(1, 20).toArray();
        for (long seed : seeds) {
            Check c = play(seed, LONG, seed % 5 == 0 ? 10 * MINUTE : 0, dir);
            probeTown += c.town[0] > 0 ? 1 : 0;
        }
        assertThat(probeTown).as("matches in which the probe reached Town").isGreaterThanOrEqualTo(10);
    }
}
