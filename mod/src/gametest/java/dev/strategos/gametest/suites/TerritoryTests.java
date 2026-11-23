package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.server.match.MatchRuntime;
import net.minecraft.gametest.framework.GameTestHelper;

/** TERR-02 in L3 (M9 spec §2.2): a placement outside its faction's territory is refused, and the issuer told why. */
public final class TerritoryTests {
    private static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    private static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    private static final PlayerSlot P0 = new PlayerSlot(0);

    private TerritoryTests() {}

    /**
     * TERR-02.4: on a 48-block arena faction 0's centre stands in one corner and faction 1's in the other; a house by
     * faction 0's centre is accepted, one by faction 1's is refused as outside faction 0's territory.
     */
    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 200)
    public static void refusedOutside(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(0), 2, 2, 0));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(1), 36, 36, 0));
        rt.submit(PlayerSlot.ADMIN, new dev.strategos.core.protocol.Grant(new FactionSlot(0), "wood", 1_000));
        var state = new Object() {
            CommandResult inside;
            CommandResult outside;
        };
        h.startSequence()
                .thenWaitUntil(() -> check(
                        core(rt).territory() != null
                                && core(rt).queries().buildings().size() == 2,
                        "no territory yet"))
                .thenExecute(() -> {
                    rt.submit(
                            P0,
                            new PlaceBuilding(HOUSE, 16, 4, 0, UnitSet.of(), QueueMode.REPLACE),
                            r -> state.inside = r);
                    rt.submit(
                            P0,
                            new PlaceBuilding(HOUSE, 30, 40, 0, UnitSet.of(), QueueMode.REPLACE),
                            r -> state.outside = r);
                })
                .thenWaitUntil(() -> check(state.inside != null && state.outside != null, "no replies yet"))
                .thenExecute(() -> {
                    check(state.inside.outcome() == CommandOutcome.ACCEPTED, "inside: " + state.inside);
                    check(state.outside.outcome() == CommandOutcome.REJECTED, "outside taken: " + state.outside);
                    check(
                            state.outside.firstReason() == RejectReason.OUTSIDE_TERRITORY,
                            "refused as " + state.outside.firstReason());
                })
                .thenSucceed();
    }
}
