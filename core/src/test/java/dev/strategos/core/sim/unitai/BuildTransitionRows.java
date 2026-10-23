package dev.strategos.core.sim.unitai;

import static dev.strategos.core.sim.unitai.UnitTreeTransitionsTest.workerRow;
import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.BusMessage;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.Stuck;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.Construction;
import dev.strategos.core.sim.build.Placing;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.unitai.UnitTreeTransitionsTest.Unit;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

/**
 * UNIT-02.1 for construction (BLD-03): the BUILD states' rows of the unit tree, run by {@link UnitTreeTransitionsTest}
 * with its worker; the faction's house is a foundation at (20, 20), its builder slots round it.
 */
final class BuildTransitionRows {
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");

    private BuildTransitionRows() {}

    /** The house's foundation. */
    static void setUp(Unit u) {
        MatchState s = u.state;
        int template = Objects.requireNonNull(s.templates().byId(HOUSE)).netIndex();
        Blueprint b = Objects.requireNonNull(s.templates().blueprint(template));
        Footprint at = Footprint.of(b, 20, 20, 0);
        u.site = s.buildings().create(template, 0, at, 0, BuildingStore.FOUNDATION, 0);
        s.buildings().setSlots(u.site, Placing.slots(s, at));
    }

    /** The unit in {@code from}: its build order and slot; at work, standing on the slot of a committed site. */
    static void start(Unit u, StateId from) {
        MatchState s = u.state;
        int i = u.index();
        int slot = s.buildings().claimNearest(u.site, u.handle, 4, 8, cell -> true);
        s.carry().setSite(i, u.site);
        int cell = s.buildings().slot(u.site, slot);
        s.orders().replace(i, OrderKind.BUILD, (cell >>> 16) + 0.5, (cell & 0xFFFF) + 0.5, 0, slot, u.site);
        s.ai().set(i, from);
        if (from.equals(UnitAi.BUILDING)) {
            u.place(u.handle, (cell >>> 16) + 0.5, (cell & 0xFFFF) + 0.5);
            Construction.commit(s, u.site);
        }
    }

    /** A build order on the house, set as the placement rule sets it, then announced. */
    static BusMessage newBuild(Unit u) {
        u.state.orders().replace(u.index(), OrderKind.BUILD, 22.5, 22.5, 0, 0, u.site);
        return new OrderReceived(u.handle);
    }

    private static Consumer<Unit> building() {
        return u -> {
            assertThat(u.state.orders().kind(u.index())).isEqualTo(OrderKind.BUILD);
            assertThat(u.state.orders().target(u.index())).isEqualTo(u.site);
            assertThat(u.state.carry().site(u.index())).isEqualTo(u.site);
        };
    }

    private static Consumer<Unit> slotFreed(Consumer<Unit> then) {
        return u -> {
            assertThat(u.state.carry().site(u.index())).isZero();
            for (int k = 0; k < u.state.buildings().slotCount(u.site); k++) {
                assertThat(u.state.buildings().claim(u.site, k)).isZero();
            }
            then.accept(u);
        };
    }

    static Stream<Arguments> rows() {
        StateId idle = UnitAi.IDLE;
        StateId walking = UnitAi.WALKING;
        StateId fighting = UnitAi.WALKING_AND_FIGHTING;
        StateId approaching = UnitAi.APPROACHING;
        StateId following = UnitAi.FOLLOW_WALKING;
        StateId toNode = UnitAi.GATHER_APPROACHING;
        StateId toSite = UnitAi.BUILD_APPROACHING;
        StateId building = UnitAi.BUILDING;
        Stream<Arguments> into = Stream.of(
                        idle,
                        walking,
                        fighting,
                        approaching,
                        UnitAi.ATTACKING,
                        UnitAi.CHASING,
                        following,
                        UnitAi.FOLLOW_WAITING,
                        toNode,
                        UnitAi.GATHERING,
                        UnitAi.GATHER_RETURNING)
                .map(from -> workerRow(
                        "a build order from " + from + " walks to a slot",
                        from,
                        BuildTransitionRows::newBuild,
                        toSite,
                        building()));
        Stream<Arguments> out = Stream.of(
                workerRow(
                        "arriving at the slot builds and commits the foundation",
                        toSite,
                        u -> {
                            u.place(
                                    u.handle,
                                    u.state.orders().targetX(u.index()),
                                    u.state.orders().targetZ(u.index()));
                            return new Arrived(u.handle);
                        },
                        building,
                        u -> {
                            building().accept(u);
                            assertThat(u.state.buildings().state(u.site)).isEqualTo(BuildingStore.CONSTRUCTING);
                            assertThat(u.state.footprintAt(22, 22))
                                    .as("its columns block units")
                                    .isTrue();
                        }),
                workerRow("arriving short of the slot walks on", toSite, u -> new Arrived(u.handle), toSite, u -> {
                    building().accept(u);
                    assertThat(u.state.buildings().state(u.site)).isEqualTo(BuildingStore.FOUNDATION);
                }),
                workerRow(
                        "a stop on the way to the site idles",
                        toSite,
                        UnitTreeTransitionsTest::stop,
                        idle,
                        slotFreed(UnitTreeTransitionsTest::nothingElse)),
                workerRow(
                        "a move on the way to the site",
                        toSite,
                        UnitTreeTransitionsTest::newMove,
                        walking,
                        slotFreed(UnitTreeTransitionsTest.kept(OrderKind.MOVE))),
                workerRow(
                        "an attack on the way to the site",
                        toSite,
                        UnitTreeTransitionsTest::newAttack,
                        approaching,
                        slotFreed(UnitTreeTransitionsTest.kept(OrderKind.ATTACK))),
                workerRow(
                        "an attack-move on the way to the site",
                        toSite,
                        UnitTreeTransitionsTest::newAttackMove,
                        fighting,
                        slotFreed(UnitTreeTransitionsTest.kept(OrderKind.ATTACK_MOVE))),
                workerRow(
                        "a follow on the way to the site",
                        toSite,
                        UnitTreeTransitionsTest::newFollow,
                        following,
                        slotFreed(UnitTreeTransitionsTest.kept(OrderKind.FOLLOW))),
                workerRow(
                        "a gather order on the way to the site",
                        toSite,
                        GatherTransitionRows::newGather,
                        toNode,
                        slotFreed(UnitTreeTransitionsTest.kept(OrderKind.GATHER))),
                workerRow(
                        "a site the builder cannot reach fails the order",
                        toSite,
                        u -> new Stuck(u.handle, Stuck.GIVE_UP),
                        idle,
                        slotFreed(UnitTreeTransitionsTest.orderFinished(true))),
                workerRow(
                        "a finished site ends the order (a tick)",
                        building,
                        u -> {
                            u.state.buildings().setState(u.site, BuildingStore.COMPLETE, BuildingStore.FULL);
                            return null;
                        },
                        idle,
                        slotFreed(UnitTreeTransitionsTest.orderFinished(false))),
                workerRow(
                        "a stop while building idles",
                        building,
                        UnitTreeTransitionsTest::stop,
                        idle,
                        slotFreed(UnitTreeTransitionsTest::nothingElse)),
                workerRow(
                        "a move while building",
                        building,
                        UnitTreeTransitionsTest::newMove,
                        walking,
                        slotFreed(UnitTreeTransitionsTest.kept(OrderKind.MOVE))),
                workerRow(
                        "an attack while building",
                        building,
                        UnitTreeTransitionsTest::newAttack,
                        approaching,
                        slotFreed(UnitTreeTransitionsTest.kept(OrderKind.ATTACK))),
                workerRow(
                        "an attack-move while building",
                        building,
                        UnitTreeTransitionsTest::newAttackMove,
                        fighting,
                        slotFreed(UnitTreeTransitionsTest.kept(OrderKind.ATTACK_MOVE))),
                workerRow(
                        "a follow while building",
                        building,
                        UnitTreeTransitionsTest::newFollow,
                        following,
                        slotFreed(UnitTreeTransitionsTest.kept(OrderKind.FOLLOW))),
                workerRow(
                        "a gather order while building",
                        building,
                        GatherTransitionRows::newGather,
                        toNode,
                        slotFreed(UnitTreeTransitionsTest.kept(OrderKind.GATHER))),
                workerRow(
                        "a new build order while building walks to a slot again",
                        building,
                        BuildTransitionRows::newBuild,
                        toSite,
                        building()));
        return Stream.concat(into, out);
    }

    /** JUnit needs the class to hold a test to load it with the others; the rows run in the unit tree's. */
    @Test
    void theRowsAreTheUnitTreesOwn() {
        assertThat(rows().count()).isEqualTo(28);
    }
}
