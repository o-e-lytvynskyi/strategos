package dev.strategos.core.sim.unitai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.hud.IdleWorkers;
import dev.strategos.core.client.select.UnitRole;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Guard;
import dev.strategos.core.protocol.Patrol;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UnitSummary;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.replicate.ReplicationService;
import dev.strategos.core.sim.replicate.Visibility;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * UI-17 in L1: the unit summary tells the owner which units hold a patrol or a guard (a guard standing beside its
 * charge reports idle), nobody else, and a guard whose charge died holds none and is idle for the idle-soldier key.
 */
class StandingReplicationTest {
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final FactionSlot F1 = new FactionSlot(1);
    private static final PlayerSlot P0 = new PlayerSlot(0);
    private static final TemplateSet T = TemplateSet.builtin();

    private static int[] spawn(SimHarness h, FactionSlot faction, Vec2d at, int count) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, faction, at, count, 1));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
    }

    private static UnitSummary.Entry entry(UnitSummary s, int handle) {
        return s.units().stream()
                .filter(e -> e.index() == Handle.index(handle))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void aGuardStandingBesideItsChargeIsIdleYetHoldsAStandingOrder() {
        SimHarness h = SimHarness.builder().size(120, 32).factions(2).build();
        int charge = spawn(h, F0, new Vec2d(30, 16), 1)[0];
        int guard = spawn(h, F0, new Vec2d(26, 16), 1)[0];
        int free = spawn(h, F0, new Vec2d(20, 16), 1)[0];
        int patroller = spawn(h, F0, new Vec2d(10, 16), 1)[0];
        h.submit(P0, Guard.replace(UnitSet.of(guard), charge));
        h.submit(P0, Patrol.replace(UnitSet.of(patroller), List.of(new Vec2d(10, 16), new Vec2d(10, 24))));
        h.runTicks(120);

        UnitSummary own = ReplicationService.summary(h.queries(), 1, F0, Visibility.ALL);
        assertThat(entry(own, guard).state()).as("standing beside its charge").isEqualTo(IdleWorkers.IDLE);
        assertThat(entry(own, guard).standing()).isTrue();
        assertThat(entry(own, patroller).standing()).isTrue();
        assertThat(entry(own, charge).standing()).isFalse();
        assertThat(entry(own, free).standing()).isFalse();
        assertThat(IdleWorkers.idle(own.units(), 0, T, UnitRole.SOLDIER))
                .extracting(UnitSummary.Entry::index)
                .as("the guard is not idle")
                .doesNotContain(Handle.index(guard))
                .contains(Handle.index(free));

        UnitSummary rival = ReplicationService.summary(h.queries(), 1, F1, Visibility.ALL);
        assertThat(rival.units()).as("another faction's view").noneMatch(UnitSummary.Entry::standing);
        UnitSummary observer = ReplicationService.summary(h.queries(), 1, null, Visibility.ALL);
        assertThat(observer.units()).noneMatch(UnitSummary.Entry::standing);
    }

    @Test
    void aGuardWhoseChargeDiedHoldsNoneAndIsIdleForTheKey() {
        SimHarness h = SimHarness.builder().size(120, 32).factions(2).build();
        int charge = spawn(h, F0, new Vec2d(30, 16), 1)[0];
        int guard = spawn(h, F0, new Vec2d(26, 16), 1)[0];
        h.submit(P0, Guard.replace(UnitSet.of(guard), charge));
        h.runTicks(80);
        assertThat(entry(ReplicationService.summary(h.queries(), 1, F0, Visibility.ALL), guard)
                        .standing())
                .isTrue();

        h.damage(new ExternalDamage(charge, ExternalDamage.Kind.LETHAL, 1_000, ExternalDamage.SourceKind.NONE));
        h.runTicks(30);

        UnitSummary after = ReplicationService.summary(h.queries(), 1, F0, Visibility.ALL);
        assertThat(entry(after, guard).standing()).isFalse();
        IdleWorkers key = new IdleWorkers(UnitRole.SOLDIER);
        assertThat(key.next(after.units(), 0, T))
                .isNotNull()
                .extracting(UnitSummary.Entry::index)
                .isEqualTo(Handle.index(guard));
    }
}
