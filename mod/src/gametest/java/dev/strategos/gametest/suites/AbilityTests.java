package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.handles;

import dev.strategos.core.content.CorePaths;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Target;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UseAbility;
import dev.strategos.core.shared.AbilityId;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.stats.StatBreakdown;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.server.match.MatchRuntime;
import net.minecraft.gametest.framework.GameTestHelper;

/** UNIT-10 on the shipped data (M10 spec §2.7): the horseman's {@code charge}, used through a real command. */
public final class AbilityTests {
    private static final TemplateId HORSEMAN = new TemplateId("strategos:unit/horseman");
    private static final PlayerSlot P0 = new PlayerSlot(0);
    private static final int COOLDOWN = 600;
    private static final int DURATION = 100;

    private AbilityTests() {}

    private static double stat(MatchRuntime rt, int handle, String path) {
        StatBreakdown b = core(rt).queries().stats(handle, path);
        check(b != null, "no " + path + " for the horseman");
        return java.util.Objects.requireNonNull(b).result();
    }

    private static void near(double actual, double expected, String what) {
        check(Math.abs(actual - expected) < 1e-6, what + " is " + actual + ", not " + expected);
    }

    /**
     * A horseman uses {@code charge} through a {@code UseAbility} command: its walk speed (0.34 a tick) rises by half
     * and its hack (10) by a fifth for 100 ticks and then both return; a second use during the 600 ticks of rest is
     * refused with {@code ON_COOLDOWN}, and one after them is accepted and charges again.
     */
    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 1_500)
    public static void charge(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(HORSEMAN, new FactionSlot(0), new Vec2d(24, 24), 1, 0));
        var state = new Object() {
            int horseman;
            CommandResult first;
            CommandResult during;
            CommandResult later;
            long used; // the tick the first use was submitted at
            long rose;
        };
        h.startSequence()
                .thenWaitUntil(() -> check(handles(rt).length == 1, "the horseman is not there yet"))
                .thenExecute(() -> {
                    state.horseman = handles(rt)[0];
                    near(stat(rt, state.horseman, CorePaths.WALK_SPEED), 0.34, "the walk speed");
                    near(stat(rt, state.horseman, CorePaths.MELEE_HACK), 10, "the hack");
                    state.used = core(rt).queries().tick();
                    rt.submit(P0, charge(state.horseman), r -> state.first = r);
                })
                .thenWaitUntil(() -> check(state.first != null, "no reply to the first use yet"))
                .thenExecute(
                        () -> check(state.first.outcome() == CommandOutcome.ACCEPTED, "the first use: " + state.first))
                .thenWaitUntil(
                        () -> near(stat(rt, state.horseman, CorePaths.WALK_SPEED), 0.34 * 1.5, "the charging speed"))
                .thenExecute(() -> {
                    state.rose = core(rt).queries().tick();
                    near(stat(rt, state.horseman, CorePaths.MELEE_HACK), 12, "the charging hack");
                    rt.submit(P0, charge(state.horseman), r -> state.during = r);
                })
                .thenWaitUntil(() -> check(state.during != null, "no reply to the second use yet"))
                .thenExecute(() -> {
                    check(state.during.outcome() == CommandOutcome.REJECTED, "the second use: " + state.during);
                    check(
                            state.during.firstReason() == RejectReason.ON_COOLDOWN,
                            "refused for " + state.during.firstReason() + ", not ON_COOLDOWN");
                })
                .thenWaitUntil(
                        () -> near(stat(rt, state.horseman, CorePaths.WALK_SPEED), 0.34, "the speed after the charge"))
                .thenExecute(() -> {
                    long lasted = core(rt).queries().tick() - state.rose;
                    check(Math.abs(lasted - DURATION) <= 2, "the charge lasted " + lasted + " ticks, not " + DURATION);
                    near(stat(rt, state.horseman, CorePaths.MELEE_HACK), 10, "the hack after the charge");
                    state.during = null;
                    rt.submit(P0, charge(state.horseman), r -> state.during = r);
                })
                .thenWaitUntil(() -> check(state.during != null, "no reply to the use after the charge yet"))
                .thenExecute(() -> check(
                        state.during.firstReason() == RejectReason.ON_COOLDOWN,
                        "a use at the charge's end: " + state.during))
                .thenWaitUntil(() ->
                        check(core(rt).queries().tick() >= state.used + COOLDOWN + 2, "the rest has not run out yet"))
                .thenExecute(() -> rt.submit(P0, charge(state.horseman), r -> state.later = r))
                .thenWaitUntil(() -> check(state.later != null, "no reply to the use after the rest yet"))
                .thenExecute(() -> check(
                        state.later.outcome() == CommandOutcome.ACCEPTED, "the use after the rest: " + state.later))
                .thenWaitUntil(() ->
                        near(stat(rt, state.horseman, CorePaths.WALK_SPEED), 0.34 * 1.5, "the second charge's speed"))
                .thenSucceed();
    }

    private static UseAbility charge(int horseman) {
        return UseAbility.replace(UnitSet.of(horseman), new AbilityId("charge"), Target.none());
    }
}
