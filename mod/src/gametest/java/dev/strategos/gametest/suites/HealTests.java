package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.body;
import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.handles;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.server.match.MatchRuntime;
import net.minecraft.gametest.framework.GameTestHelper;

/** CMB-12 on the shipped data (M10 spec §2.9): the healer's autocast {@code heal}, seen through the real bodies. */
public final class HealTests {
    private static final TemplateId HEALER = new TemplateId("strategos:unit/healer");
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final double AMOUNT = 5; // the healer's heal, hit points a pulse (data)
    private static final int COOLDOWN = 40; // ticks between pulses (data)
    private static final int WATCH =
            100; // pulses of the first period's offset (at most 10), then every COOLDOWN: 3 in 100

    private HealTests() {}

    /**
     * A wounded spearman beside a healer: the healer, idle, heals it by itself; a hundred ticks after the wound the
     * spearman has gained exactly three pulses of the data's amount (the first within the autocast period, then one a
     * cooldown), and the healer's own hit points did not move.
     */
    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 600)
    public static void healer_heals(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(HEALER, F0, new Vec2d(24, 24), 1, 0));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, F0, new Vec2d(27, 24), 1, 0));
        var state = new Object() {
            int healer;
            int soldier;
            double healerBefore;
            double wounded;
        };
        h.startSequence()
                .thenWaitUntil(() -> {
                    check(handles(rt).length == 2, "the units are not both there yet");
                    state.healer = handles(rt)[0]; // spawned first, so the lower handle
                    state.soldier = handles(rt)[1];
                    body(rt, state.soldier);
                })
                .thenExecute(() -> {
                    check(
                            core(rt).queries().unit(state.healer).template().equals(HEALER),
                            "the first unit is not the healer");
                    state.healerBefore = core(rt).queries().hitPoints(state.healer);
                    body(rt, state.soldier).hurt(h.getLevel().damageSources().generic(), 40);
                })
                .thenExecuteAfter(2, () -> {
                    state.wounded = core(rt).queries().hitPoints(state.soldier);
                    check(state.wounded < 100 - 10, "the spearman kept " + state.wounded + " hit points: not wounded");
                })
                .thenExecuteAfter(WATCH - 2, () -> {
                    double now = core(rt).queries().hitPoints(state.soldier);
                    double pulses = (now - state.wounded) / AMOUNT;
                    check(
                            Math.abs(now - (state.wounded + 3 * AMOUNT)) < 1e-6,
                            "after " + WATCH + " ticks the spearman has " + now + " hit points from " + state.wounded
                                    + ": " + pulses + " pulses of " + AMOUNT + ", not 3 (cooldown " + COOLDOWN + ")");
                    check(
                            core(rt).queries().hitPoints(state.healer) == state.healerBefore,
                            "the healer's own hit points moved");
                })
                .thenSucceed();
    }
}
