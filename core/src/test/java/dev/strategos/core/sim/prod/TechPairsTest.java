package dev.strategos.core.sim.prod;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.Research;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.TechResearched;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TECH-05 acceptance 3 in L1: paired technologies, choose one of two, and the lint that keeps every pair whole. */
class TechPairsTest {
    private static final TemplateSet SET = TemplateSet.builtin();
    private static final TechId LUMBER = new TechId("strategos:guild_lumber");
    private static final TechId MINING = new TechId("strategos:guild_mining");
    private static final TemplateId STOREHOUSE = new TemplateId("strategos:building/storehouse");

    @Test
    void everyPairExcludesTheOtherBothWaysAndThereAreAtLeastTwo() {
        List<TechDef> excluding =
                SET.techs().stream().filter(t -> !t.excludes().isEmpty()).toList();
        for (TechDef tech : excluding) {
            for (TechId other : tech.excludes()) {
                TechDef pair = SET.tech(other);
                assertThat(pair).as("%s excludes a tech of the set", tech.id()).isNotNull();
                assertThat(pair.excludes())
                        .as("%s excludes %s back", other, tech.id())
                        .contains(tech.id());
            }
        }
        assertThat(excluding.size() / 2).as("pairs: one economic, one military").isGreaterThanOrEqualTo(2);
        assertThat(SET.tech(LUMBER).excludes()).containsExactly(MINING);
        assertThat(SET.tech(new TechId("strategos:phalanx")).excludes())
                .containsExactly(new TechId("strategos:skirmish_drill"));
    }

    @Test
    void researchingOneRefusesItsPairAndNothingIsPaid() {
        ProdScene p = ProdScene.of(5_000, 5_000);
        int shed = storehouse(p);
        grantStone(p);
        assertThat(research(p, shed, LUMBER).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        long stock = p.s.treasury().stock(0, 0);
        CommandResult queued = research(p, shed, MINING);
        assertThat(queued.firstReason()).as("the pair of a queued tech").isEqualTo(RejectReason.LIMIT);
        assertThat(p.s.treasury().stock(0, 0)).as("nothing paid").isEqualTo(stock);
        assertThat(p.s.buildings().queues().queueSize(shed)).isEqualTo(1);
        assertThat(untilResearched(p, 1_300)).isTrue();
        assertThat(research(p, shed, MINING).firstReason())
                .as("the pair of a researched tech")
                .isEqualTo(RejectReason.LIMIT);
        assertThat(p.s.progress().researched(0, MINING)).isFalse();
    }

    @Test
    void aPairQueuedInAnotherBuildingIsRefusedToo() {
        ProdScene p = ProdScene.of(5_000, 5_000);
        grantStone(p);
        int first = storehouse(p);
        int second = storehouse(p);
        assertThat(research(p, first, MINING).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(research(p, second, LUMBER).firstReason()).isEqualTo(RejectReason.LIMIT);
    }

    @Test
    void theLintRefusesAOneSidedPairAndAnUnknownOrSelfExclusion() {
        TemplateCompiler.Result oneSided = compileWith(LUMBER, List.of(), "strategos:guild_mining");
        assertThat(oneSided.problems())
                .extracting(pr -> pr.pointer() + " " + pr.message())
                .anyMatch(m -> m.contains("/excludes/0") && m.contains("does not exclude"));
        assertThat(oneSided.set().tech(MINING))
                .as("the one-sided tech is left out")
                .isNotNull();

        TemplateCompiler.Result unknown = compileWith(LUMBER, List.of("strategos:nothing"), null);
        assertThat(unknown.problems()).extracting(pr -> pr.message()).anyMatch(m -> m.contains("unknown tech"));

        TemplateCompiler.Result self = compileWith(LUMBER, List.of("strategos:guild_lumber"), null);
        assertThat(self.problems()).extracting(pr -> pr.message()).anyMatch(m -> m.contains("exclude itself"));
    }

    /**
     * The built-in set with {@code tech}'s {@code excludes} replaced by {@code names}; {@code other}'s pair of {@code
     * tech} is dropped when {@code dropReverse} is set.
     */
    private static TemplateCompiler.Result compileWith(TechId tech, List<String> names, String dropReverse) {
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : SET.all()) {
            flats.add(new TemplateCompiler.Flat(
                    t.id(), t.netIndex(), SET.flattened(t.id()).deepCopy()));
        }
        List<TemplateCompiler.TechFlat> techs = new ArrayList<>();
        for (TechDef t : SET.techs()) {
            JsonObject json = t.toJson();
            if (t.id().equals(tech) && dropReverse == null) {
                var arr = new com.google.gson.JsonArray();
                names.forEach(arr::add);
                json.add("excludes", arr);
            } else if (t.id().equals(MINING) && dropReverse != null) {
                json.remove("excludes"); // the lumber guild still names it; it names no one back
            }
            techs.add(new TemplateCompiler.TechFlat(
                    t.id(),
                    t.netIndex(),
                    JsonParser.parseString(json.toString()).getAsJsonObject()));
        }
        return TemplateCompiler.fromFlattened(flats, techs);
    }

    private static void grantStone(ProdScene p) {
        p.s.progress().complete(0, new TechId("strategos:phase_town")); // the guilds are Town techs
        Techs.reapply(p.s, null);
        p.h.submit(PlayerSlot.ADMIN, new dev.strategos.core.protocol.Grant(new FactionSlot(0), "stone", 5_000));
        p.h.step();
    }

    private static int storehouse(ProdScene p) {
        int n = p.s.buildings().handles().length;
        p.h.submit(PlayerSlot.ADMIN, new SpawnBuilding(STOREHOUSE, new FactionSlot(0), 20 + 14 * n, 30, 0));
        p.h.step();
        int[] all = p.s.buildings().handles();
        return all[all.length - 1];
    }

    private static CommandResult research(ProdScene p, int building, TechId tech) {
        p.h.submit(ProdScene.P0, new Research(building, tech));
        p.h.step();
        return p.h.results().get(p.h.results().size() - 1);
    }

    private static boolean untilResearched(ProdScene p, int limit) {
        for (int t = 0; t < limit; t++) {
            for (OutputEvent e : p.h.step().events()) {
                if (e instanceof TechResearched) {
                    return true;
                }
            }
        }
        return false;
    }
}
