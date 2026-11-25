package dev.strategos.core.sim.ai;

import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.Capture;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.capture.CaptureMaths;

/**
 * Capture or burn (AI-08, M9 §2.12): a building that can be captured is, instead of burnt, when the squad's capture
 * effect at the building's health beats its owner's regeneration, garrison included, by half. Pure reads of the view.
 */
final class Seize {
    /** The effect must exceed the regeneration by this factor: 3 over 2, half as much again. */
    private static final long MARGIN_NUM = 3;

    private static final long MARGIN_DEN = 2;

    /** How long a capture of one building may last before the wave burns it instead: a minute and a half. */
    static final long GIVE_UP = 1_800;

    private Seize() {}

    /** Whether {@code b} can be captured at all. */
    static boolean capturable(AiView view, BuildingView b) {
        return view.captureTotal(b.handle()) > 0;
    }

    /** The summed capture strength of {@code handles}, in thousandths a second. */
    static long strength(AiView view, int[] handles) {
        long sum = 0;
        for (int h : handles) {
            sum += view.captureStrength(h);
        }
        return sum;
    }

    /** Whether a squad of summed {@code strength} takes {@code b} by half faster than its owner holds it. */
    static boolean beats(AiView view, long strength, BuildingView b) {
        if (!capturable(view, b)) {
            return false;
        }
        long effect = CaptureMaths.effect(strength, b.hitPoints(), b.maxHitPoints());
        return effect * MARGIN_DEN > view.captureRegen(b.handle()) * MARGIN_NUM;
    }

    /** Whether {@code handles} take {@code b} by half faster than it holds. */
    static boolean beats(AiView view, int[] handles, BuildingView b) {
        return beats(view, strength(view, handles), b);
    }

    /** The order that sends {@code handles} at {@code b}: a capture when {@code capture}, else an attack. */
    static Command order(int[] handles, BuildingView b, boolean capture) {
        UnitSet set = UnitSet.of(handles);
        return capture ? new Capture(set, b.handle()) : Attack.replace(set, b.handle());
    }
}
