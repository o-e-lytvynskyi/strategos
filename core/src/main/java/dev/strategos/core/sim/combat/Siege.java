package dev.strategos.core.sim.combat;

import dev.strategos.core.content.CorePaths;
import dev.strategos.core.sim.MatchState;
import java.util.List;

/**
 * How a unit strikes a building (CMB-03, CMB-09): with a torch, its {@code Attack/Structure/fire} on the torch's own
 * wind-up and period, or, with none, the {@code Attack/Melee/crush} of its melee attack on that attack's, as a ram's log
 * does. A building whose classes the unit's {@code Attack/Melee/restrictedClasses} name, a field for a ram, is never
 * struck. Nothing else of a melee attack reaches a building: a spear's hack is for units.
 */
public final class Siege {
    private Siege() {}

    /** Unit {@code i}'s torch fire a blow; 0 for none. */
    public static double fire(MatchState s, int i) {
        return value(s, i, CombatSystem.TORCH_FIRE);
    }

    /** Unit {@code i}'s crush a blow against a building, the log's: 0 when it carries a torch instead. */
    public static double crush(MatchState s, int i) {
        return fire(s, i) > 0 ? 0 : value(s, i, CorePaths.MELEE_CRUSH);
    }

    /** Whether unit {@code i} can strike building {@code target}: a blow it deals, and a building it may strike. */
    public static boolean canStrike(MatchState s, int i, int target) {
        if (!(fire(s, i) > 0 || crush(s, i) > 0)) {
            return false;
        }
        List<String> classes =
                s.templates().byIndex(s.buildings().template(target)).classes();
        return !s.stats().restricts(i, classes);
    }

    /** The wind-up before unit {@code i}'s first blow, in ticks. */
    public static int prepare(MatchState s, int i) {
        return (int) value(s, i, fire(s, i) > 0 ? CombatSystem.TORCH_PREPARE : CorePaths.MELEE_PREPARE);
    }

    /** The ticks between unit {@code i}'s blows; 20 for a template that gives none. */
    public static int repeat(MatchState s, int i) {
        double v = value(s, i, fire(s, i) > 0 ? CombatSystem.TORCH_REPEAT : CorePaths.MELEE_REPEAT);
        return v > 0 ? (int) v : 20;
    }

    /** The packet of unit {@code i}'s blow on building {@code target}: fire, or crush. */
    public static Damage blow(MatchState s, int i, int attacker, int target) {
        return new Damage(target, attacker, 0, 0, (float) crush(s, i), (float) fire(s, i), List.of(), false);
    }

    private static double value(MatchState s, int i, String path) {
        int p = s.stats().path(path);
        double v = p < 0 ? Double.NaN : s.stats().read(i, p);
        return Double.isFinite(v) && v >= 0 ? v : 0;
    }
}
