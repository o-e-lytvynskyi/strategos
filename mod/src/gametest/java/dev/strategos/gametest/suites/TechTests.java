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
import dev.strategos.mod.server.net.payload.MatchStatePayload;
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

    /**
     * TECH-03.3: when iron weapons are researched, every player of the match, the researcher and the other, has the
     * faction's new weapon tier in its {@code match_state} (its client model) within a second: 20 ticks.
     */
    @StrategosTest(batch = "tech", arena = "arena_flat_48", timeoutTicks = 400)
    public static void aTierReachesEveryViewerWithinASecond(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        TechId weapons = new TechId("strategos:iron_weapons");
        TemplateId barracks = new TemplateId("strategos:building/barracks");
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(barracks, new FactionSlot(0), 4, 4, 0));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(barracks, new FactionSlot(1), 30, 30, 0));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(0), new Vec2d(20, 20), 1, 1));
        for (String resource : List.of("food", "wood", "iron")) {
            rt.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), resource, 1_000));
        }
        rt.queueSwap(new ContentSwap(withIronWeaponsAtOnce()));
        var a = BuildingTests.seated(h, rt, "strat-tier-a", 0);
        var b = BuildingTests.seated(h, rt, "strat-tier-b", 1);
        long[] doneAt = {-1};
        rt.addListener(out -> out.events().stream()
                .filter(TechResearched.class::isInstance)
                .map(TechResearched.class::cast)
                .filter(t -> t.tech().equals(weapons))
                .forEach(t -> doneAt[0] = core(rt).queries().tick()));
        h.startSequence()
                .thenWaitUntil(
                        () -> check(core(rt).queries().buildings().size() == 2 && handles(rt).length == 1, "not ready"))
                .thenWaitUntil(() -> check(
                        weaponTier(a, rt, 0) == 0 && weaponTier(b, rt, 0) == 0,
                        "no starting tier yet: " + weaponTier(a, rt, 0) + ", " + weaponTier(b, rt, 0)))
                .thenExecute(() -> rt.submit(
                        P0,
                        new Research(
                                core(rt).queries().buildings().stream()
                                        .filter(v -> v.template().equals(barracks) && v.faction() == 0)
                                        .findFirst()
                                        .orElseThrow()
                                        .handle(),
                                weapons)))
                .thenWaitUntil(() -> check(doneAt[0] >= 0, "not researched yet"))
                .thenWaitUntil(() -> check(
                        weaponTier(a, rt, 0) == 1 && weaponTier(b, rt, 0) == 1,
                        "the tier has not arrived: " + weaponTier(a, rt, 0) + ", " + weaponTier(b, rt, 0)))
                .thenExecute(() -> {
                    long took = core(rt).queries().tick() - doneAt[0];
                    check(took <= 20, "the tier took " + took + " ticks to reach both viewers");
                    check(weaponTier(a, rt, 1) == 0 && weaponTier(b, rt, 1) == 0, "the other faction's tier moved");
                    a.close();
                    b.close();
                })
                .thenSucceed();
    }

    /** The weapon tier of {@code faction} in the last {@code match_state} {@code viewer} received; -1 if none. */
    private static int weaponTier(dev.strategos.gametest.net.MockViewer viewer, MatchRuntime rt, int faction) {
        viewer.poll();
        int tier = -1;
        for (var p : viewer.capture().of(ClientboundCustomPayloadPacket.class)) {
            if (p.payload() instanceof MatchStatePayload m
                    && m.state().matchId() == rt.id().value()
                    && faction < m.state().looks().size()) {
                tier = m.state().looks().get(faction).weapon();
            }
        }
        return tier;
    }

    /** The built-in set whose iron weapons take a second, need no Town and are researched at the barracks. */
    private static TemplateSet withIronWeaponsAtOnce() {
        TemplateSet b = TemplateSet.builtin();
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : b.all()) {
            JsonObject json = b.flattened(t.id()).deepCopy();
            if (t.id().value().equals("strategos:building/barracks")) {
                json.getAsJsonObject("Researcher").addProperty("techs", "strategos:iron_weapons");
            }
            flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), json));
        }
        List<TemplateCompiler.TechFlat> techs = new ArrayList<>();
        for (TechDef t : b.techs()) {
            JsonObject json = t.toJson();
            if (t.id().value().equals("strategos:iron_weapons")) {
                json.addProperty("time", 1);
                json.remove("requires");
            }
            techs.add(new TemplateCompiler.TechFlat(t.id(), t.netIndex(), json));
        }
        return TemplateCompiler.fromFlattened(flats, techs).set();
    }

    /**
     * TECH-05 acceptance 2, the economy family: two tiers of a resource line researched through the command at the
     * storehouse. The first tier lifts a worker's wood rate by a fifth, the second supersedes it and stands at 1.4
     * times the base, not on top of the fifth.
     */
    @StrategosTest(batch = "tech", timeoutTicks = 600)
    public static void economy(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        TechId wood2 = new TechId("strategos:gather_wood_2");
        rt.queueSwap(new ContentSwap(fast(
                "strategos:building/storehouse",
                "strategos:gather_wood_1 strategos:gather_wood_2",
                List.of(WOOD_1.value(), wood2.value()))));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(STOREHOUSE, new FactionSlot(0), 4, 4, 0));
        for (String resource : List.of("food", "wood")) {
            rt.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), resource, 1_000));
        }
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.WORKER, new FactionSlot(0), new Vec2d(24, 24), 1, 1));
        h.startSequence()
                .thenWaitUntil(() -> check(storehouse(rt) != null && handles(rt).length == 1, "not ready"))
                .thenExecute(() -> rt.submit(P0, new Research(storehouse(rt).handle(), WOOD_1)))
                .thenWaitUntil(() -> check(Math.abs(rate(rt) - 0.72) < 1e-9, "tier 1: the rate is " + rate(rt)))
                .thenExecute(() -> rt.submit(P0, new Research(storehouse(rt).handle(), wood2)))
                .thenWaitUntil(() -> check(Math.abs(rate(rt) - 0.84) < 1e-9, "tier 2: the rate is " + rate(rt)))
                .thenSucceed();
    }

    /**
     * TECH-05 acceptance 2, the military family: the shield wall lifts a spearman's hack resistance by two, and its
     * pair, the light drill, is then refused and changes nothing.
     */
    @StrategosTest(batch = "tech", timeoutTicks = 600)
    public static void military(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        TechId phalanx = new TechId("strategos:phalanx");
        TechId drill = new TechId("strategos:skirmish_drill");
        TemplateId barracks = new TemplateId("strategos:building/barracks");
        rt.queueSwap(new ContentSwap(fast(
                barracks.value(), phalanx.value() + " " + drill.value(), List.of(phalanx.value(), drill.value()))));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(barracks, new FactionSlot(0), 4, 4, 0));
        for (String resource : List.of("food", "wood", "iron")) {
            rt.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), resource, 1_000));
        }
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(0), new Vec2d(24, 24), 1, 1));
        List<TechId> done = new ArrayList<>();
        rt.addListener(out -> out.events().stream()
                .filter(TechResearched.class::isInstance)
                .map(TechResearched.class::cast)
                .forEach(t -> done.add(t.tech())));
        double[] base = {0};
        long[] refusedAt = {0};
        h.startSequence()
                .thenWaitUntil(
                        () -> check(!core(rt).queries().buildings().isEmpty() && handles(rt).length == 1, "not ready"))
                .thenExecute(() -> {
                    base[0] = hackResistance(rt);
                    rt.submit(
                            P0,
                            new Research(core(rt).queries().buildings().get(0).handle(), phalanx));
                })
                .thenWaitUntil(() -> check(
                        Math.abs(hackResistance(rt) - (base[0] + 2)) < 1e-9,
                        "the resistance is " + hackResistance(rt) + ", not " + (base[0] + 2)))
                .thenExecute(() -> {
                    rt.submit(
                            P0,
                            new Research(core(rt).queries().buildings().get(0).handle(), drill));
                    refusedAt[0] = core(rt).queries().tick();
                })
                .thenWaitUntil(() -> check(core(rt).queries().tick() >= refusedAt[0] + 40, "waiting for the refusal"))
                .thenExecute(() -> {
                    check(done.equals(List.of(phalanx)), "researched " + done + ", the pair was not refused");
                    check(core(rt).queries().buildings().get(0).queue().isEmpty(), "the pair was queued");
                })
                .thenSucceed();
    }

    /** TECH-05 acceptance 2, the buildings family: masonry at the arsenal raises its hit points by a fifth. */
    @StrategosTest(batch = "tech", timeoutTicks = 600)
    public static void buildings(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        TechId masonry = new TechId("strategos:masonry_1");
        TemplateId arsenal = new TemplateId("strategos:building/arsenal");
        rt.queueSwap(new ContentSwap(fast(arsenal.value(), masonry.value(), List.of(masonry.value()))));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(arsenal, new FactionSlot(0), 4, 4, 0));
        for (String resource : List.of("wood", "stone")) {
            rt.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), resource, 1_000));
        }
        double[] base = {0};
        h.startSequence()
                .thenWaitUntil(() -> check(!core(rt).queries().buildings().isEmpty(), "not ready"))
                .thenExecute(() -> {
                    base[0] = core(rt).queries().buildings().get(0).maxHitPoints();
                    rt.submit(
                            P0,
                            new Research(core(rt).queries().buildings().get(0).handle(), masonry));
                })
                .thenWaitUntil(() -> check(
                        Math.abs(core(rt).queries().buildings().get(0).maxHitPoints() - base[0] * 1.2) < 1e-6,
                        "the hit points are "
                                + core(rt).queries().buildings().get(0).maxHitPoints() + ", not " + base[0] * 1.2))
                .thenSucceed();
    }

    private static double hackResistance(MatchRuntime rt) {
        StatBreakdown b = core(rt).queries().stats(handles(rt)[0], "Resistance/hack");
        return b == null ? Double.NaN : b.result();
    }

    /**
     * The built-in set with {@code researcher} researching exactly {@code list}, and each of {@code fast} taking a
     * second with no phase to reach (a tech it names stays required, its phase does not).
     */
    private static TemplateSet fast(String researcher, String list, List<String> fast) {
        TemplateSet b = TemplateSet.builtin();
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : b.all()) {
            JsonObject json = b.flattened(t.id()).deepCopy();
            if (t.id().value().equals(researcher)) {
                json.getAsJsonObject("Researcher").addProperty("techs", list);
            }
            flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), json));
        }
        List<TemplateCompiler.TechFlat> techs = new ArrayList<>();
        for (TechDef t : b.techs()) {
            JsonObject json = t.toJson();
            if (fast.contains(t.id().value())) {
                json.addProperty("time", 1);
                json.remove("requires");
                if (t.supersedes() != null) {
                    var req = new JsonObject();
                    req.addProperty("tech", t.supersedes().value());
                    json.add("requires", req);
                }
            }
            techs.add(new TemplateCompiler.TechFlat(t.id(), t.netIndex(), json));
        }
        return TemplateCompiler.fromFlattened(flats, techs).set();
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
