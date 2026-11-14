package dev.strategos.core.sim.prod;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.ClassFilter;
import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TechEffect;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.FactionLook;
import dev.strategos.core.sim.Progress;
import dev.strategos.core.sim.stats.Modifier;
import dev.strategos.core.sim.stats.ModifierOp;
import dev.strategos.core.sim.stats.StatBreakdown;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TECH-03 acceptance 1 in L1: each shipped technology's effect is exactly the modifier its faction's stats hold. */
class TechEffectTest {
    private static final TemplateSet SET = TemplateSet.builtin();

    @Test
    void eachEffectEqualsItsModifier() {
        for (TechDef tech : SET.techs()) {
            ProdScene p = ProdScene.of(0, 0);
            p.s.progress().complete(0, tech.id());
            Techs.reapply(p.s, null);
            for (TechEffect e : tech.effects()) {
                Modifier expected = new Modifier(
                        e.path(),
                        ModifierOp.valueOf(e.op().name()),
                        e.value(),
                        e.tokens() == null ? "" : e.tokens(),
                        e.filter(),
                        e.stage(),
                        e.priority(),
                        Progress.sourceId(tech.netIndex()));
                ClassFilter filter = ClassFilter.compile(e.filter(), SET.tokens());
                int matched = 0;
                for (UnitTemplate t : SET.all()) {
                    boolean hits = filter.matches(SET.tokens().bits(t.classes()));
                    int path = e.op() == TechEffect.Op.TOKENS
                            ? p.s.stats().tokenPath(e.path())
                            : p.s.stats().path(e.path());
                    boolean has = path >= 0 && hasPath(p, t, e, path);
                    if (!hits || !has) {
                        continue;
                    }
                    matched++;
                    if (e.op() == TechEffect.Op.TOKENS) {
                        List<String> now = p.s.stats().blockTokens(t.netIndex(), 0, path);
                        assertThat(now)
                                .as("%s's tokens at %s of %s", tech.id(), e.path(), t.id())
                                .containsAll(List.of(e.tokens().replace("+", "").split(" ")));
                        continue;
                    }
                    StatBreakdown b = p.s.stats().blockBreakdown(t.netIndex(), 0, e.path());
                    assertThat(b).isNotNull();
                    assertThat(b.modifiers())
                            .as("%s's modifier at %s of %s", tech.id(), e.path(), t.id())
                            .contains(expected);
                    double result = switch (e.op()) {
                        case MUL -> b.base() * e.value();
                        case ADD -> b.base() + e.value();
                        case REPLACE -> e.value();
                        case TOKENS -> Double.NaN;
                    };
                    long others = b.modifiers().stream()
                            .filter(m -> m.path().equals(e.path()))
                            .count();
                    if (others == 1) {
                        assertThat(b.result())
                                .as("%s at %s of %s equals its modifier exactly", tech.id(), e.path(), t.id())
                                .isEqualTo(Math.min(result, max(p, e.path())));
                    }
                }
                assertThat(matched)
                        .as("%s's effect %s reaches a template that has the path", tech.id(), e.path())
                        .isPositive();
            }
        }
    }

    @Test
    void theMilitaryTechsRaiseTheTiersAndNothingElseDoes() {
        for (TechDef tech : SET.techs()) {
            boolean raises = tech.effects().stream().anyMatch(e -> e.path().startsWith("Look/"));
            assertThat(raises)
                    .as(tech.id() + " raises a tier")
                    .isEqualTo(tech.id().value().endsWith("iron_weapons")
                            || tech.id().value().endsWith("chainmail"));
        }
        ProdScene p = ProdScene.of(0, 0);
        assertThat(p.h.core().queries().factionLooks()).containsOnly(FactionLook.BASE);
        p.s.progress().complete(0, new dev.strategos.core.shared.TechId("strategos:iron_weapons"));
        Techs.reapply(p.s, null);
        assertThat(p.h.core().queries().factionLooks().get(0)).isEqualTo(new FactionLook(1, 0));
        p.s.progress().complete(0, new dev.strategos.core.shared.TechId("strategos:chainmail"));
        Techs.reapply(p.s, null);
        assertThat(p.h.core().queries().factionLooks().get(0)).isEqualTo(new FactionLook(1, 1));
        assertThat(p.h.core().queries().factionLooks().get(1))
                .as("the other faction's gear is its own")
                .isEqualTo(FactionLook.BASE);
    }

    private static boolean hasPath(ProdScene p, UnitTemplate t, TechEffect e, int path) {
        if (e.op() == TechEffect.Op.TOKENS) {
            return p.s.stats().blockTokens(t.netIndex(), 0, path) != null;
        }
        return !Double.isNaN(SET.value(t.netIndex(), path));
    }

    private static double max(ProdScene p, String path) {
        return path.startsWith("Look/") ? CorePaths.MAX_LOOK : Double.POSITIVE_INFINITY;
    }
}
