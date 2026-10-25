package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.handles;

import com.google.gson.JsonObject;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.MatchNews;
import dev.strategos.core.protocol.Research;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.ContentSwap;
import dev.strategos.core.sim.TechResearched;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.stats.StatBreakdown;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.payload.MatchEventPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;

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

    /**
     * TECH-02.4: when a faction reaches Town, every participant of the match, its own and the other's, hears the PHASE
     * news with the advancing faction and the phase's ordinal.
     */
    @StrategosTest(batch = "tech", arena = "arena_flat_48", timeoutTicks = 400)
    public static void everyPlayerGetsThePhaseNotice(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        TechId town = new TechId("strategos:phase_town");
        TemplateId house = new TemplateId("strategos:building/house");
        TemplateId centre = new TemplateId("strategos:building/civic_centre");
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(centre, new FactionSlot(0), 4, 4, 0));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(centre, new FactionSlot(1), 30, 30, 0));
        for (int k = 0; k < 5; k++) {
            rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(house, new FactionSlot(0), 4 + 8 * k, 18, 0));
        }
        for (String resource : List.of("food", "wood", "stone", "iron")) {
            rt.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), resource, 1_000));
        }
        rt.queueSwap(new ContentSwap(withTownIn(1))); // the notice depends only on the Phase class, not on the 60 s
        var a = BuildingTests.seated(h, rt, "strat-phase-a", 0);
        var b = BuildingTests.seated(h, rt, "strat-phase-b", 1);
        h.startSequence()
                .thenWaitUntil(() -> check(
                        core(rt).queries().buildings().size() == 7,
                        "the buildings are not up: "
                                + core(rt).queries().buildings().size()))
                .thenExecute(() -> rt.submit(
                        P0,
                        new Research(
                                core(rt).queries().buildings().stream()
                                        .filter(v -> v.template().equals(centre) && v.faction() == 0)
                                        .findFirst()
                                        .orElseThrow()
                                        .handle(),
                                town)))
                .thenWaitUntil(() -> check(
                        core(rt).queries().buildings().stream()
                                .anyMatch(v -> !v.queue().isEmpty()),
                        "not queued"))
                .thenWaitUntil(() -> check(
                        core(rt).queries().buildings().stream()
                                .noneMatch(v -> !v.queue().isEmpty()),
                        "Town is not researched yet"))
                .thenExecuteAfter(3, () -> {
                    for (var viewer : List.of(a, b)) {
                        viewer.poll();
                        List<MatchNews> news = new ArrayList<>();
                        for (var p : viewer.capture().of(ClientboundCustomPayloadPacket.class)) {
                            if (p.payload() instanceof MatchEventPayload m
                                    && m.news().matchId() == rt.id().value()
                                    && m.news().kind() == MatchNews.PHASE) {
                                news.add(m.news());
                            }
                        }
                        String who = viewer.player().getName().getString();
                        check(news.size() == 1, who + " heard " + news);
                        check(news.get(0).faction() == 0 && news.get(0).ticks() == 1, who + " heard " + news.get(0));
                    }
                    a.close();
                    b.close();
                })
                .thenSucceed();
    }

    /** The built-in set with {@code phase_town} researched in {@code seconds}. */
    private static TemplateSet withTownIn(double seconds) {
        TemplateSet b = TemplateSet.builtin();
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : b.all()) {
            flats.add(new TemplateCompiler.Flat(
                    t.id(), t.netIndex(), b.flattened(t.id()).deepCopy()));
        }
        List<TemplateCompiler.TechFlat> techs = new ArrayList<>();
        for (TechDef t : b.techs()) {
            JsonObject json = t.toJson();
            if (t.id().value().equals("strategos:phase_town")) {
                json.addProperty("time", seconds);
            }
            techs.add(new TemplateCompiler.TechFlat(t.id(), t.netIndex(), json));
        }
        return TemplateCompiler.fromFlattened(flats, techs).set();
    }
}
