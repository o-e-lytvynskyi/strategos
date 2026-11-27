package dev.strategos.core.sim.ai;

import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.Target;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UseAbility;
import dev.strategos.core.shared.AbilityId;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.sim.UnitView;
import java.util.ArrayList;
import java.util.List;

/**
 * The charge on the approach (AI-09, UNIT-10): a soldier of the wave that has the script's {@code depth.charge} ability
 * off cooldown uses it once an enemy soldier is within its range but not yet in melee. The order goes to the front of
 * the unit's queue, so the attack it was on resumes once the ability has fired.
 */
final class Charge {
    /** Closer than this the soldier is in melee already: a charge would be wasted. */
    static final double MELEE = 4;

    private Charge() {}

    /** Sends the charge for the soldiers of {@code wave} that may use it; {@code near} is the enemy soldiers in reach. */
    static void use(
            AiView view,
            AiBudget budget,
            CommandSink sink,
            Depth.Charge charge,
            List<UnitView> wave,
            List<UnitView> near) {
        if (!charge.on() || near.isEmpty()) {
            return;
        }
        List<Integer> going = new ArrayList<>();
        for (UnitView u : wave) {
            if (!view.abilityReady(u, charge.ability())) {
                continue;
            }
            double nearest = Double.POSITIVE_INFINITY;
            for (UnitView e : near) {
                nearest = Math.min(nearest, DetMath.length(e.x() - u.x(), e.z() - u.z()));
            }
            if (nearest <= charge.range() && nearest > MELEE) {
                going.add(u.handle());
            }
        }
        if (!going.isEmpty() && budget.spend(10)) {
            sink.send(new UseAbility(
                    UnitSet.of(going.stream().mapToInt(Integer::intValue).toArray()),
                    new AbilityId(charge.ability()),
                    Target.none(),
                    QueueMode.PUSH_FRONT));
        }
    }
}
