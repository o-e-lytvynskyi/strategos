package dev.strategos.core.sim.unitai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.balance.BalanceSheet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.stats.Modifier;
import dev.strategos.core.sim.stats.StatService;
import dev.strategos.core.testkit.BalanceData;
import dev.strategos.core.testkit.SimHarness;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * UNIT-04 in L1: who gathers and builds follows the class tokens in data and through the modifier pipeline (D-02:
 * workers do, the bundled spearman does not); a worker hit while idle runs from the blow and stands again, a spearman
 * hits back; both templates are on the balance sheet.
 */
class WorkerModelTest {
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final FactionSlot F1 = new FactionSlot(1);
    private static final int SOURCE = 41;

    private static int spawn(SimHarness h, TemplateId template, FactionSlot faction, Vec2d at) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(template, faction, at, 1, 0));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .findFirst()
                .orElseThrow();
    }

    private static MatchState state(SimHarness h) {
        return KernelAccess.state(h.core());
    }

    @Test
    void gatheringAndBuildingFollowClassTokens() {
        SimHarness h = SimHarness.builder().build();
        int worker = Handle.index(spawn(h, TemplateSet.WORKER, F0, new Vec2d(10.5, 10.5)));
        int spearman = Handle.index(spawn(h, TemplateSet.INFANTRY, F0, new Vec2d(14.5, 10.5)));
        int enemy = Handle.index(spawn(h, TemplateSet.INFANTRY, F1, new Vec2d(30.5, 30.5)));
        StatService stats = state(h).stats();

        assertThat(stats.canGather(worker)).isTrue();
        assertThat(stats.canBuild(worker)).isTrue();
        assertThat(stats.canGather(spearman)).isFalse();
        assertThat(stats.canBuild(spearman)).isFalse();

        // citizen-soldiers through data: a faction's modifier gives its spearmen the token, and they gather (D-02)
        stats.add(F0, Modifier.tokens("Identity/classes", "+Gatherer", SOURCE).withFilter("Spearman"));
        assertThat(stats.canGather(spearman)).isTrue();
        assertThat(stats.canBuild(spearman)).isFalse();
        assertThat(stats.canGather(enemy)).as("the other faction's spearmen").isFalse();

        stats.add(
                F0,
                Modifier.tokens("Identity/classes", "-Gatherer -Builder", SOURCE + 1)
                        .withFilter("Worker"));
        assertThat(stats.canGather(worker)).isFalse();
        assertThat(stats.canBuild(worker)).isFalse();
    }

    @Test
    void aWorkerHitWhileIdleRunsFromTheBlowAndASpearmanHitsBack() {
        SimHarness h = SimHarness.builder().build();
        Vec2d start = new Vec2d(20.5, 20.5);
        int worker = spawn(h, TemplateSet.WORKER, F0, start);
        int raider = spawn(h, TemplateSet.INFANTRY, F1, new Vec2d(18.5, 20.5));
        MatchState s = state(h);
        int w = Handle.index(worker);
        assertThat(s.stats().fleeDistance(w)).isEqualTo(8);
        assertThat(s.stats().maxHealth(w)).isEqualTo(50);

        h.submit(new PlayerSlot(1), Attack.replace(UnitSet.of(raider), worker));
        boolean fled = false;
        for (int t = 0; t < 40 && !fled; t++) {
            h.step();
            fled = s.orders().kind(w) == OrderKind.FLEE;
        }
        assertThat(fled).as("the worker took up a flight after the first blow").isTrue();
        assertThat(s.orders().targetX(w))
                .as("east, away from the raider to its west")
                .isGreaterThan(start.x() + 7);

        for (int t = 0; t < 200 && s.orders().kind(w) == OrderKind.FLEE; t++) {
            h.step();
        }
        assertThat(s.orders().kind(w)).as("there, it stands again").isEqualTo(OrderKind.NONE);
        double ran = DetMath.length(s.motion().x(w) - start.x(), s.motion().z(w) - start.z());
        assertThat(ran).isGreaterThan(6.5);

        SimHarness back = SimHarness.builder().build();
        int defender = spawn(back, TemplateSet.INFANTRY, F0, start);
        int attacker = spawn(back, TemplateSet.INFANTRY, F1, new Vec2d(18.5, 20.5));
        back.submit(new PlayerSlot(1), Attack.replace(UnitSet.of(attacker), defender));
        MatchState b = state(back);
        boolean engaged = false;
        for (int t = 0; t < 40 && !engaged; t++) {
            back.step();
            engaged = b.orders().kind(Handle.index(defender)) == OrderKind.ENGAGE;
        }
        assertThat(engaged).as("a spearman hits back rather than running").isTrue();
    }

    @Test
    void bothTemplatesAreOnTheBalanceSheet() {
        Path data = Path.of(System.getProperty("strategos.repoRoot", "..")).resolve("mod/src/main/resources/data");
        BalanceSheet sheet = BalanceData.read(data).sheet();

        assertThat(sheet.problems()).isEmpty();
        assertThat(sheet.rows())
                .filteredOn(r -> r.template().equals(TemplateSet.WORKER))
                .extracting(BalanceSheet.Row::path)
                .contains("Health/max", "Behaviour/flee", "Cost/resources/food", "Cost/time", "UnitMotion/walkSpeed");
        assertThat(sheet.rows())
                .filteredOn(r -> r.template().equals(TemplateSet.INFANTRY))
                .extracting(BalanceSheet.Row::path)
                .contains("Attack/Melee/hack", "Cost/resources/food", "Cost/resources/wood", "Cost/time");
    }
}
