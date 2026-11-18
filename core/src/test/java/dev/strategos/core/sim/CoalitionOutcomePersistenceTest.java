package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoalitionOutcomePersistenceTest {
    private static SimHarness terminal(List<Integer> winners) {
        SimHarness h = SimHarness.builder().build();
        Outcome outcome = KernelAccess.state(h.core()).outcome();
        outcome.hold(0);
        outcome.hold(1);
        outcome.end(winners, 0);
        h.step();
        return h;
    }

    private static JsonObject json(SimHarness h) {
        return MatchSnapshotCodec.encode(JsonOps.INSTANCE, h.core().snapshot()).getAsJsonObject();
    }

    private static MatchSnapshot decode(JsonObject json) {
        return MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, json), Migrations.production())
                .getOrThrow();
    }

    @Test
    void schemaFiveIndividualAndDrawMigrateWithoutChangingReleasedHashes() {
        for (List<Integer> winners : List.of(List.of(0), List.<Integer>of())) {
            SimHarness h = terminal(winners);
            JsonObject legacy = json(h);
            legacy.addProperty("v", 5);
            legacy.getAsJsonObject("play").getAsJsonArray("outcome").remove(5);
            MatchSnapshot migrated = decode(legacy);
            assertThat(migrated.play().outcome()).hasSize(6);
            SimHarness restored =
                    SimHarness.restore(migrated, KernelAccess.state(h.core()).templates());
            assertThat(restored.stateHash()).isEqualTo(h.stateHash());
            assertThat(KernelAccess.state(restored.core()).outcome().winners()).isEqualTo(winners);
        }
    }

    @Test
    void currentMissingInconsistentOutOfRangeOrNonterminalWinnerMasksFailClosed() {
        JsonObject good = json(terminal(List.of(0, 1)));
        for (int problem = 0; problem < 6; problem++) {
            JsonObject bad = good.deepCopy();
            JsonArray outcome = bad.getAsJsonObject("play").getAsJsonArray("outcome");
            switch (problem) {
                case 0 -> outcome.remove(5);
                case 1 -> outcome.set(3, new com.google.gson.JsonPrimitive(1));
                case 2 -> outcome.set(5, new com.google.gson.JsonPrimitive(1 << 2));
                case 3 -> outcome.set(2, new com.google.gson.JsonPrimitive(0));
                case 4 -> outcome.set(1, new com.google.gson.JsonPrimitive(1));
                case 5 -> outcome.set(3, new com.google.gson.JsonPrimitive(1L << 32));
                default -> throw new AssertionError();
            }
            assertThat(MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, bad), Migrations.production())
                            .error())
                    .as("malformed terminal result %s", problem)
                    .isPresent();
        }
    }

    @Test
    void legacyFiveCannotSmuggleANewCoalitionAndEndingTwiceCannotReplaceTheResult() {
        SimHarness h = terminal(List.of(0, 1));
        JsonObject bad = json(h);
        bad.addProperty("v", 5);
        assertThat(MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, bad), Migrations.production())
                        .error())
                .isPresent();
        Outcome outcome = KernelAccess.state(h.core()).outcome();
        outcome.end(List.of(), 100);
        assertThat(outcome.winners()).containsExactly(0, 1);
        assertThat(outcome.endedAt()).isZero();
    }
}
