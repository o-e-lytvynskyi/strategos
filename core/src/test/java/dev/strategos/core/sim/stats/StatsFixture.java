package dev.strategos.core.sim.stats;

import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.IdentityStore;
import dev.strategos.core.sim.MatchBus;
import dev.strategos.core.testkit.TestTemplates;

/**
 * The stats tests' templates ({@code templates/stats}): archer, captain (with a production list), champion, horseman
 * and spearman, over a base of 100 health, 0.2 blocks a tick and 45 degrees a tick; two factions.
 */
final class StatsFixture {
    static final String HEALTH = "Health/max";
    static final String HACK = "Attack/Melee/damage/hack";
    static final String UNITS = "Production/units";
    static final TemplateSet SET = compile();

    private StatsFixture() {}

    static StatService service() {
        return service(new MatchBus(), new IdentityStore());
    }

    static StatService service(MatchBus bus, IdentityStore identity) {
        return new StatService(SET, 2, identity, bus);
    }

    static int template(String name) {
        var t = SET.byId(new TemplateId("strategos:unit/" + name));
        if (t == null) {
            throw new IllegalArgumentException("no template " + name);
        }
        return t.netIndex();
    }

    private static TemplateSet compile() {
        TemplateCompiler.Result result = TestTemplates.compile("templates/stats");
        if (!result.ok()) {
            throw new IllegalStateException("the stats templates do not compile: " + result.problems());
        }
        return result.set();
    }
}
