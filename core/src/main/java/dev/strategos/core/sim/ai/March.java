package dev.strategos.core.sim.ai;

import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.FormationMove;
import dev.strategos.core.protocol.FormationShape;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.UnitView;
import java.util.List;

/**
 * Marches in formation (AI-09, ORD-06): a wave that has a long way to the enemy's front, the script's {@code depth.march}
 * blocks or more, leaves as one box that keeps its places and walks at its slowest member's speed. The fight's tactics
 * leave the formation's members alone until the wave nears the enemy ({@link #holding}); the attack orders they are
 * given then dissolve the formation.
 */
final class March {
    /** The fewest soldiers that march in formation: a handful walks as it is. */
    static final int FEWEST = 4;

    private March() {}

    /** The order that sends {@code handles}, the army, to {@code front}: a box formation on a long march, else an attack-move. */
    static Command order(StrategyScript script, List<UnitView> soldiers, int[] handles, Vec2d front) {
        UnitSet set = UnitSet.of(handles);
        int far = script.depth().march();
        if (far > 0 && handles.length >= FEWEST && distance(soldiers, front) >= far) {
            return FormationMove.replace(set, front, FormationShape.BOX);
        }
        return AttackMove.replace(set, front);
    }

    /** How far the middle of {@code units} is from {@code at}, in blocks; 0 for none. */
    static double distance(List<UnitView> units, Vec2d at) {
        if (units.isEmpty()) {
            return 0;
        }
        double cx = 0;
        double cz = 0;
        for (UnitView u : units) {
            cx += u.x();
            cz += u.z();
        }
        return DetMath.length(cx / units.size() - at.x(), cz / units.size() - at.z());
    }

    /**
     * Whether the wave's middle is still more than {@link Tactics#ARROWS} blocks from the enemy's aim with no enemy soldier
     * in reach: the soldiers in a formation are then left to march.
     */
    static boolean holding(StrategyScript script, Base base, List<UnitView> wave, List<UnitView> near) {
        return script.depth().march() > 0
                && near.isEmpty()
                && distance(wave, new Vec2d(base.enemyX(), base.enemyZ())) > Tactics.ARROWS;
    }
}
