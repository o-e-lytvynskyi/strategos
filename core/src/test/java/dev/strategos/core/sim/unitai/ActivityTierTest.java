package dev.strategos.core.sim.unitai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitHurt;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * UNIT-05 in L1: the tier of every state with and without a recent order (1); a unit that stood idle for long answers
 * an order and a blow within a tick (2); and its hits land on the same ticks whether its target had stood idle for
 * minutes or had just been ordered (3).
 */
class ActivityTierTest {
    private static final PlayerSlot P0 = new PlayerSlot(0);

    @Test
    void theTableOfStatesAndOrders() {
        assertThat(ActivityTier.of(UnitState.IDLE, false)).isEqualTo(ActivityTier.IDLE);
        assertThat(ActivityTier.of(UnitState.WALKING, false)).isEqualTo(ActivityTier.MOVING);
        assertThat(ActivityTier.of(UnitState.GATHERING, false)).isEqualTo(ActivityTier.WORKING);
        assertThat(ActivityTier.of(UnitState.BUILDING, false)).isEqualTo(ActivityTier.WORKING);
        assertThat(ActivityTier.of(UnitState.COMBAT, false)).isEqualTo(ActivityTier.ACTIVE);
        for (UnitState s : UnitState.values()) {
            assertThat(ActivityTier.of(s, true)).as("%s ordered", s).isEqualTo(ActivityTier.ACTIVE);
        }
        assertThat(ActivityTier.ACTIVE.period()).isLessThan(ActivityTier.MOVING.period());
        assertThat(ActivityTier.MOVING.period()).isLessThan(ActivityTier.WORKING.period());
        assertThat(ActivityTier.WORKING.period()).isLessThan(ActivityTier.IDLE.period());
    }

    @Test
    void anOrderKeepsAUnitActiveForFortyTicksThenItWalks() {
        SimHarness h = SimHarness.builder().size(64, 64).build(); // room to walk past the order's 40 ticks
        int u = spawn(h, TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(8.5, 8.5));
        MatchQueries q = h.core().queries();
        assertThat(q.tier(u)).isEqualTo(ActivityTier.IDLE);
        h.submit(P0, Move.replace(UnitSet.of(u), new Vec2d(40.5, 40.5)));
        h.step();
        assertThat(q.tier(u)).isEqualTo(ActivityTier.ACTIVE);
        for (int k = 0; k < ActivityTier.ORDERED; k++) {
            h.step();
        }
        assertThat(q.tier(u)).as("walking, its order 41 ticks old").isEqualTo(ActivityTier.MOVING);
        assertThat(q.tierCounts()[ActivityTier.MOVING.code()]).isEqualTo(1);
    }

    @Test
    void aLongIdleUnitAnswersAnOrderAndABlowWithinATick() {
        SimHarness h = SimHarness.builder().build();
        int idle = spawn(h, TemplateSet.INFANTRY, new FactionSlot(0), new Vec2d(10.5, 10.5));
        for (int k = 0; k < 400; k++) {
            h.step();
        }
        assertThat(h.core().queries().tier(idle)).isEqualTo(ActivityTier.IDLE);
        h.submit(P0, Move.replace(UnitSet.of(idle), new Vec2d(12.5, 10.5)));
        h.step();
        assertThat(h.core().queries().unit(idle).state()).as("ordered").isEqualTo(UnitState.WALKING);
        for (int k = 0; k < 200; k++) {
            h.step(); // arrived and idle again
        }
        int enemy = spawn(h, TemplateSet.INFANTRY, new FactionSlot(1), new Vec2d(30.5, 10.5));
        h.submit(new PlayerSlot(1), Attack.replace(UnitSet.of(enemy), idle));
        long hurtAt = -1;
        for (int k = 0; k < 600 && hurtAt < 0; k++) {
            TickOutputs out = h.step();
            for (OutputEvent e : out.events()) {
                if (e instanceof UnitHurt hurt && hurt.handle() == idle) {
                    hurtAt = out.tick();
                }
            }
        }
        assertThat(hurtAt).as("struck").isPositive();
        h.step();
        assertThat(h.core().queries().unit(idle).state())
                .as("a tick after the blow")
                .isNotEqualTo(UnitState.IDLE);
    }

    @Test
    void hitsLandOnTheSameTicksOnALongIdleTargetAndAJustOrderedOne() {
        assertThat(hitTicks(false)).isEqualTo(hitTicks(true)).isNotEmpty();
    }

    /** The ticks a spearman's hits land on a debug walker, which stood idle for 400 ticks or was just ordered to. */
    private static List<Long> hitTicks(boolean ordered) {
        SimHarness h = SimHarness.builder().build();
        int target = spawn(h, TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(20.5, 10.5));
        for (int k = 0; k < 400; k++) {
            h.step();
        }
        if (ordered) {
            h.submit(new PlayerSlot(1), Move.replace(UnitSet.of(target), new Vec2d(20.5, 10.5))); // where it stands
            h.step();
        } else {
            h.step();
        }
        int attacker = spawn(h, TemplateSet.INFANTRY, new FactionSlot(0), new Vec2d(18.5, 10.5));
        h.submit(P0, Attack.replace(UnitSet.of(attacker), target));
        List<Long> at = new ArrayList<>();
        long start = h.core().queries().tick();
        for (int k = 0; k < 200; k++) {
            TickOutputs out = h.step();
            for (OutputEvent e : out.events()) {
                if (e instanceof UnitHurt hurt && hurt.attacker() == attacker) {
                    at.add(out.tick() - start);
                }
            }
        }
        return at;
    }

    private static int spawn(SimHarness h, TemplateId template, FactionSlot faction, Vec2d at) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(template, faction, at, 1, 0));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .findFirst()
                .orElseThrow();
    }
}
