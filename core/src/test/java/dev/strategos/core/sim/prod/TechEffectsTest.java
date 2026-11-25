package dev.strategos.core.sim.prod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import dev.strategos.core.content.ClassFilter;
import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.Requirement;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TechEffect;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.Research;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.Progress;
import dev.strategos.core.sim.TechResearched;
import dev.strategos.core.sim.stats.StatBreakdown;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * TECH-05 acceptance 1 in L1, generated: one case per technology of the shipped set. Each case researches the tech
 * through the {@code Research} command in a fresh match (a phase, which no building researches, is completed), with
 * what it requires done first, and asserts that every path its effects name changes on exactly the templates its
 * filter selects by its own op and value, measured against a match with the same prerequisites but not the tech, and
 * that the tier it supersedes stops contributing.
 */
class TechEffectsTest {
    private static final TemplateSet SET = TemplateSet.builtin();
    private static final String[] RESOURCES = {"food", "wood", "stone", "iron"};

    @TestFactory
    Stream<DynamicTest> everyTechnologyDoesWhatItsDataSays() {
        return SET.techs().stream().map(tech -> dynamicTest(tech.id().value(), () -> check(tech)));
    }

    private static void check(TechDef tech) {
        Set<TechId> prerequisites = prerequisites(tech);
        Set<TechId> chain = supersededChain(tech);
        ProdScene before = scene(withoutChain(prerequisites, chain));
        ProdScene after = scene(prerequisites);
        if (tech.isPhase()) {
            after.s.progress().complete(0, tech.id());
            Techs.reapply(after.s, null);
        } else {
            researchByCommand(after, tech);
        }
        assertThat(after.s.progress().researched(0, tech.id()))
                .as("%s is researched", tech.id())
                .isTrue();
        for (TechId old : chain) {
            int source = Progress.sourceId(SET.tech(old).netIndex());
            for (UnitTemplate t : SET.all()) {
                for (TechEffect e : SET.tech(old).effects()) {
                    int path = e.op() == TechEffect.Op.TOKENS
                            ? -1
                            : after.s.stats().path(e.path());
                    StatBreakdown b = path < 0 ? null : after.s.stats().blockBreakdown(t.netIndex(), 0, e.path());
                    if (b != null) {
                        assertThat(b.modifiers())
                                .as(
                                        "%s superseded by %s stops contributing to %s of %s",
                                        old, tech.id(), e.path(), t.id())
                                .noneMatch(m -> m.sourceId() == source);
                    }
                }
            }
        }
        for (int i = 0; i < tech.effects().size(); i++) {
            TechEffect e = tech.effects().get(i);
            ClassFilter filter = ClassFilter.compile(e.filter(), SET.tokens());
            int matched = 0;
            for (UnitTemplate t : SET.all()) {
                boolean hits = filter.matches(SET.tokens().bits(t.classes()));
                if (e.op() == TechEffect.Op.TOKENS) {
                    int path = after.s.stats().tokenPath(e.path());
                    List<String> now = path < 0 ? null : after.s.stats().blockTokens(t.netIndex(), 0, path);
                    if (now == null) {
                        continue;
                    }
                    List<String> was = before.s.stats().blockTokens(t.netIndex(), 0, path);
                    for (String token : e.tokens().split(" ")) {
                        String id = token.substring(1);
                        assertThat(now.contains(id))
                                .as("%s's token %s at %s of %s", tech.id(), token, e.path(), t.id())
                                .isEqualTo(hits || (was != null && was.contains(id)));
                    }
                    matched += hits ? 1 : 0;
                    continue;
                }
                int pathId = after.s.stats().path(e.path());
                if (pathId < 0 || Double.isNaN(SET.value(t.netIndex(), pathId))) {
                    continue;
                }
                double was = before.s.stats().blockValue(t.netIndex(), 0, pathId);
                double now = after.s.stats().blockValue(t.netIndex(), 0, pathId);
                if (!hits) {
                    assertThat(now)
                            .as("%s's effect %d leaves %s of %s alone", tech.id(), i, e.path(), t.id())
                            .isEqualTo(was);
                    continue;
                }
                matched++;
                double expected = switch (e.op()) {
                    case MUL -> was * e.value();
                    case ADD -> was + e.value();
                    case REPLACE -> e.value();
                    case TOKENS -> Double.NaN;
                };
                if (e.path().startsWith("Look/")) {
                    expected = Math.min(expected, CorePaths.MAX_LOOK);
                }
                assertThat(now)
                        .as(
                                "%s's effect %d (%s %s %s) on %s of %s",
                                tech.id(), i, e.op(), e.value(), e.path(), e.path(), t.id())
                        .isCloseTo(expected, org.assertj.core.data.Offset.offset(1e-9));
                assertThat(now).as("it changed").isNotEqualTo(was);
            }
            assertThat(matched)
                    .as("%s's effect %d reaches a template that has %s", tech.id(), i, e.path())
                    .isPositive();
        }
    }

    @Test
    void everyTierCostsMoreTakesLongerAndGivesMoreThanTheOneItSupersedes() {
        int checked = 0;
        for (TechDef tech : SET.techs()) {
            if (tech.supersedes() == null) {
                continue;
            }
            checked++;
            TechDef old = SET.tech(tech.supersedes());
            assertThat(old).as("%s supersedes a tech of the set", tech.id()).isNotNull();
            assertThat(total(tech))
                    .as("%s costs more than %s", tech.id(), old.id())
                    .isGreaterThan(total(old));
            assertThat(tech.time())
                    .as("%s takes longer than %s", tech.id(), old.id())
                    .isGreaterThan(old.time());
            assertThat(tech.requires().toJson().toString())
                    .as("%s requires the tier it supersedes", tech.id())
                    .contains(old.id().value());
            for (TechEffect e : old.effects()) {
                TechEffect next = tech.effects().stream()
                        .filter(x -> x.path().equals(e.path()) && x.filter().equals(e.filter()))
                        .findFirst()
                        .orElse(null);
                assertThat(next)
                        .as("%s carries %s's effect on %s", tech.id(), old.id(), e.path())
                        .isNotNull();
                assertThat(next.op()).isEqualTo(e.op());
                assertThat(next.value())
                        .as("%s is a larger step at %s than %s", tech.id(), e.path(), old.id())
                        .isGreaterThan(e.value());
            }
        }
        assertThat(checked).as("the tiers of the shipped set").isGreaterThanOrEqualTo(14);
    }

    @Test
    void theShippedSetHasAboutThirtyTechnologies() {
        assertThat(SET.techs()).hasSize(32);
    }

    private static double total(TechDef tech) {
        return tech.cost().values().stream().mapToDouble(Double::doubleValue).sum();
    }

    /** The scene: a match with plenty of each resource and the techs {@code done} researched. */
    private static ProdScene scene(Set<TechId> done) {
        ProdScene p = ProdScene.of(50_000, 50_000);
        for (String r : List.of("stone", "iron")) {
            p.h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), r, 50_000));
        }
        p.h.step();
        for (TechId id : done) {
            p.s.progress().complete(0, id);
        }
        Techs.reapply(p.s, null);
        return p;
    }

    private static void researchByCommand(ProdScene p, TechDef tech) {
        int path = p.s.stats().tokenPath(Techs.RESEARCHER);
        UnitTemplate researcher = null;
        for (UnitTemplate t : SET.all()) {
            List<String> techs = t.isBuilding() ? p.s.stats().blockTokens(t.netIndex(), 0, path) : null;
            if (techs != null && techs.contains(tech.id().value())) {
                researcher = t;
                break;
            }
        }
        assertThat(researcher).as("a building researches %s", tech.id()).isNotNull();
        p.h.submit(PlayerSlot.ADMIN, new SpawnBuilding(researcher.id(), new FactionSlot(0), 30, 30, 0));
        p.h.step();
        int[] all = p.s.buildings().handles();
        int building = all[all.length - 1];
        p.h.submit(ProdScene.P0, new Research(building, tech.id()));
        p.h.step();
        assertThat(p.h.results().get(p.h.results().size() - 1).outcome())
                .as("researching %s at %s", tech.id(), researcher.id())
                .isEqualTo(CommandOutcome.ACCEPTED);
        for (long t = 0; t < tech.ticks() + 5; t++) {
            for (OutputEvent e : p.h.step().events()) {
                if (e instanceof TechResearched) {
                    return;
                }
            }
        }
    }

    /** What {@code tech} needs done first: each tech and phase its requirement names, transitively. */
    private static Set<TechId> prerequisites(TechDef tech) {
        Set<TechId> out = new LinkedHashSet<>();
        require(tech.requires(), out);
        return out;
    }

    private static void require(Requirement r, Set<TechId> out) {
        switch (r) {
            case Requirement.All all -> all.of().forEach(x -> require(x, out));
            case Requirement.Any any -> require(any.of().get(0), out);
            case Requirement.Tech t -> add(t.tech(), out);
            case Requirement.Phase p -> add(new TechId("strategos:phase_" + p.name()), out);
            case Requirement.Buildings b -> {} // a phase is completed, not researched, so its buildings are not needed
        }
    }

    private static void add(TechId id, Set<TechId> out) {
        TechDef def = SET.tech(id);
        if (def != null && out.add(id)) {
            require(def.requires(), out);
        }
    }

    private static Set<TechId> supersededChain(TechDef tech) {
        Set<TechId> chain = new LinkedHashSet<>();
        for (TechId id = tech.supersedes(); id != null; id = SET.tech(id).supersedes()) {
            chain.add(id);
        }
        return chain;
    }

    private static Set<TechId> withoutChain(Set<TechId> all, Set<TechId> chain) {
        Set<TechId> out = new LinkedHashSet<>(all);
        out.removeAll(chain);
        return out;
    }
}
