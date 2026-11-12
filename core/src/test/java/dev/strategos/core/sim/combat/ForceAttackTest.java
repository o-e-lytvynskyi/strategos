package dev.strategos.core.sim.combat;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import org.junit.jupiter.api.Test;

/**
 * ORD-05 force attack in L1: an Attack with the flag may target the issuer's own units and buildings, which wear down
 * as an enemy's would; without it the same command is refused, and Gaia's animals are refused even with it.
 */
class ForceAttackTest {
    private static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    private static final TemplateId SHEEP = new TemplateId("strategos:unit/sheep");
    private static final PlayerSlot P0 = new PlayerSlot(0);

    private final SimHarness h =
            SimHarness.builder().size(64, 64).yBand(0, 31, 1).build();
    private final MatchState s;

    ForceAttackTest() {
        h.publishAll(VoxelFixtures.flat(64, 64, 32, 1));
        h.step();
        s = KernelAccess.state(h.core());
    }

    private int[] units(TemplateId template, int faction, Vec2d at, int count) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(template, new FactionSlot(faction), at, count, 1.5));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
    }

    private int house(int faction, int x, int z) {
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(HOUSE, new FactionSlot(faction), x, z, 0));
        h.step();
        int[] handles = s.buildings().handles();
        return handles[handles.length - 1];
    }

    private CommandResult send(Attack attack) {
        h.submit(P0, attack);
        h.step();
        return h.results().get(h.results().size() - 1);
    }

    private static Attack attack(int[] units, int target, boolean force) {
        return new Attack(UnitSet.of(units), target, QueueMode.REPLACE, force);
    }

    @Test
    void ownSpearmenBurnTheirOwnHouseOnlyWhenForced() {
        int house = house(0, 30, 30);
        int[] spearmen = units(TemplateSet.INFANTRY, 0, new Vec2d(24, 24), 3);

        CommandResult plain = send(attack(spearmen, house, false));
        assertThat(plain.accepted()).isZero();
        assertThat(plain.firstReason()).isEqualTo(RejectReason.INVALID_ARGUMENT);

        assertThat(send(attack(spearmen, house, true)).accepted()).isEqualTo(3);
        assertThat(s.orders().kind(Handle.index(spearmen[0]))).isEqualTo(OrderKind.BURN);
        assertThat(h.runUntil(() -> s.buildings().damage(house) > 0, 300))
                .as("the house is hurt")
                .isTrue();
    }

    @Test
    void forcedAttackOnAnOwnUnitIsAcceptedAndHurtsIt() {
        int[] spearmen = units(TemplateSet.INFANTRY, 0, new Vec2d(24, 24), 2);
        int[] friend = units(TemplateSet.INFANTRY, 0, new Vec2d(26, 24), 1);

        CommandResult plain = send(attack(spearmen, friend[0], false));
        assertThat(plain.firstReason()).isEqualTo(RejectReason.INVALID_ARGUMENT);

        assertThat(send(attack(spearmen, friend[0], true)).accepted()).isEqualTo(2);
        assertThat(s.orders().target(Handle.index(spearmen[0]))).isEqualTo(friend[0]);
        assertThat(h.runUntil(() -> s.health().damage(Handle.index(friend[0])) > 0, 300))
                .as("the friend is hurt")
                .isTrue();
        assertThat(send(attack(friend, friend[0], true)).accepted())
                .as("never itself")
                .isZero();
    }

    @Test
    void aForcedAttackNeverTakesGaiasAnimals() {
        int[] spearmen = units(TemplateSet.INFANTRY, 0, new Vec2d(24, 24), 1);
        int sheep = units(SHEEP, s.gaia(), new Vec2d(30.5, 24.5), 1)[0];

        CommandResult forced = send(attack(spearmen, sheep, true));
        assertThat(forced.accepted()).isZero();
        assertThat(forced.firstReason()).isEqualTo(RejectReason.INVALID_ARGUMENT);
        assertThat(s.orders().kind(Handle.index(spearmen[0]))).isNotEqualTo(OrderKind.ATTACK);

        assertThat(send(attack(spearmen, sheep, false)).accepted())
                .as("unforced, as before")
                .isEqualTo(1);
    }

    @Test
    void anEnemysHouseBurnsForcedOrNot() {
        int house = house(1, 30, 30);
        int[] spearmen = units(TemplateSet.INFANTRY, 0, new Vec2d(24, 24), 1);
        assertThat(send(attack(spearmen, house, true)).accepted()).isEqualTo(1);
        assertThat(s.orders().kind(Handle.index(spearmen[0]))).isEqualTo(OrderKind.BURN);
    }
}
