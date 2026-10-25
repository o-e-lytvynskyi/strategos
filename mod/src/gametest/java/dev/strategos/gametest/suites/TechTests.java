package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.handles;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.Research;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.TechResearched;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.stats.StatBreakdown;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;

/** Research in a real level (TECH-01, acceptance 4): queued at a building, completed in its time, applied to the stats. */
public final class TechTests {
    static final TemplateId STOREHOUSE = new TemplateId("strategos:building/storehouse");
    static final TechId WOOD_1 = new TechId("strategos:gather_wood_1");
    static final String RATE = "ResourceGatherer/rates/wood/tree";
    static final PlayerSlot P0 = new PlayerSlot(0);

    private TechTests() {}

    private static BuildingView storehouse(MatchRuntime rt) {
        return core(rt).queries().buildings().stream()
                .filter(b -> b.template().equals(STOREHOUSE))
                .findFirst()
                .orElse(null);
    }

    /** A worker's wood rate at trees, or NaN while there is no worker. */
    private static double rate(MatchRuntime rt) {
        if (handles(rt).length == 0) {
            return Double.NaN;
        }
        StatBreakdown b = core(rt).queries().stats(handles(rt)[0], RATE);
        return b == null ? Double.NaN : b.result();
    }

    /** TECH-01.4: the storehouse researches the wood technology within its 600 ticks, and the worker gathers 1.2 times faster. */
    @StrategosTest(batch = "tech", timeoutTicks = 1_000)
    public static void aResearchCompletesAndApplies(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(STOREHOUSE, new FactionSlot(0), 4, 4, 0));
        rt.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "food", 500));
        rt.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "wood", 500));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.WORKER, new FactionSlot(0), new Vec2d(24, 24), 1, 1));
        List<TechResearched> done = new ArrayList<>();
        rt.addListener(out -> out.events().stream()
                .filter(TechResearched.class::isInstance)
                .map(TechResearched.class::cast)
                .forEach(done::add));
        long[] started = {0};
        h.startSequence()
                .thenWaitUntil(() -> check(storehouse(rt) != null && handles(rt).length == 1, "not ready"))
                .thenExecute(() -> {
                    check(Math.abs(rate(rt) - 0.6) < 1e-9, "the base rate is " + rate(rt));
                    started[0] = core(rt).queries().tick();
                    rt.submit(P0, new Research(storehouse(rt).handle(), WOOD_1));
                })
                .thenWaitUntil(() -> check(storehouse(rt).queue().size() == 1, "the research is not queued"))
                .thenExecute(() -> check(done.isEmpty() && Math.abs(rate(rt) - 0.6) < 1e-9, "applied before its time"))
                .thenWaitUntil(() -> check(!done.isEmpty(), "not researched yet"))
                .thenExecute(() -> {
                    long took = core(rt).queries().tick() - started[0];
                    check(took >= 600 && took <= 700, "researched after " + took + " ticks, not about 600");
                    check(
                            done.get(0).tech().equals(WOOD_1)
                                    && done.get(0).faction().value() == 0,
                            "researched " + done.get(0));
                    check(Math.abs(rate(rt) - 0.72) < 1e-9, "the worker's wood rate is " + rate(rt) + ", not 0.72");
                    check(storehouse(rt).queue().isEmpty(), "the entry stayed in the queue");
                })
                .thenSucceed();
    }
}
