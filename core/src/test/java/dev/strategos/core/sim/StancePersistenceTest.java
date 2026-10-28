package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.RawTemplate;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.SetStance;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Stance;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.Seeds;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Stances are saved and hashed only once a match uses them, so every earlier save and journal keeps its bytes. */
class StancePersistenceTest {
    private static MatchSnapshot roundTrip(MatchSnapshot snapshot) {
        return MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, MatchSnapshotCodec.encode(JsonOps.INSTANCE, snapshot)),
                        Migrations.production())
                .getOrThrow();
    }

    @Test
    void aMatchWithoutStancesSavesAndHashesAsBefore() {
        SimHarness h = SimHarness.builder().seed(Seeds.first("canary")).build();
        h.spawn(new FactionSlot(0), new Vec2d(10.5, 10.5), 3, 2);
        h.runTicks(5);
        JsonObject json =
                MatchSnapshotCodec.encode(JsonOps.INSTANCE, h.core().snapshot()).getAsJsonObject();
        assertThat(json.has("play"))
                .as("no play block while nothing in it is set")
                .isFalse();
        assertThat(h.core().snapshot().play().stances()).isEmpty();
        long hash = h.stateHash();
        KernelAccess.state(h.core()).stances(); // reading is not using
        assertThat(h.stateHash()).isEqualTo(hash);
    }

    @Test
    void anExplicitStanceRoundTripsAndResumesWithTheSameHashes() {
        SimHarness h = SimHarness.builder().seed(Seeds.first("canary")).build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(10.5, 10.5), 3, 2);
        long before = h.stateHash();
        h.submit(new PlayerSlot(0), new SetStance(UnitSet.of(units[0], units[2]), Stance.DEFENSIVE));
        h.submit(new PlayerSlot(0), new SetStance(UnitSet.of(units[1]), Stance.PASSIVE));
        h.runTicks(3);
        assertThat(h.stateHash()).isNotEqualTo(before);

        MatchSnapshot saved = roundTrip(h.core().snapshot());
        assertThat(saved.play().stances()).hasSize(3);
        MatchCore resumed = MatchCore.restore(saved, TemplateSet.builtin(), CorePorts.of(() -> 0L));
        assertThat(resumed.stateHash()).isEqualTo(h.stateHash());
        MatchState back = KernelAccess.state(resumed);
        assertThat(back.stances().explicit(Handle.index(units[1]))).isEqualTo(Stance.PASSIVE);
        assertThat(back.stances().explicit(Handle.index(units[0]))).isEqualTo(Stance.DEFENSIVE);
        assertThat(back.stances().spotX(Handle.index(units[0])))
                .isEqualTo(KernelAccess.state(h.core()).stances().spotX(Handle.index(units[0])));
        for (int i = 0; i < 4; i++) {
            long tick = h.tick() + 1;
            h.core().step(new TickInputs(tick, List.of()));
            resumed.step(new TickInputs(tick, List.of()));
            assertThat(resumed.stateHash()).isEqualTo(h.stateHash());
        }
    }

    @Test
    void aDefensiveTemplateKeepsItsSpotThroughASaveAndResumesWithEqualHashes() {
        TemplateId guard = new TemplateId("strategos:unit/guard");
        JsonObject json = JsonParser.parseString("""
                {"Identity":{},"UnitMotion":{"walkSpeed":0.25},"Behaviour":{"stance":"defensive"},
                 "Body":{"entityType":"strategos:unit_humanoid","width":0.6,"height":1.8},
                 "Attack":{"Melee":{"hack":1}}}
                """).getAsJsonObject();
        TemplateSet templates = TemplateCompiler.compile(List.of(new RawTemplate(guard, "guard.json", json)))
                .set();
        SimHarness h = SimHarness.builder()
                .seed(Seeds.first("canary"))
                .templates(templates)
                .build();
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(guard, new FactionSlot(0), new Vec2d(10.5, 10.5), 1, 0));
        h.runTicks(25); // its idle think takes the spot it stands on
        MatchSnapshot saved = roundTrip(h.core().snapshot());
        assertThat(saved.play().stances()).hasSize(1);
        assertThat(saved.play().stances().get(0).stance())
                .as("no explicit stance")
                .isEqualTo(-1);
        MatchCore resumed = MatchCore.restore(saved, templates, CorePorts.of(() -> 0L));
        assertThat(resumed.stateHash()).isEqualTo(h.stateHash());
        for (int i = 0; i < 4; i++) {
            long tick = h.tick() + 1;
            h.core().step(new TickInputs(tick, List.of()));
            resumed.step(new TickInputs(tick, List.of()));
            assertThat(resumed.stateHash()).isEqualTo(h.stateHash());
        }
    }
}
