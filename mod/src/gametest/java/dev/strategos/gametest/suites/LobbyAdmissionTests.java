package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.DiplomacySetup;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRefusedException;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;

/** Runtime authority installation and seat admission; these are not lobby networking/start acceptance. */
public final class LobbyAdmissionTests {
    private LobbyAdmissionTests() {}

    private static DiplomacySetup coop() {
        DiplomacyState authority = new DiplomacyState(2);
        for (int player = 0; player < 4; player++) {
            authority = authority.bind(new PlayerSlot(player), new FactionSlot(player / 2));
        }
        return new DiplomacySetup(authority.lockTeams().snapshot());
    }

    @StrategosTest(timeoutTicks = 40)
    public static void fourPlayersCanOccupyTwoEconomyFactions(GameTestHelper h) {
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        DiplomacySetup setup = coop();
        rt.initializeDiplomacy(setup);
        h.startSequence()
                .thenExecuteAfter(1, () -> {
                    var core = core(rt);
                    check(core.queries().diplomacy().teamsLocked(), "setup was not installed before admission");
                    for (int player = 0; player < 4; player++) {
                        UUID member = new UUID(7, player + 1);
                        StrategosServer.get().matches().join(rt, member, new PlayerSlot(player));
                        var seat = StrategosServer.get().matches().seatOf(member);
                        check(
                                seat != null && seat.slot().value() == player,
                                "player seat was confused with faction capacity");
                        check(
                                core.queries()
                                                .diplomacy()
                                                .factionOf(new PlayerSlot(player))
                                                .orElseThrow()
                                                .value()
                                        == player / 2,
                                "wrong co-op faction");
                    }
                    check(rt.roster().members().size() == 4, "not all four players admitted");
                })
                .thenSucceed();
    }

    @StrategosTest(timeoutTicks = 40)
    public static void runtimeSetupIsSingleUseAndCannotReplaceAuthorityAfterTheFirstTick(GameTestHelper h) {
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        rt.initializeDiplomacy(coop());
        boolean duplicate = false;
        try {
            rt.initializeDiplomacy(coop());
        } catch (IllegalStateException expected) {
            duplicate = true;
        }
        check(duplicate, "a second queued setup replaced authority");
        h.startSequence()
                .thenExecuteAfter(1, () -> {
                    boolean late = false;
                    try {
                        rt.initializeDiplomacy(coop());
                    } catch (IllegalStateException expected) {
                        late = true;
                    }
                    check(late, "late setup was accepted");
                })
                .thenSucceed();
    }

    @StrategosTest(timeoutTicks = 40)
    public static void legacyIdentityStillRejectsUnboundSlots(GameTestHelper h) {
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        UUID member = new UUID(7, 101);
        boolean refused = false;
        try {
            StrategosServer.get().matches().join(rt, member, new PlayerSlot(2));
        } catch (MatchRefusedException expected) {
            refused = true;
        }
        check(refused, "unbound legacy player seat was admitted");
        check(StrategosServer.get().matches().seatOf(member) == null, "refusal left a partial seat");
        h.succeed();
    }
}
