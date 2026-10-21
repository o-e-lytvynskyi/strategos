package dev.strategos.core.sim.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.testkit.Seeds;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpawnUnitsRuleTest {
    private static final FactionSlot FACTION_0 = new FactionSlot(0);

    private static CommandResult spawn(SimHarness h, PlayerSlot issuer, SpawnUnits spawn) {
        int seq = h.submit(issuer, spawn);
        h.step();
        return h.results().stream()
                .filter(r -> r.clientSeq() == seq)
                .findFirst()
                .orElseThrow();
    }

    private static SpawnUnits walkers(Vec2d center, int count, double spread) {
        return new SpawnUnits(TemplateSet.DEBUG_WALKER, FACTION_0, center, count, spread);
    }

    @Test
    void onlyAdminMaySpawn() {
        SimHarness h = SimHarness.builder().build();
        CommandResult result = spawn(h, new PlayerSlot(0), walkers(new Vec2d(8, 8), 2, 1));
        assertThat(result.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(result.firstReason()).isEqualTo(RejectReason.NOT_PERMITTED);
        assertThat(h.queries().unitCount()).isZero();
    }

    @Test
    void refusesWhatTheRulesForbid() {
        SimHarness h = SimHarness.builder().rules(new MatchRules(256, 5)).build();
        record Case(SpawnUnits spawn, RejectReason reason) {}
        for (Case c : List.of(
                new Case(
                        new SpawnUnits(new TemplateId("strategos:unit/nobody"), FACTION_0, new Vec2d(8, 8), 1, 0),
                        RejectReason.UNKNOWN_TEMPLATE),
                new Case(walkers(new Vec2d(8, 8), 0, 0), RejectReason.INVALID_ARGUMENT),
                new Case(walkers(new Vec2d(8, 8), 301, 0), RejectReason.INVALID_ARGUMENT),
                new Case(walkers(new Vec2d(8, 8), 1, 65), RejectReason.INVALID_ARGUMENT),
                new Case(
                        new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(5), new Vec2d(8, 8), 1, 0),
                        RejectReason.INVALID_ARGUMENT),
                new Case(walkers(new Vec2d(32, 8), 1, 0), RejectReason.OUT_OF_BOUNDS),
                new Case(walkers(new Vec2d(8, -0.5), 1, 0), RejectReason.OUT_OF_BOUNDS),
                new Case(walkers(new Vec2d(8, 8), 6, 0), RejectReason.UNIT_CAP))) {
            CommandResult result = spawn(h, PlayerSlot.ADMIN, c.spawn());
            assertThat(result.outcome()).as("%s", c).isEqualTo(CommandOutcome.REJECTED);
            assertThat(result.firstReason()).as("%s", c).isEqualTo(c.reason());
        }
        assertThat(h.queries().unitCount()).isZero();
        assertThat(spawn(h, PlayerSlot.ADMIN, walkers(new Vec2d(8, 8), 5, 0)).outcome())
                .as("exactly the cap")
                .isEqualTo(CommandOutcome.ACCEPTED);
    }

    @Test
    void theUnitCapCountsTheUnitsAlreadyAlive() {
        SimHarness h = SimHarness.builder().rules(new MatchRules(256, 5)).build();
        assertThat(spawn(h, PlayerSlot.ADMIN, walkers(new Vec2d(8, 8), 3, 0)).outcome())
                .isEqualTo(CommandOutcome.ACCEPTED);

        CommandResult over = spawn(h, PlayerSlot.ADMIN, walkers(new Vec2d(8, 8), 3, 0));

        assertThat(over.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(over.firstReason()).isEqualTo(RejectReason.UNIT_CAP);
        assertThat(h.queries().unitCount()).isEqualTo(3);
        assertThat(spawn(h, PlayerSlot.ADMIN, walkers(new Vec2d(8, 8), 2, 0)).outcome())
                .as("up to the cap")
                .isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(h.queries().unitCount()).isEqualTo(5);
    }

    @Test
    void placesUnitsAtTheirGoldenPositionsOnTheFloor() {
        // A floor at 4, apart from minY and the default 1, so a unit stored at the wrong height shows.
        SimHarness h = SimHarness.builder()
                .seed(Seeds.first("golden-spawn"))
                .yBand(0, 16, 4.0)
                .build();
        CommandResult result = spawn(h, PlayerSlot.ADMIN, walkers(new Vec2d(16, 16), 4, 3.0));
        assertThat(result.outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(result.accepted()).isEqualTo(4);

        List<UnitCreated> created = h.events(UnitCreated.class);
        assertThat(created).hasSize(4);
        // A regression pin, computed once from the spawn formula (M0 spec 5.7) with seed 7, at tick 1, key ADMIN/1.
        double[][] golden = GoldenSpawns.SEED_7;
        for (int i = 0; i < created.size(); i++) {
            UnitCreated u = created.get(i);
            UnitView stored = h.queries().unit(u.handle());
            assertThat(stored).as("unit %d", i).isNotNull();
            assertThat(new double[] {stored.x(), stored.z()}).as("unit %d", i).containsExactly(golden[i]);
            assertThat(stored.y()).isEqualTo(4.0);
            assertThat(stored.yaw()).isZero();
            assertThat(stored.state()).isEqualTo(UnitState.IDLE);
            assertThat(stored.target()).isNull();
            assertThat(new double[] {u.x(), u.y(), u.z(), u.yaw()})
                    .as("the event announces the stored pose")
                    .containsExactly(stored.x(), stored.y(), stored.z(), stored.yaw());
            assertThat(Math.hypot(u.x() - 16, u.z() - 16)).isLessThanOrEqualTo(3.0);
        }
    }

    @Test
    void keepsUnitsHalfABlockInsideTheBounds() {
        SimHarness h = SimHarness.builder().build();
        spawn(h, PlayerSlot.ADMIN, walkers(new Vec2d(0.1, 31.9), 50, 10.0));
        for (UnitCreated u : h.events(UnitCreated.class)) {
            UnitView stored = h.queries().unit(u.handle());
            assertThat(stored).isNotNull();
            for (double v : new double[] {u.x(), u.z(), stored.x(), stored.z()}) {
                assertThat(v).isBetween(0.5, 31.5);
            }
        }
    }
}
