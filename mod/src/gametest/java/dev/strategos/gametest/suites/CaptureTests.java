package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.handles;
import static dev.strategos.gametest.suites.Units.unit;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Capture;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;

/** CMB-10 on real bodies (M9 spec §2.6): a captured building works for its new owner. */
public final class CaptureTests {
    private static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    private static final TemplateId BARRACKS = new TemplateId("strategos:building/barracks");
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final FactionSlot F1 = new FactionSlot(1);
    private static final PlayerSlot P0 = new PlayerSlot(0);
    private static final int INFANTRY = 20;

    private CaptureTests() {}

    private static BuildingView barracks(MatchRuntime rt) {
        BuildingView found = null;
        for (BuildingView b : core(rt).queries().buildings()) {
            if (b.template().equals(BARRACKS)) {
                found = b;
            }
        }
        check(found != null, "no barracks yet");
        return java.util.Objects.requireNonNull(found);
    }

    /**
     * CMB-10.3: twenty infantry of faction 0 ordered to capture faction 1's undefended barracks take it (800 points
     * against 20 x 2.5 - 10 a second, 20 s and the walk); faction 0 then trains an infantryman there and the new unit
     * is faction 0's.
     */
    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 3_000)
    public static void captured_barracks_trains(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, F0, 2, 2, 0)); // housing for the army and the recruit
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, F0, 2, 20, 0));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(BARRACKS, F1, 32, 32, 0));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, F0, new Vec2d(20, 32), INFANTRY, 1.5));
        for (String resource : List.of("food", "wood", "stone", "iron")) {
            rt.submit(PlayerSlot.ADMIN, new Grant(F0, resource, 1_000));
        }
        var state = new Object() {
            CommandResult order;
            CommandResult train;
            final List<Integer> army = new ArrayList<>();
            int barracks;
        };
        h.startSequence()
                .thenWaitUntil(() -> {
                    check(handles(rt).length == INFANTRY, "the army is not there yet");
                    BuildingView b = barracks(rt);
                    check(b.state() == BuildingStore.COMPLETE, "the barracks is not finished");
                    check(core(rt).queries().buildings().size() == 3, "not every building is there yet");
                })
                .thenExecute(() -> {
                    state.barracks = barracks(rt).handle();
                    for (int handle : handles(rt)) {
                        state.army.add(handle);
                    }
                    rt.submit(
                            P0,
                            new Capture(
                                    UnitSet.of(state.army.stream()
                                            .mapToInt(Integer::intValue)
                                            .toArray()),
                                    state.barracks),
                            r -> state.order = r);
                })
                .thenWaitUntil(() -> check(state.order != null, "no reply to the order yet"))
                .thenExecute(() ->
                        check(state.order.outcome() == CommandOutcome.ACCEPTED, "the capture order: " + state.order))
                .thenWaitUntil(() -> check(barracks(rt).faction() == 0, "the barracks is still faction 1's"))
                .thenExecute(() -> rt.submit(P0, new Train(state.barracks, TemplateSet.INFANTRY), r -> state.train = r))
                .thenWaitUntil(() -> check(state.train != null, "no reply to the training yet"))
                .thenExecute(
                        () -> check(state.train.outcome() == CommandOutcome.ACCEPTED, "the training: " + state.train))
                .thenWaitUntil(() -> check(handles(rt).length == INFANTRY + 1, "no recruit yet"))
                .thenExecute(() -> {
                    int recruits = 0;
                    for (int handle : handles(rt)) {
                        if (!state.army.contains(handle)) {
                            recruits++;
                            check(unit(rt, handle).faction().equals(F0), "the recruit is not faction 0's");
                        }
                    }
                    check(recruits == 1, recruits + " recruits, not 1");
                })
                .thenSucceed();
    }
}
