package dev.strategos.core.sim.unitai;

import static dev.strategos.core.sim.unitai.UnitTreeTransitionsTest.workerRow;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.BusMessage;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.Stuck;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.Placing;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.unitai.UnitTreeTransitionsTest.Unit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

/**
 * UNIT-02.1 for torches (CMB-03): the BURN states' rows of the unit tree, run by {@link UnitTreeTransitionsTest} with its
 * worker, which here carries a torch so that one unit can be in every subtree; the enemy's finished house stands at
 * (25, 12).
 */
final class BurnTransitionRows {
    private BurnTransitionRows() {}

    /** The built-in templates whose worker carries a torch, at the same net indices. */
    static TemplateSet torchWorkers() {
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : TemplateSet.builtin().all()) {
            var json = Objects.requireNonNull(TemplateSet.builtin().flattened(t.id()));
            if (t.id().equals(TemplateSet.WORKER)) {
                json.add(
                        "Attack",
                        JsonParser.parseString("{\"Structure\": {\"fire\": 1, \"prepare\": 10, \"repeat\": 20}}")
                                .getAsJsonObject());
            }
            flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), json));
        }
        TemplateCompiler.Result result = TemplateCompiler.fromFlattened(
                flats,
                TemplateSet.builtin().techs().stream()
                        .map(TemplateCompiler.TechFlat::of)
                        .toList());
        assertThat(result.problems()).isEmpty();
        return result.set();
    }

    /** The enemy's house, finished. */
    static void setUp(Unit u) {
        MatchState s = u.state;
        int template = Objects.requireNonNull(s.templates().byId(BuildTransitionRows.HOUSE))
                .netIndex();
        Blueprint b = Objects.requireNonNull(s.templates().blueprint(template));
        u.enemyHouse = s.buildings()
                .create(template, 1, Footprint.of(b, 25, 12, 0), 0, BuildingStore.COMPLETE, BuildingStore.FULL);
    }

    /** The unit in {@code from}: its burn order and place; burning, standing on it. */
    static void start(Unit u, StateId from) {
        MatchState s = u.state;
        int i = u.index();
        int[] ring = Placing.slots(s, s.footprintOf(u.enemyHouse));
        int cell = ring[0];
        s.orders().replace(i, OrderKind.BURN, (cell >>> 16) + 0.5, (cell & 0xFFFF) + 0.5, 0, 1, u.enemyHouse);
        s.ai().set(i, from);
        if (from.equals(UnitAi.BURNING)) {
            u.place(u.handle, (cell >>> 16) + 0.5, (cell & 0xFFFF) + 0.5);
        }
    }

    /** An attack on the house, as the attack rule turns it into a burn order, then announced. */
    static BusMessage newBurn(Unit u) {
        u.state.orders().replace(u.index(), OrderKind.BURN, 27.5, 14.5, 0, 0, u.enemyHouse);
        return new OrderReceived(u.handle);
    }

    private static Consumer<Unit> burning() {
        return u -> {
            assertThat(u.state.orders().kind(u.index())).isEqualTo(OrderKind.BURN);
            assertThat(u.state.orders().target(u.index())).isEqualTo(u.enemyHouse);
            assertThat(u.state.orders().slot(u.index()))
                    .as("a place round the house")
                    .isPositive();
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
        StateId toHouse = UnitAi.BURN_APPROACHING;
        StateId burning = UnitAi.BURNING;
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
                        UnitAi.GATHER_RETURNING,
                        toSite,
                        UnitAi.BUILDING)
                .map(from -> workerRow(
                        "a burn order from " + from + " walks to a place",
                        from,
                        BurnTransitionRows::newBurn,
                        toHouse,
                        burning()));
        List<Arguments> out = new ArrayList<>();
        out.add(workerRow(
                "arriving at the place burns",
                toHouse,
                u -> {
                    u.place(
                            u.handle,
                            u.state.orders().targetX(u.index()),
                            u.state.orders().targetZ(u.index()));
                    return new Arrived(u.handle);
                },
                burning,
                burning()));
        out.add(workerRow(
                "arriving short of the place walks on", toHouse, u -> new Arrived(u.handle), toHouse, burning()));
        out.add(workerRow(
                "a house the burner cannot reach fails the order",
                toHouse,
                u -> new Stuck(u.handle, Stuck.GIVE_UP),
                idle,
                UnitTreeTransitionsTest.orderFinished(true)));
        out.add(workerRow(
                "a burnt house ends the order (a tick)",
                burning,
                u -> {
                    u.state.destroyBuilding(u.enemyHouse);
                    return null;
                },
                idle,
                UnitTreeTransitionsTest.orderFinished(false)));
        out.add(workerRow(
                "a new burn order while burning walks to a place again",
                burning,
                BurnTransitionRows::newBurn,
                toHouse,
                burning()));
        for (StateId from : List.of(toHouse, burning)) {
            String at = from.equals(toHouse) ? "on the way to the house" : "while burning";
            out.add(workerRow(
                    "a stop " + at + " idles",
                    from,
                    UnitTreeTransitionsTest::stop,
                    idle,
                    UnitTreeTransitionsTest::nothingElse));
            out.add(workerRow(
                    "a move " + at,
                    from,
                    UnitTreeTransitionsTest::newMove,
                    walking,
                    UnitTreeTransitionsTest.kept(OrderKind.MOVE)));
            out.add(workerRow(
                    "an attack " + at,
                    from,
                    UnitTreeTransitionsTest::newAttack,
                    approaching,
                    UnitTreeTransitionsTest.kept(OrderKind.ATTACK)));
            out.add(workerRow(
                    "an attack-move " + at,
                    from,
                    UnitTreeTransitionsTest::newAttackMove,
                    fighting,
                    UnitTreeTransitionsTest.kept(OrderKind.ATTACK_MOVE)));
            out.add(workerRow(
                    "a follow " + at,
                    from,
                    UnitTreeTransitionsTest::newFollow,
                    following,
                    UnitTreeTransitionsTest.kept(OrderKind.FOLLOW)));
            out.add(workerRow(
                    "a gather order " + at,
                    from,
                    GatherTransitionRows::newGather,
                    toNode,
                    UnitTreeTransitionsTest.kept(OrderKind.GATHER)));
            out.add(workerRow(
                    "a build order " + at,
                    from,
                    BuildTransitionRows::newBuild,
                    toSite,
                    UnitTreeTransitionsTest.kept(OrderKind.BUILD)));
        }
        return Stream.concat(into, out.stream());
    }

    /** JUnit needs the class to hold a test to load it with the others; the rows run in the unit tree's. */
    @Test
    void theRowsAreTheUnitTreesOwn() {
        assertThat(rows().count()).isEqualTo(32);
    }
}
