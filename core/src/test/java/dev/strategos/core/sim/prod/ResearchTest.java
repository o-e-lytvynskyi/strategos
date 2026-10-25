package dev.strategos.core.sim.prod;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.CancelTrain;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.Research;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.ContentSwap;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.TechResearched;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.sim.stats.StatBreakdown;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * TECH-01 in L1 (acceptance 2 and 3): a research completes in its time and its stats change on that tick, the
 * breakdown names the tech, a tech is researched once and in one building at a time, requirements gate it, a cancel
 * refunds, and a restore keeps both the research and its effects.
 */
class ResearchTest {
    static final TemplateId STOREHOUSE = new TemplateId("strategos:building/storehouse");
    static final TechId WOOD_1 = new TechId("strategos:gather_wood_1");
    static final TechId WOOD_2 = new TechId("strategos:gather_wood_2");
    static final String RATE = "ResourceGatherer/rates/wood/tree";

    static final TechId WOOD_3 = new TechId("strategos:gather_wood_3");

    private static final String WOOD_2_AFTER_1 = """
            {"icon":"minecraft:diamond_axe","cost":{"food":10},"time":5,
             "requires":{"tech":"strategos:gather_wood_1"},
             "supersedes":"strategos:gather_wood_1",
             "effects":[{"path":"ResourceGatherer/rates/wood/tree","op":"mul","value":1.1,"filter":"Worker"}]}
            """;
    private static final String WOOD_2_ALONE = """
            {"icon":"minecraft:diamond_axe","cost":{"food":10},"time":5,
             "supersedes":"strategos:gather_wood_1",
             "effects":[{"path":"ResourceGatherer/rates/wood/tree","op":"mul","value":1.1,"filter":"Worker"}]}
            """;
    private static final String WOOD_3_AFTER_2 = """
            {"icon":"minecraft:netherite_axe","cost":{"food":10},"time":5,
             "requires":{"tech":"strategos:gather_wood_2"},
             "supersedes":"strategos:gather_wood_2",
             "effects":[{"path":"ResourceGatherer/rates/wood/tree","op":"mul","value":1.05,"filter":"Worker"}]}
            """;

    /** The built-in set with extra techs (test-only), all researchable at the storehouse; the first one's factor too. */
    static TemplateSet techSet(double firstFactor, String... more) {
        TemplateSet b = TemplateSet.builtin();
        StringBuilder researches = new StringBuilder(WOOD_1.value());
        List<TemplateCompiler.TechFlat> techs = new ArrayList<>();
        TechDef first = b.tech(WOOD_1);
        assertThat(first).isNotNull();
        JsonObject firstJson = first.toJson();
        firstJson.getAsJsonArray("effects").get(0).getAsJsonObject().addProperty("value", firstFactor);
        techs.add(new TemplateCompiler.TechFlat(WOOD_1, 0, firstJson));
        for (int k = 0; k < more.length; k++) {
            TechId id = new TechId("strategos:gather_wood_" + (k + 2));
            researches.append(' ').append(id.value());
            techs.add(new TemplateCompiler.TechFlat(
                    id, k + 1, JsonParser.parseString(more[k]).getAsJsonObject()));
        }
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : b.all()) {
            JsonObject json = b.flattened(t.id()).deepCopy();
            if (t.id().equals(STOREHOUSE)) {
                json.getAsJsonObject("Researcher").addProperty("techs", researches.toString());
            }
            flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), json));
        }
        return TemplateCompiler.fromFlattened(flats, techs).set();
    }

    /** The built-in set and a second tech, researchable at the storehouse once the first is (test-only). */
    static TemplateSet twoTechs() {
        return techSet(1.2, WOOD_2_AFTER_1);
    }

    private static int storehouse(ProdScene p, int x, int z) {
        int before = p.s.buildings().handles().length;
        p.h.submit(PlayerSlot.ADMIN, new SpawnBuilding(STOREHOUSE, new FactionSlot(0), x, z, 0));
        p.h.step();
        int[] all = p.s.buildings().handles();
        assertThat(all).hasSize(before + 1);
        return all[all.length - 1];
    }

    private static CommandResult research(ProdScene p, int building, TechId tech) {
        p.h.submit(ProdScene.P0, new Research(building, tech));
        p.h.step();
        return p.h.results().get(p.h.results().size() - 1);
    }

    private static double rate(ProdScene p) {
        int worker = p.s.templates().byId(TemplateSet.WORKER).netIndex();
        return p.s.stats().blockValue(worker, 0, p.s.stats().path(RATE));
    }

    private static ProdScene rich() {
        ProdScene p = ProdScene.of(1_000, 1_000);
        p.h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "stone", 500));
        p.h.step();
        return p;
    }

    /** Steps until the tech is researched; the ticks taken, the submit step counted as the first. */
    private static int untilResearched(ProdScene p, int limit) {
        for (int t = 1; t <= limit; t++) {
            for (OutputEvent e : p.h.step().events()) {
                if (e instanceof TechResearched) {
                    return t;
                }
            }
        }
        return -1;
    }

    @Test
    void aCompletedTechChangesStatsAtOnce() {
        ProdScene p = rich();
        int house = storehouse(p, 30, 30);
        assertThat(rate(p)).isEqualTo(0.6);
        assertThat(research(p, house, WOOD_1).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(p.s.progress().researched(0, WOOD_1))
                .as("pending is not researched")
                .isFalse();
        int ticks = 0;
        for (int t = 2; t <= 600; t++) { // the submit step trained one tick of the 600
            TickOutputs r = p.h.step();
            boolean done = r.events().stream().anyMatch(e -> e instanceof TechResearched);
            if (t < 600) {
                assertThat(done).as("tick " + t).isFalse();
                assertThat(rate(p)).as("tick " + t).isEqualTo(0.6);
            } else {
                assertThat(done).as("the completion tick").isTrue();
                ticks = t;
            }
        }
        assertThat(ticks).isEqualTo(600);
        assertThat(rate(p)).as("at once").isEqualTo(0.6 * 1.2);
        assertThat(p.s.progress().researched(0, WOOD_1)).isTrue();
        assertThat(p.s.buildings().queueSize(house))
                .as("the entry left the queue")
                .isZero();
    }

    @Test
    void theBreakdownNamesTheTech() {
        ProdScene p = rich();
        int house = storehouse(p, 30, 30);
        research(p, house, WOOD_1);
        assertThat(untilResearched(p, 700)).isPositive();
        int worker = p.s.templates().byId(TemplateSet.WORKER).netIndex();
        StatBreakdown b = p.s.stats().blockBreakdown(worker, 0, RATE);
        assertThat(b).isNotNull();
        assertThat(b.modifiers()).hasSize(1);
        TechId named = p.s.progress().techOf(b.modifiers().get(0).sourceId());
        assertThat(named).isEqualTo(WOOD_1);
        assertThat(p.s.progress().techOf(7)).as("an id that is no tech's").isNull();
    }

    @Test
    void onceAndInOneBuildingAtATime() {
        ProdScene p = rich();
        int first = storehouse(p, 30, 30);
        int second = storehouse(p, 44, 44);
        assertThat(research(p, first, WOOD_1).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        CommandResult twice = research(p, second, WOOD_1);
        assertThat(twice.firstReason()).as("pending in the first").isEqualTo(RejectReason.LIMIT);
        CommandResult same = research(p, first, WOOD_1);
        assertThat(same.firstReason()).as("pending in this one too").isEqualTo(RejectReason.LIMIT);
        assertThat(untilResearched(p, 700)).isPositive();
        assertThat(research(p, second, WOOD_1).firstReason()).as("researched").isEqualTo(RejectReason.LIMIT);
        assertThat(rate(p)).as("the refused ones paid and changed nothing").isEqualTo(0.6 * 1.2);
    }

    @Test
    void refusalsNameTheirReasons() {
        ProdScene p = rich();
        int house = storehouse(p, 30, 30);
        assertThat(research(p, house, new TechId("strategos:nothing")).firstReason())
                .isEqualTo(RejectReason.UNKNOWN_TEMPLATE);
        assertThat(research(p, 0x7FFFF, WOOD_1).firstReason()).isEqualTo(RejectReason.UNKNOWN_TARGET);
        assertThat(research(p, p.centre, WOOD_1).firstReason())
                .as("the civic centre researches nothing")
                .isEqualTo(RejectReason.UNSUPPORTED);
        ProdScene poor = ProdScene.of(0, 0);
        int shed = storehouse(poor, 30, 30);
        CommandResult broke = research(poor, shed, WOOD_1);
        assertThat(broke.firstReason()).isEqualTo(RejectReason.NOT_ENOUGH_RESOURCES);
        assertThat(broke.missing()).containsExactly(100_000L, 50_000L, 0L, 0L);
    }

    @Test
    void requirementsAreChecked() {
        ProdScene p = ProdScene.of(1_000, 1_000, twoTechs());
        int house = storehouse(p, 30, 30);
        CommandResult early = research(p, house, WOOD_2);
        assertThat(early.firstReason()).isEqualTo(RejectReason.REQUIREMENTS);
        assertThat(p.s.treasury().stock(0, 0)).as("nothing paid").isEqualTo(1_000_000L);
        research(p, house, WOOD_1);
        assertThat(untilResearched(p, 700)).isPositive();
        assertThat(research(p, house, WOOD_2).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(untilResearched(p, 200)).isPositive();
        assertThat(rate(p))
                .as("the second supersedes the first: only its own multiplier stays")
                .isEqualTo(0.6 * 1.1);
    }

    @Test
    void aCancelRefundsTheCost() {
        ProdScene p = rich();
        int house = storehouse(p, 30, 30);
        long food = p.s.treasury().stock(0, 0);
        long wood = p.s.treasury().stock(0, 1);
        research(p, house, WOOD_1);
        assertThat(p.s.treasury().stock(0, 0)).isEqualTo(food - 100_000);
        assertThat(p.s.treasury().stock(0, 1)).isEqualTo(wood - 50_000);
        p.h.runTicks(30);
        p.h.submit(ProdScene.P0, new CancelTrain(house, 0));
        p.h.step();
        assertThat(p.s.treasury().stock(0, 0)).isEqualTo(food);
        assertThat(p.s.treasury().stock(0, 1)).isEqualTo(wood);
        assertThat(p.s.buildings().queueSize(house)).isZero();
        assertThat(research(p, house, WOOD_1).outcome()).as("no longer pending").isEqualTo(CommandOutcome.ACCEPTED);
    }

    @Test
    void aLostBuildingRefundsItsPendingTech() {
        ProdScene p = rich();
        int house = storehouse(p, 30, 30);
        long food = p.s.treasury().stock(0, 0);
        research(p, house, WOOD_1);
        Production.lost(p.s, house);
        assertThat(p.s.treasury().stock(0, 0)).isEqualTo(food);
    }

    @Test
    void aTechSharesTheQueueAndTakesNoPopulation() {
        ProdScene p = rich();
        int house = storehouse(p, 30, 30);
        int used = p.s.population().used(0);
        research(p, house, WOOD_1);
        assertThat(p.s.buildings().queue(house))
                .as("a tech entry is -1 - its index")
                .containsExactly(-1);
        assertThat(p.s.population().used(0)).isEqualTo(used);
        assertThat(p.scanned(0)[0]).isEqualTo(used);
        ProdScene full = rich();
        int shed = storehouse(full, 30, 30);
        int worker = full.s.templates().byId(TemplateSet.WORKER).netIndex();
        for (int k = 0; k < 5; k++) {
            assertThat(full.s.buildings().enqueue(shed, worker)).isTrue();
        }
        assertThat(research(full, shed, WOOD_1).firstReason()).isEqualTo(RejectReason.QUEUE_FULL);
    }

    @Test
    void restoreKeepsTheResearchAndItsEffects() {
        ProdScene p = rich();
        int house = storehouse(p, 30, 30);
        research(p, house, WOOD_1);
        assertThat(untilResearched(p, 700)).isPositive();
        long hash = p.h.stateHash();
        SimHarness back = SimHarness.restore(p.h.core().snapshot(), TemplateSet.builtin());
        assertThat(back.stateHash()).as("same state").isEqualTo(hash);
        ProdScene r = new ProdScene(back, p.centre);
        assertThat(r.s.progress().researched(0, WOOD_1)).isTrue();
        assertThat(rate(r)).as("the effect is back").isEqualTo(0.6 * 1.2);
        assertThat(r.s.stats()
                        .blockBreakdown(r.s.templates().byId(TemplateSet.WORKER).netIndex(), 0, RATE)
                        .modifiers())
                .hasSize(1);
    }

    @Test
    void aPendingResearchSurvivesARestore() {
        ProdScene p = rich();
        int house = storehouse(p, 30, 30);
        research(p, house, WOOD_1);
        p.h.runTicks(100);
        SimHarness back = SimHarness.restore(p.h.core().snapshot(), TemplateSet.builtin());
        assertThat(back.stateHash()).isEqualTo(p.h.stateHash());
        ProdScene r = new ProdScene(back, p.centre);
        assertThat(r.s.buildings().queueSize(house)).isOne();
        assertThat(untilResearched(r, 600)).isPositive();
        assertThat(rate(r)).isEqualTo(0.6 * 1.2);
    }

    @Test
    void aMatchWithoutResearchKeepsItsHash() {
        ProdScene p = ProdScene.of(1_000, 1_000);
        assertThat(p.s.progress().mix(12345L))
                .as("an empty Progress mixes nothing")
                .isEqualTo(12345L);
        long plain = p.h.stateHash();
        ProdScene q = rich();
        int house = storehouse(q, 30, 30);
        research(q, house, WOOD_1);
        assertThat(untilResearched(q, 700)).isPositive();
        assertThat(q.s.progress().mix(12345L)).as("a researched tech is hashed").isNotEqualTo(12345L);
        assertThat(q.h.stateHash()).isNotEqualTo(plain);
        assertThat(q.s.progress().save()).isNotEmpty();
        assertThat(p.s.progress().save())
                .as("nothing researched, nothing saved")
                .isEmpty();
    }

    /** The stat a restore of {@code p} gives, after checking the restored state hashes the same. */
    private static double restoredRate(ProdScene p, TemplateSet set) {
        SimHarness back = SimHarness.restore(p.h.core().snapshot(), set);
        assertThat(back.stateHash()).isEqualTo(p.h.stateHash());
        return rate(new ProdScene(back, p.centre));
    }

    @Test
    void aSupersededTechCannotBeResearchedAnyMore() {
        ProdScene p = ProdScene.of(1_000, 1_000, techSet(1.2, WOOD_2_ALONE));
        int house = storehouse(p, 30, 30);
        int other = storehouse(p, 44, 44);
        assertThat(research(p, house, WOOD_2).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(research(p, other, WOOD_1).firstReason())
                .as("its successor is pending")
                .isEqualTo(RejectReason.LIMIT);
        assertThat(untilResearched(p, 200)).isPositive();
        assertThat(research(p, other, WOOD_1).firstReason())
                .as("its successor is researched")
                .isEqualTo(RejectReason.LIMIT);
        assertThat(rate(p)).isEqualTo(0.6 * 1.1);
    }

    @Test
    void anOlderTechFinishingAfterItsSuccessorLeavesTheStatsAsARestoreHasThem() {
        TemplateSet set = techSet(1.2, WOOD_2_ALONE);
        ProdScene p = ProdScene.of(1_000, 1_000, set);
        int slow = storehouse(p, 30, 30);
        int quick = storehouse(p, 44, 44);
        research(p, slow, WOOD_1); // 600 ticks
        research(p, quick, WOOD_2); // 100 ticks
        assertThat(untilResearched(p, 200)).isPositive();
        assertThat(rate(p)).isEqualTo(0.6 * 1.1);
        assertThat(untilResearched(p, 600)).as("the older one completes too").isPositive();
        assertThat(p.s.progress().researched(0, WOOD_1)).isTrue();
        assertThat(rate(p)).as("the successor's alone").isEqualTo(0.6 * 1.1);
        assertThat(restoredRate(p, set)).isEqualTo(rate(p));
    }

    @Test
    void aChainOfSupersedingTechsLeavesOnlyTheLastAndRestoresTheSame() {
        TemplateSet set = techSet(1.2, WOOD_2_AFTER_1, WOOD_3_AFTER_2);
        ProdScene p = ProdScene.of(1_000, 1_000, set);
        int house = storehouse(p, 30, 30);
        for (TechId tech : List.of(WOOD_1, WOOD_2, WOOD_3)) {
            assertThat(research(p, house, tech).outcome()).as(tech.value()).isEqualTo(CommandOutcome.ACCEPTED);
            assertThat(untilResearched(p, 700)).isPositive();
        }
        assertThat(rate(p)).isEqualTo(0.6 * 1.05);
        assertThat(restoredRate(p, set)).isEqualTo(rate(p));
        int worker = p.s.templates().byId(TemplateSet.WORKER).netIndex();
        assertThat(p.s.stats().blockBreakdown(worker, 0, RATE).modifiers()).hasSize(1);
    }

    @Test
    void anUninterruptedRunAndARestoreAtTheSameTickAgree() {
        ProdScene whole = rich();
        int house = storehouse(whole, 30, 30);
        research(whole, house, WOOD_1);
        whole.h.runTicks(300);
        SimHarness back = SimHarness.restore(whole.h.core().snapshot(), TemplateSet.builtin());
        whole.h.runTicks(400);
        back.runTicks(400); // 100 ticks past the completion
        ProdScene restored = new ProdScene(back, whole.centre);
        assertThat(back.stateHash()).isEqualTo(whole.h.stateHash());
        int worker = whole.s.templates().byId(TemplateSet.WORKER).netIndex();
        int path = whole.s.stats().path(RATE);
        assertThat(restored.s.stats().blockValue(worker, 0, path))
                .isEqualTo(whole.s.stats().blockValue(worker, 0, path));
        assertThat(rate(restored)).isEqualTo(0.6 * 1.2);
    }

    @Test
    void aSwapRederivesTheResearchedTechsAndDropsAPendingOneTheNewSetLacks() {
        ProdScene p = ProdScene.of(1_000, 1_000, twoTechs());
        int done = storehouse(p, 30, 30);
        int pending = storehouse(p, 44, 44);
        research(p, done, WOOD_1);
        assertThat(untilResearched(p, 700)).isPositive();
        assertThat(research(p, pending, WOOD_2).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        long food = p.s.treasury().stock(0, 0);

        p.h.schedule(p.h.tick() + 1, new ContentSwap(techSet(1.5))); // the first tech stronger, the second gone
        p.h.runTicks(2);
        assertThat(p.s.templates().techs()).hasSize(1);
        assertThat(rate(p)).as("the researched tech from the new set").isEqualTo(0.6 * 1.5);
        assertThat(p.s.buildings().queueSize(pending))
                .as("the pending entry whose tech is gone")
                .isZero();
        assertThat(p.s.treasury().stock(0, 0))
                .as("no refund: its cost is not known")
                .isEqualTo(food);

        p.h.schedule(
                p.h.tick() + 1,
                new ContentSwap(TemplateSet.builtin().techs().isEmpty() ? TemplateSet.builtin() : noTechs()));
        p.h.runTicks(2);
        assertThat(rate(p)).as("a researched tech the set lacks gives nothing").isEqualTo(0.6);
        assertThat(p.s.progress().researched(0, WOOD_1))
                .as("but it stays researched")
                .isTrue();
    }

    private static TemplateSet noTechs() {
        TemplateSet b = TemplateSet.builtin();
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : b.all()) {
            flats.add(new TemplateCompiler.Flat(
                    t.id(), t.netIndex(), b.flattened(t.id()).deepCopy()));
        }
        return TemplateCompiler.fromFlattened(flats, List.of()).set();
    }

    @Test
    void aSnapshotKeepsProgressAndAQueuedTechByIdThroughJson() {
        ProdScene p = rich();
        int house = storehouse(p, 30, 30);
        int other = storehouse(p, 44, 44);
        research(p, house, WOOD_1);
        assertThat(untilResearched(p, 700)).isPositive();
        TemplateSet set = twoTechs();
        ProdScene q = ProdScene.of(1_000, 1_000, set);
        int h1 = storehouse(q, 30, 30);
        research(q, h1, WOOD_1);
        q.h.runTicks(50);
        MatchSnapshot snapshot = q.h.core().snapshot();
        MatchSnapshot decoded = MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, MatchSnapshotCodec.encode(JsonOps.INSTANCE, snapshot)),
                        Migrations.production())
                .getOrThrow();
        assertThat(decoded).isEqualTo(snapshot);
        assertThat(decoded.buildings().stream().flatMap(b -> b.production().techs().stream()))
                .containsExactly(WOOD_1.value());
        assertThat(decoded.buildings().stream().flatMap(b -> b.production().queue().stream()))
                .as("saved as -1 - the id's place in the building's list")
                .containsExactly(-1);

        MatchSnapshot done = p.h.core().snapshot();
        MatchSnapshot back = MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, MatchSnapshotCodec.encode(JsonOps.INSTANCE, done)),
                        Migrations.production())
                .getOrThrow();
        assertThat(back.play().progress()).containsExactly(List.of(WOOD_1.value()), List.of());
        assertThat(back).isEqualTo(done);
        assertThat(other).isPositive();
    }

    @Test
    void aMalformedTechIdFailsTheDecode() {
        ProdScene p = rich();
        int house = storehouse(p, 30, 30);
        research(p, house, WOOD_1);
        assertThat(untilResearched(p, 700)).isPositive();
        JsonElement json =
                MatchSnapshotCodec.encode(JsonOps.INSTANCE, p.h.core().snapshot());
        String text = json.toString().replace(WOOD_1.value(), "Not A Tech");
        assertThat(MatchSnapshotCodec.decode(
                                new Dynamic<>(JsonOps.INSTANCE, JsonParser.parseString(text)), Migrations.production())
                        .error())
                .isPresent();
    }

    @Test
    void aFoundationAndAnotherFactionsBuildingRefuse() {
        ProdScene p = rich();
        int house = storehouse(p, 30, 30);
        p.h.submit(new PlayerSlot(1), new Research(house, WOOD_1));
        p.h.step();
        assertThat(p.h.results().get(p.h.results().size() - 1).firstReason()).isEqualTo(RejectReason.NOT_OWNER);
        p.s.buildings().setState(house, BuildingStore.FOUNDATION, 0);
        assertThat(research(p, house, WOOD_1).firstReason()).isEqualTo(RejectReason.UNSUPPORTED);
    }

    @Test
    void aGaiaBuildingResearchesNothing() {
        ProdScene p = rich();
        p.h.submit(PlayerSlot.ADMIN, new SpawnBuilding(STOREHOUSE, new FactionSlot(p.s.gaia()), 44, 44, 0));
        p.h.step();
        int[] all = p.s.buildings().handles();
        int gaia = all[all.length - 1];
        p.h.submit(PlayerSlot.ADMIN, new Research(gaia, WOOD_1));
        p.h.step();
        assertThat(p.h.results().get(p.h.results().size() - 1).firstReason()).isEqualTo(RejectReason.UNSUPPORTED);
    }
}
