package dev.strategos.core.sim.garrison;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Garrison;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Ungarrison;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OrderFinished;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * BLD-16 in L1: the civic centre takes twenty workers and infantry in at its door and no more; a unit inside keeps its
 * handle, hit points and the orders behind the garrison order, has no place in the world, and comes out alive when the
 * building falls.
 */
class GarrisonTest {
    static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    static final TemplateId HORSEMAN = new TemplateId("strategos:unit/horseman");
    static final PlayerSlot P0 = new PlayerSlot(0);

    final SimHarness h =
            SimHarness.builder().size(96, 64).yBand(0, 31, 1).factions(3).build();
    final MatchState s;
    final List<OrderFinished> finished = new ArrayList<>();

    GarrisonTest() {
        h.publishAll(VoxelFixtures.flat(96, 64, 32, 1));
        h.step();
        s = KernelAccess.state(h.core());
        s.bus().subscribe(OrderFinished.class, finished::add);
    }

    int building(int faction, int x, int z) {
        return building(CENTRE, faction, x, z);
    }

    int building(TemplateId template, int faction, int x, int z) {
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(template, new FactionSlot(faction), x, z, 0));
        h.step();
        int[] handles = s.buildings().handles();
        return handles[handles.length - 1];
    }

    int[] units(TemplateId template, int faction, double x, double z, int count) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(template, new FactionSlot(faction), new Vec2d(x, z), count, 1.5));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
    }

    /** Workers standing a few blocks from the building's door. */
    int[] workers(int centre, int count) {
        int[] door = Garrisons.door(s, centre);
        return units(TemplateSet.WORKER, 0, door[0] + 0.5, door[1] + 9.5, count);
    }

    CommandResult send(PlayerSlot issuer, dev.strategos.core.protocol.Command command) {
        int seq = h.submit(issuer, command);
        h.step();
        return h.results().stream()
                .filter(r -> r.clientSeq() == seq)
                .findFirst()
                .orElseThrow();
    }

    boolean inside(int unit) {
        return s.garrison().inside(Handle.index(unit));
    }

    @Test
    void capacityAndClasses() {
        int centre = building(0, 40, 30);
        assertThat(Garrisons.capacity(s, centre)).isEqualTo(20);
        int[] workers = workers(centre, 21);
        int[] horseman = units(HORSEMAN, 0, 30, 30, 1);
        int foreign = building(1, 70, 30);
        int unfinished = building(0, 10, 10);
        s.buildings().setState(unfinished, BuildingStore.CONSTRUCTING, 0);

        assertThat(send(P0, new Garrison(UnitSet.of(horseman), centre)).firstReason())
                .as("a horseman is Cavalry, which the centre does not admit")
                .isEqualTo(RejectReason.NOT_PERMITTED);
        assertThat(send(P0, new Garrison(UnitSet.of(workers[0]), foreign)).firstReason())
                .as("another faction's building")
                .isEqualTo(RejectReason.NOT_OWNER);
        assertThat(send(P0, new Garrison(UnitSet.of(workers[0]), unfinished)).firstReason())
                .as("a building still being built")
                .isEqualTo(RejectReason.UNSUPPORTED);
        assertThat(s.garrison().empty()).isTrue();
        assertThat(s.orders().size(Handle.index(workers[0]))).isZero();

        CommandResult all = send(P0, new Garrison(UnitSet.of(workers), centre));
        assertThat(all.outcome())
                .as("the full building is found out on arrival")
                .isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(h.runUntil(
                        () -> s.garrison().count(centre) == 20 && s.orders().kind(lastOut(workers)) == 0, 400))
                .isTrue();
        h.runTicks(40);
        assertThat(s.garrison().count(centre)).as("twenty fit").isEqualTo(20);
        List<Integer> out = new ArrayList<>();
        for (int w : workers) {
            if (!inside(w)) {
                out.add(w);
            } else {
                assertThat(s.ai().state(Handle.index(w))).isEqualTo(UnitState.GARRISONED);
            }
        }
        assertThat(out).as("the twenty-first stays out").hasSize(1);
        assertThat(finished.stream().filter(OrderFinished::failed).map(OrderFinished::handle))
                .as("and its order failed")
                .containsExactly(out.get(0));
        assertThat(s.ai().state(Handle.index(out.get(0)))).isEqualTo(UnitState.IDLE);
    }

    /** The index of a worker that is not inside, or the first one. */
    private int lastOut(int[] workers) {
        for (int w : workers) {
            if (!inside(w)) {
                return Handle.index(w);
            }
        }
        return Handle.index(workers[0]);
    }

    @Test
    void handleHpAndOrdersSurvive() {
        int centre = building(0, 40, 30);
        int worker = workers(centre, 1)[0];
        int i = Handle.index(worker);
        s.health().setDamage(i, 3.5);
        send(P0, new Garrison(UnitSet.of(worker), centre));
        send(P0, new Move(UnitSet.of(worker), new Vec2d(10.5, 50.5), QueueMode.QUEUE, 0));
        send(P0, new Move(UnitSet.of(worker), new Vec2d(12.5, 52.5), QueueMode.QUEUE, 0));
        assertThat(s.orders().size(i)).isEqualTo(3);
        assertThat(s.orders().kind(i)).isEqualTo(OrderKind.GARRISON);

        assertThat(h.runUntil(() -> inside(worker), 200)).isTrue();
        OrderQueues.Saved kept = s.orders().saved(i);
        assertThat(s.orders().size(i))
                .as("the garrison order is done, the two moves wait")
                .isEqualTo(2);
        assertThat(s.orders().kind(i)).isEqualTo(OrderKind.MOVE);
        assertThat(s.entities().handleAt(i)).isEqualTo(worker);
        assertThat(s.health().damage(i)).isEqualTo(3.5);
        double xIn = s.motion().x(i);
        h.runTicks(30);
        assertThat(s.motion().x(i)).as("the queued move does not start inside").isEqualTo(xIn);
        assertThat(s.orders().saved(i)).isEqualTo(kept);

        send(P0, new Ungarrison(centre, UnitSet.of()));
        assertThat(inside(worker)).isFalse();
        assertThat(s.entities().handleAt(i)).as("the same handle").isEqualTo(worker);
        assertThat(s.health().damage(i)).as("the same hit points").isEqualTo(3.5);
        assertThat(s.orders().saved(i)).as("the same orders").isEqualTo(kept);
        assertThat(s.ai().state(i)).as("and it takes up the next one").isEqualTo(UnitState.WALKING);
        assertThat(h.runUntil(() -> s.orders().size(i) == 0, 600)).isTrue();
        assertThat(Math.hypot(s.motion().x(i) - 12.5, s.motion().z(i) - 52.5)).isLessThan(2);
    }

    @Test
    void aUnitInsideHasNoPlaceInTheWorld() {
        int centre = building(0, 40, 30);
        int[] workers = workers(centre, 3);
        send(P0, new Garrison(UnitSet.of(workers), centre));
        assertThat(h.runUntil(() -> s.garrison().count(centre) == 3, 300)).isTrue();
        h.runTicks(2);
        assertThat(s.spatial().units().count()).as("out of the spatial index").isZero();
        int i = Handle.index(workers[0]);
        assertThat(s.spatial()
                        .countWithin(
                                s.motion().x(i), s.motion().z(i), 10, dev.strategos.core.sim.spatial.UnitFilter.ANY))
                .isZero();
        double x = s.motion().x(i);
        double z = s.motion().z(i);

        s.queueDamage(new Damage(workers[0], 0, 0, 50, 0, 0, List.of(), false));
        h.runTicks(2);
        assertThat(s.health().damage(i)).as("nothing reaches it").isZero();
        assertThat(s.motion().x(i)).isEqualTo(x);
        assertThat(s.motion().z(i)).isEqualTo(z);
        assertThat(send(
                                PlayerSlot.ADMIN,
                                new dev.strategos.core.protocol.Attack(
                                        UnitSet.of(units(TemplateSet.INFANTRY, 1, 20, 20, 1)),
                                        workers[0],
                                        QueueMode.REPLACE))
                        .firstReason())
                .as("it cannot be named as a target")
                .isEqualTo(RejectReason.UNKNOWN_TARGET);
        assertThat(java.util.Objects.requireNonNull(h.queries().unit(workers[0]))
                        .state())
                .as("its view says so")
                .isEqualTo(UnitState.GARRISONED);
    }

    @Test
    void theBuildingsFallLetsThemOutAlive() {
        int centre = building(0, 40, 30);
        int[] workers = workers(centre, 5);
        send(P0, new Garrison(UnitSet.of(workers), centre));
        assertThat(h.runUntil(() -> s.garrison().count(centre) == 5, 300)).isTrue();
        s.health().setDamage(Handle.index(workers[2]), 2);

        s.destroyBuilding(centre);
        h.runTicks(2);

        assertThat(s.garrison().empty()).isTrue();
        Set<Long> cells = new HashSet<>();
        for (int w : workers) {
            int i = Handle.index(w);
            assertThat(s.entities().isAlive(w)).isTrue();
            assertThat(s.ai().state(i)).isNotEqualTo(UnitState.GARRISONED);
            assertThat(s.footprintAt((int) s.motion().x(i), (int) s.motion().z(i)))
                    .isFalse();
            cells.add(((long) (int) s.motion().x(i) << 32) | (int) s.motion().z(i));
        }
        assertThat(cells).as("each at its own cell round the door").hasSize(5);
        assertThat(s.health().damage(Handle.index(workers[2]))).isEqualTo(2);
        assertThat(s.spatial().units().count()).isEqualTo(5);
    }

    @Test
    void ungarrisonLetsOutTheNamedInTheOrderTheyWentIn() {
        int centre = building(0, 40, 30);
        int[] workers = workers(centre, 3);
        send(P0, new Garrison(UnitSet.of(workers), centre));
        assertThat(h.runUntil(() -> s.garrison().count(centre) == 3, 300)).isTrue();
        int[] order = s.garrison().occupants(centre);

        CommandResult stranger = send(P0, new Ungarrison(centre, UnitSet.of(units(TemplateSet.WORKER, 0, 20, 20, 1))));
        assertThat(stranger.firstReason()).isEqualTo(RejectReason.INVALID_ARGUMENT);
        CommandResult one = send(P0, new Ungarrison(centre, UnitSet.of(order[1])));
        assertThat(one.outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(s.garrison().occupants(centre)).containsExactly(order[0], order[2]);
        assertThat(send(new PlayerSlot(1), new Ungarrison(centre, UnitSet.of())).firstReason())
                .isEqualTo(RejectReason.NOT_OWNER);
        assertThat(send(P0, new Ungarrison(BuildingStore.BASE + 9, UnitSet.of()))
                        .firstReason())
                .isEqualTo(RejectReason.UNKNOWN_TARGET);
    }

    @Test
    void theRulesRefuseWhatCannotGarrison() {
        int centre = building(0, 40, 30);
        int[] workers = workers(centre, 2);
        assertThat(send(P0, new Garrison(UnitSet.of(), centre)).firstReason()).isEqualTo(RejectReason.INVALID_ARGUMENT);
        assertThat(send(P0, new Garrison(UnitSet.of(workers), BuildingStore.BASE + 9))
                        .firstReason())
                .as("no such building")
                .isEqualTo(RejectReason.UNKNOWN_TARGET);
        assertThat(send(new PlayerSlot(1), new Garrison(UnitSet.of(workers), centre))
                        .firstReason())
                .as("not the issuer's building")
                .isEqualTo(RejectReason.NOT_OWNER);
        int house = building(new TemplateId("strategos:building/house"), 0, 10, 10);
        CommandResult notHolder = send(P0, new Garrison(UnitSet.of(workers), house));
        assertThat(notHolder.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(notHolder.firstReason()).as("a building that holds nobody").isEqualTo(RejectReason.UNSUPPORTED);
        int enemy = units(TemplateSet.WORKER, 1, 20, 20, 1)[0];
        assertThat(send(PlayerSlot.ADMIN, new Garrison(UnitSet.of(enemy), centre))
                        .firstReason())
                .as("another faction's unit")
                .isEqualTo(RejectReason.NOT_OWNER);
        assertThat(s.garrison().empty()).isTrue();

        send(P0, new Garrison(UnitSet.of(workers[0]), centre));
        assertThat(h.runUntil(() -> inside(workers[0]), 300)).isTrue();
        CommandResult again = send(P0, new Garrison(UnitSet.of(workers), centre));
        assertThat(again.outcome())
                .as("the one inside is refused, the other goes")
                .isEqualTo(CommandOutcome.PARTIAL);
        assertThat(again.firstReason()).isEqualTo(RejectReason.INVALID_ARGUMENT);
        assertThat(s.garrison().count(centre)).isEqualTo(1);
    }

    @Test
    void aUnitThatDiesInsideLeavesNoRow() {
        int centre = building(0, 40, 30);
        int[] workers = workers(centre, 2);
        send(P0, new Garrison(UnitSet.of(workers), centre));
        assertThat(h.runUntil(() -> s.garrison().count(centre) == 2, 300)).isTrue();

        s.queueDamage(new Damage(workers[0], 0, 0, 0, 0, 0, List.of(), true));
        h.runTicks(1);

        assertThat(s.entities().isAlive(workers[0])).isFalse();
        assertThat(s.garrison().occupants(centre)).containsExactly(workers[1]);
    }

    @Test
    void aUnitInsideAddsAnArrowToTheCentre() {
        int centre = building(0, 40, 30);
        int[] workers = workers(centre, 3);
        assertThat(dev.strategos.core.sim.combat.TownArrowsAccess.garrisoned(s, centre))
                .isZero();
        send(P0, new Garrison(UnitSet.of(workers), centre));
        assertThat(h.runUntil(() -> s.garrison().count(centre) == 3, 300)).isTrue();
        assertThat(dev.strategos.core.sim.combat.TownArrowsAccess.garrisoned(s, centre))
                .as("the seam town arrows count")
                .isEqualTo(3);
    }

    private double[] where(int[] units) {
        double[] out = new double[units.length * 2];
        for (int k = 0; k < units.length; k++) {
            out[2 * k] = s.motion().x(Handle.index(units[k]));
            out[2 * k + 1] = s.motion().z(Handle.index(units[k]));
        }
        return out;
    }

    @Test
    void unitsLetOutWithNothingToDoStayWhereTheyAreLetOut() {
        int centre = building(0, 40, 30);
        int[] workers = workers(centre, 3);
        send(P0, new Garrison(UnitSet.of(workers), centre));
        assertThat(h.runUntil(() -> s.garrison().count(centre) == 3, 300)).isTrue();
        send(P0, new Ungarrison(centre, UnitSet.of()));
        double[] out = where(workers);
        h.runTicks(30);
        assertThat(where(workers))
                .as("no arrival latch walks them back to the door")
                .isEqualTo(out);

        send(P0, new Garrison(UnitSet.of(workers), centre));
        assertThat(h.runUntil(() -> s.garrison().count(centre) == 3, 300)).isTrue();
        s.queueDamage(new Damage(centre, 0, 0, 0, 0, 0, List.of(), true));
        h.step();
        assertThat(s.garrison().empty()).isTrue();
        double[] fallen = where(workers);
        h.runTicks(30);
        assertThat(where(workers)).as("nor back to the rubble").isEqualTo(fallen);
    }

    @Test
    void aBuildingLostOnTheWayEndsTheOrderAsFailedOrDone() {
        int centre = building(0, 40, 30);
        int worker = workers(centre, 1)[0];
        send(P0, new Garrison(UnitSet.of(worker), centre));
        h.runTicks(2);
        assertThat(inside(worker)).isFalse();
        s.destroyBuilding(centre);
        assertThat(h.runUntil(() -> s.orders().size(Handle.index(worker)) == 0, 40))
                .isTrue();
        assertThat(inside(worker)).isFalse();
        assertThat(finished).extracting(OrderFinished::handle).contains(worker);
        assertThat(s.ai().state(Handle.index(worker))).isEqualTo(UnitState.IDLE);
    }

    @Test
    void withNoFreeGroundTheyAreLetOutWhereTheyStand() {
        int centre = building(0, 40, 30);
        int[] workers = workers(centre, 2);
        send(P0, new Garrison(UnitSet.of(workers), centre));
        assertThat(h.runUntil(() -> s.garrison().count(centre) == 2, 300)).isTrue();
        dev.strategos.core.content.Footprint at = s.footprintOf(centre);
        for (int x = 0; x < 96; x++) {
            for (int z = 0; z < 64; z++) {
                boolean in = x >= at.anchorX()
                        && x < at.anchorX() + at.spanX()
                        && z >= at.anchorZ()
                        && z < at.anchorZ() + at.spanZ();
                if (!in) {
                    s.restoreColumn(x, z);
                }
            }
        }
        double[] before = where(workers);
        send(P0, new Ungarrison(centre, UnitSet.of()));
        assertThat(s.garrison().empty()).isTrue();
        for (int w : workers) {
            assertThat(s.entities().isAlive(w)).isTrue();
            assertThat(s.ai().state(Handle.index(w))).isEqualTo(UnitState.IDLE);
        }
        assertThat(where(workers)).isEqualTo(before);
    }

    @Test
    void nothingThatLooksForTargetsFindsAUnitInside() {
        int enemyCentre = building(1, 40, 40);
        int hidden = units(TemplateSet.WORKER, 1, 40, 36, 1)[0];
        int exposed = units(TemplateSet.WORKER, 1, 30, 48, 1)[0];
        int ours = building(0, 40, 52); // its arrows reach both workers' places
        int spearman = units(TemplateSet.INFANTRY, 0, 30, 44, 1)[0];
        List<dev.strategos.core.sim.ShotRequested> shots = new ArrayList<>();
        s.bus().subscribe(dev.strategos.core.sim.ShotRequested.class, shots::add);
        send(PlayerSlot.ADMIN, new Garrison(UnitSet.of(hidden), enemyCentre));
        assertThat(h.runUntil(() -> inside(hidden), 300)).isTrue();
        shots.clear();
        for (int t = 0; t < 200; t++) {
            h.step();
            assertThat(s.orders().target(Handle.index(spearman)))
                    .as("never aims at it")
                    .isNotEqualTo(hidden);
            for (int i = 0; i < s.entities().highWater(); i++) {
                s.health().setDamage(i, 0); // nobody dies, so the volleys go on
            }
        }
        assertThat(shots).as("the centre does shoot at the one outside").anyMatch(r -> r.target() == exposed);
        assertThat(shots).as("and never at the one inside").noneMatch(r -> r.target() == hidden);
        assertThat(send(
                                PlayerSlot.ADMIN,
                                new dev.strategos.core.protocol.AttackSpread(
                                        UnitSet.of(spearman), hidden, QueueMode.REPLACE))
                        .firstReason())
                .isEqualTo(RejectReason.UNKNOWN_TARGET);
        assertThat(ours).isNotZero();
    }
}
