package dev.strategos.core.sim.garrison;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.AllClear;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Garrison;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.RingBell;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.BellRung;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * BLD-16's bell in L1: ringing puts a garrison order before the orders of every own worker in reach, to the nearest
 * holder with room; the all clear takes those orders out and lets the called units out, each back to the order that
 * waited behind; units garrisoned by a command of their own stay.
 */
class BellTest {
    static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    static final TemplateId HORSEMAN = new TemplateId("strategos:unit/horseman");
    static final PlayerSlot P0 = new PlayerSlot(0);

    final SimHarness h =
            SimHarness.builder().size(160, 64).yBand(0, 31, 1).factions(3).build();
    final MatchState s;

    BellTest() {
        h.publishAll(VoxelFixtures.flat(160, 64, 32, 1));
        h.step();
        s = KernelAccess.state(h.core());
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

    CommandResult send(Command command) {
        int seq = h.submit(P0, command);
        h.step();
        return h.results().stream()
                .filter(r -> r.clientSeq() == seq)
                .findFirst()
                .orElseThrow();
    }

    boolean inside(int unit) {
        return s.garrison().inside(Handle.index(unit));
    }

    boolean allInside(int[] units) {
        for (int u : units) {
            if (!inside(u)) {
                return false;
            }
        }
        return true;
    }

    @Test
    void theBellPutsTheGarrisonOrderBeforeTheCurrentOneAndTheWorkersGoIn() {
        int centre = building(CENTRE, 0, 80, 30);
        int[] workers = units(TemplateSet.WORKER, 0, 60.5, 50.5, 4);
        send(new Move(UnitSet.of(workers), new Vec2d(20.5, 60.5), QueueMode.REPLACE, 0));
        h.runTicks(3);

        CommandResult rung = send(new RingBell(centre));

        assertThat(rung.outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(rung.accepted()).isEqualTo(4);
        for (int w : workers) {
            int i = Handle.index(w);
            assertThat(s.orders().size(i))
                    .as("the garrison order and the move behind it")
                    .isEqualTo(2);
            assertThat(s.orders().kind(i)).isEqualTo(OrderKind.GARRISON);
            assertThat(s.orders().slot(i)).as("marked as the bell's").isEqualTo(Garrisons.BELL_SLOT);
            assertThat(s.orders().target(i)).isEqualTo(centre);
            assertThat(s.orders().order(i, 1).kind()).isEqualTo(OrderKind.MOVE);
        }
        assertThat(h.runUntil(() -> allInside(workers), 400)).isTrue();
        for (int w : workers) {
            int i = Handle.index(w);
            assertThat(s.garrison().belled(i)).isTrue();
            assertThat(s.ai().state(i)).isEqualTo(UnitState.GARRISONED);
            assertThat(s.orders().kind(i)).as("the move waits behind").isEqualTo(OrderKind.MOVE);
        }
    }

    @Test
    void theBellSoundsOnceAtTheBuilding() {
        int centre = building(CENTRE, 0, 80, 30);
        send(new RingBell(centre));
        List<BellRung> heard = h.events(BellRung.class);
        assertThat(heard).hasSize(1);
        assertThat(heard.get(0).x()).isBetween(80, 90);
        assertThat(heard.get(0).z()).isBetween(30, 40);
    }

    @Test
    void onlyOwnWorkersInReachAreCalled() {
        int centre = building(CENTRE, 0, 80, 30);
        int near = units(TemplateSet.WORKER, 0, 60.5, 50.5, 1)[0];
        int far = units(TemplateSet.WORKER, 0, 150.5, 60.5, 1)[0]; // beyond 64 blocks of the middle
        int soldier = units(TemplateSet.INFANTRY, 0, 40.5, 50.5, 1)[0];
        int horseman = units(HORSEMAN, 0, 40.5, 56.5, 1)[0];
        int enemy = units(TemplateSet.WORKER, 1, 95.5, 55.5, 1)[0];
        int plain = units(TemplateSet.WORKER, 0, 68.5, 50.5, 1)[0];
        int inside = units(TemplateSet.WORKER, 0, 84.5, 42.5, 1)[0];
        send(new Garrison(UnitSet.of(inside), centre));
        assertThat(h.runUntil(() -> inside(inside), 200)).isTrue();
        send(new Garrison(UnitSet.of(plain), centre));

        CommandResult rung = send(new RingBell(centre));

        assertThat(rung.accepted()).as("only the near worker").isEqualTo(1);
        assertThat(s.orders().slot(Handle.index(near))).isEqualTo(Garrisons.BELL_SLOT);
        for (int u : new int[] {far, soldier, horseman, enemy}) {
            assertThat(s.orders().size(Handle.index(u))).as("untouched").isZero();
        }
        assertThat(s.orders().slot(Handle.index(plain)))
                .as("already walking in on its own order")
                .isZero();
        assertThat(s.orders().size(Handle.index(plain))).isEqualTo(1);
        assertThat(s.orders().size(Handle.index(inside))).as("already inside").isZero();
    }

    @Test
    void aWorkerGoesToTheNearestHolderWithRoomCountingThoseOnTheirWay() {
        int west = building(CENTRE, 0, 20, 30);
        int east = building(CENTRE, 0, 120, 30);
        int[] crowd = units(TemplateSet.WORKER, 0, 36.5, 56.5, 21);
        int lone = units(TemplateSet.WORKER, 0, 85.5, 40.5, 1)[0]; // in the west bell's reach, nearer the east centre

        assertThat(send(new RingBell(west)).accepted()).isEqualTo(22);

        int toWest = 0;
        for (int w : crowd) {
            toWest += s.orders().target(Handle.index(w)) == west ? 1 : 0;
        }
        assertThat(toWest).as("twenty places in the nearer").isEqualTo(20);
        assertThat(s.orders().target(Handle.index(lone)))
                .as("the one nearer the other")
                .isEqualTo(east);
        int spill = 0;
        for (int w : crowd) {
            spill += s.orders().target(Handle.index(w)) == east ? 1 : 0;
        }
        assertThat(spill).as("the twenty-first goes on to the other").isEqualTo(1);
    }

    @Test
    void aWorkerWithAFullQueueIsRefusedAndTheRestGoIn() {
        int centre = building(CENTRE, 0, 80, 30);
        int[] workers = units(TemplateSet.WORKER, 0, 60.5, 50.5, 2);
        for (int k = 0; k < 16; k++) {
            send(new Move(UnitSet.of(workers[0]), new Vec2d(20.5 + k, 60.5), QueueMode.QUEUE, 0));
        }

        CommandResult rung = send(new RingBell(centre));

        assertThat(rung.outcome()).isEqualTo(CommandOutcome.PARTIAL);
        assertThat(rung.accepted()).isEqualTo(1);
        assertThat(rung.rejected()).isEqualTo(1);
        assertThat(s.orders().kind(Handle.index(workers[0]))).isEqualTo(OrderKind.MOVE);
        assertThat(s.orders().kind(Handle.index(workers[1]))).isEqualTo(OrderKind.GARRISON);
    }

    @Test
    void allClearLetsTheCalledOutAndEachResumesItsOrder() {
        int centre = building(CENTRE, 0, 80, 30);
        int[] workers = units(TemplateSet.WORKER, 0, 60.5, 50.5, 3);
        int idle = units(TemplateSet.WORKER, 0, 62.5, 52.5, 1)[0];
        send(new Move(UnitSet.of(workers), new Vec2d(20.5, 60.5), QueueMode.REPLACE, 0));
        send(new RingBell(centre));
        int[] all = {workers[0], workers[1], workers[2], idle};
        assertThat(h.runUntil(() -> allInside(all), 400)).isTrue();

        CommandResult clear = send(new AllClear(centre));

        assertThat(clear.outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(clear.accepted()).isEqualTo(4);
        assertThat(s.garrison().empty()).isTrue();
        for (int w : workers) {
            int i = Handle.index(w);
            assertThat(s.orders().kind(i)).isEqualTo(OrderKind.MOVE);
            assertThat(s.ai().state(i)).as("back to its walk").isEqualTo(UnitState.WALKING);
        }
        assertThat(s.ai().state(Handle.index(idle))).as("nothing to resume").isEqualTo(UnitState.IDLE);
        assertThat(h.runUntil(() -> s.orders().size(Handle.index(workers[0])) == 0, 800))
                .as("and arrives where it was going")
                .isTrue();
        assertThat(Math.hypot(
                        s.motion().x(Handle.index(workers[0])) - 20.5,
                        s.motion().z(Handle.index(workers[0])) - 60.5))
                .isLessThan(3);
    }

    @Test
    void allClearDropsTheOrderOfAWorkerStillWalkingIn() {
        int centre = building(CENTRE, 0, 80, 30);
        int[] workers = units(TemplateSet.WORKER, 0, 50.5, 56.5, 2);
        int idle = units(TemplateSet.WORKER, 0, 52.5, 56.5, 1)[0];
        send(new Move(UnitSet.of(workers), new Vec2d(20.5, 60.5), QueueMode.REPLACE, 0));
        send(new RingBell(centre));
        h.runTicks(4);
        assertThat(s.garrison().empty()).as("still on the way").isTrue();

        send(new AllClear(centre));

        for (int w : workers) {
            int i = Handle.index(w);
            assertThat(s.orders().size(i)).isEqualTo(1);
            assertThat(s.orders().kind(i)).isEqualTo(OrderKind.MOVE);
            assertThat(s.ai().state(i)).isEqualTo(UnitState.WALKING);
        }
        assertThat(s.orders().size(Handle.index(idle))).isZero();
        assertThat(s.ai().state(Handle.index(idle))).isEqualTo(UnitState.IDLE);
        h.runTicks(300);
        assertThat(s.garrison().empty()).as("nobody went in after all").isTrue();
    }

    @Test
    void allClearTakesABellOrderFromBehindAnotherToo() {
        int centre = building(CENTRE, 0, 80, 30);
        int worker = units(TemplateSet.WORKER, 0, 50.5, 56.5, 1)[0];
        int i = Handle.index(worker);
        send(new Move(UnitSet.of(worker), new Vec2d(20.5, 60.5), QueueMode.REPLACE, 0));
        send(new RingBell(centre));
        s.orders().pushFront(i, OrderKind.FLEE, 40.5, 40.5, 0, 0); // a flight comes before the walk in
        assertThat(s.orders().size(i)).isEqualTo(3);

        send(new AllClear(centre));

        assertThat(s.orders().size(i)).isEqualTo(2);
        assertThat(s.orders().kind(i)).as("the flight carries on").isEqualTo(OrderKind.FLEE);
        assertThat(s.orders().order(i, 1).kind()).isEqualTo(OrderKind.MOVE);
    }

    @Test
    void allClearLeavesUnitsGarrisonedByACommandOfTheirOwn() {
        int centre = building(CENTRE, 0, 80, 30);
        int[] workers = units(TemplateSet.WORKER, 0, 60.5, 50.5, 2);
        int rung = units(TemplateSet.WORKER, 0, 62.5, 50.5, 1)[0];
        send(new Garrison(UnitSet.of(workers), centre));
        assertThat(h.runUntil(() -> allInside(workers), 400)).isTrue();
        send(new RingBell(centre));
        assertThat(h.runUntil(() -> inside(rung), 400)).isTrue();
        assertThat(s.garrison().belled(Handle.index(workers[0]))).isFalse();

        send(new AllClear(centre));

        assertThat(inside(rung)).isFalse();
        assertThat(inside(workers[0]) && inside(workers[1])).as("they stay").isTrue();
        assertThat(s.garrison().count(centre)).isEqualTo(2);
    }

    @Test
    void theRulesRefuseWhatCannotRing() {
        int centre = building(CENTRE, 0, 80, 30);
        int foreign = building(CENTRE, 1, 120, 30);
        int unfinished = building(CENTRE, 0, 20, 30);
        s.buildings().setState(unfinished, BuildingStore.CONSTRUCTING, 0);
        int house = building(HOUSE, 0, 80, 70);

        for (Command[] pair : new Command[][] {
            {new RingBell(foreign), new AllClear(foreign)},
            {new RingBell(unfinished), new AllClear(unfinished)},
            {new RingBell(house), new AllClear(house)},
            {new RingBell(0x7FFFF), new AllClear(0x7FFFF)}
        }) {
            RejectReason want = pair[0] instanceof RingBell r && r.building() == foreign
                    ? RejectReason.NOT_OWNER
                    : pair[0] instanceof RingBell r2 && r2.building() == 0x7FFFF
                            ? RejectReason.UNKNOWN_TARGET
                            : RejectReason.UNSUPPORTED;
            for (Command c : pair) {
                CommandResult result = send(c);
                assertThat(result.outcome()).isEqualTo(CommandOutcome.REJECTED);
                assertThat(result.firstReason()).as(c.toString()).isEqualTo(want);
            }
        }
        assertThat(h.events(BellRung.class)).as("no bell sounded").isEmpty();
        assertThat(send(new RingBell(centre)).outcome())
                .as("a ring that finds no worker is still a ring")
                .isEqualTo(CommandOutcome.ACCEPTED);
    }
}
