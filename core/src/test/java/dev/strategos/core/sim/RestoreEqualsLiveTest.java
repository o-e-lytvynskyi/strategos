package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.ProvingGround;
import dev.strategos.core.testkit.RandomCommands;
import dev.strategos.core.testkit.Seeds;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.SoakOrders;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * M9 §10.4 item 3 (the restore check): three soak findings were state outside the snapshot. On every seeded scene of
 * the replay and soak tests, a match run to mid-match is saved the way saves do (the snapshot through its codec, encoded
 * and decoded), restored into a fresh match, and both are stepped with the same inputs: their state hashes are equal at
 * every tick. A store missing from the snapshot (or from the hash) shows as the first tick the two differ.
 */
class RestoreEqualsLiveTest {
    private static final List<FactionSlot> FACTIONS = List.of(new FactionSlot(0), new FactionSlot(1));
    private static final int SEEDED_MID = 1_000;
    private static final int SEEDED_AFTER = 500;
    private static final int SOAK_PER_FACTION = 100;
    private static final int SOAK_TICKS = 1_200;

    static LongStream seeds() {
        return Seeds.set("m0-200");
    }

    @ParameterizedTest(name = "seed={0}")
    @MethodSource("seeds")
    void aSeededMatchRestoredMidRunStepsToTheLiveHashes(long seed) {
        checkSeeded(seed);
    }

    /** The scene of {@link SeededReplayTest}: {@link SeededRuns}' two factions on 32 x 32, random commands. */
    private static void checkSeeded(long seed) {
        SimHarness live = SeededRuns.run(seed, SEEDED_MID);
        SimHarness restored = SimHarness.restore(roundTrip(live.core().snapshot()), TemplateSet.builtin());
        assertThat(restored.stateHash())
                .withFailMessage("seed=%d: the restored match hashes differently at tick %d", seed, live.tick())
                .isEqualTo(live.stateHash());
        for (int t = 0; t < SEEDED_AFTER; t++) {
            for (FactionSlot faction : FACTIONS) {
                for (Command command : RandomCommands.next(seed, live.tick(), live.queries(), faction)) {
                    live.submit(new PlayerSlot(faction.value()), command);
                    restored.submit(new PlayerSlot(faction.value()), command);
                }
            }
            live.step();
            restored.step();
            assertThat(restored.stateHash())
                    .withFailMessage("seed=%d: the restored match diverged at tick %d", seed, live.tick())
                    .isEqualTo(live.stateHash());
        }
    }

    /** The scene of {@link SoakSeedReplayTest}: two crowds on the proving ground, the soak's orders, restored mid-run. */
    @Test
    void theSoakSeedRestoredMidRunStepsToTheLiveHashes() {
        ProvingGround ground = ProvingGround.generate();
        SimHarness live = SimHarness.builder()
                .size(ProvingGround.SIZE, ProvingGround.SIZE)
                .yBand(0, ProvingGround.HEIGHT, 16)
                .build();
        live.publishAll(ground.voxels());
        live.step();
        for (int f = 0; f < 2; f++) {
            StartSlot slot = ground.startSlots().get(f);
            live.spawn(new FactionSlot(f), new Vec2d(slot.x() + 0.5, slot.z() + 0.5), SOAK_PER_FACTION, 10);
        }
        SoakOrders orders = new SoakOrders(Seeds.first("soak"), ground);
        SimHarness restored = null;
        for (int t = 0; t < SOAK_TICKS; t++) {
            if (t == SOAK_TICKS / 2) {
                MatchSnapshot snapshot = roundTrip(live.core().snapshot());
                Map<Long, byte[]> slices = new TreeMap<>();
                for (var page : snapshot.pages()) {
                    slices.put(
                            page.slice(),
                            ground.voxels()
                                    .slice(new PageCoord(page.x(), page.z()))
                                    .encode());
                }
                restored = SimHarness.restore(snapshot, TemplateSet.builtin(), slices::get);
                assertThat(restored.stateHash()).isEqualTo(live.stateHash());
            }
            Move move = orders.next(live.tick() + 1, live.queries());
            if (move != null) {
                live.submit(PlayerSlot.ADMIN, move);
                if (restored != null) {
                    restored.submit(PlayerSlot.ADMIN, move);
                }
            }
            live.step();
            if (restored != null) {
                restored.step();
                assertThat(restored.stateHash())
                        .withFailMessage("soak: the restored match diverged at tick %d", live.tick())
                        .isEqualTo(live.stateHash());
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
}
