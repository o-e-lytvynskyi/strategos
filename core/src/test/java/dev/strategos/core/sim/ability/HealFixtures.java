package dev.strategos.core.sim.ability;

import static dev.strategos.core.sim.ability.AbilityFixtures.state;

import dev.strategos.core.content.AbilityDef;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.testkit.SimHarness;

/** The healer of the built-in set and the soldiers around it, for CMB-12's tests. */
final class HealFixtures {
    static final TemplateId HEALER = new TemplateId("strategos:unit/healer");
    static final TemplateId WORKER = new TemplateId("strategos:unit/worker");

    private HealFixtures() {}

    /** The healer's {@code heal} ability, as the data declares it. */
    static AbilityDef heal() {
        return TemplateSet.builtin().ability("heal");
    }

    /** The hit points one heal restores, from the data. */
    static double amount() {
        return heal().effects().get(0).params().get("amount").getAsDouble();
    }

    static int healer(SimHarness h, FactionSlot faction, double x, double z) {
        return AbilityFixtures.spawn(h, HEALER, faction, x, z);
    }

    static int infantry(SimHarness h, FactionSlot faction, double x, double z) {
        return AbilityFixtures.spawn(h, TemplateSet.INFANTRY, faction, x, z);
    }

    static int worker(SimHarness h, FactionSlot faction, double x, double z) {
        return AbilityFixtures.spawn(h, WORKER, faction, x, z);
    }

    static void wound(SimHarness h, int unit, double taken) {
        state(h).health().setDamage(Handle.index(unit), taken);
    }

    static double damage(SimHarness h, int unit) {
        return state(h).health().damage(Handle.index(unit));
    }
}
