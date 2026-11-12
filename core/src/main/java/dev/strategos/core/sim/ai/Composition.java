package dev.strategos.core.sim.ai;

import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.UnitView;
import org.jspecify.annotations.Nullable;

/**
 * What a computer player trains against what the enemy fields (AI-03, M5 §2.11): the counters of the roster, spearmen
 * against horsemen, archers against spearmen, horsemen against archers. Each {@code replan} ticks it counts the
 * enemy's soldiers by role (all it may see: until fog, everything) and wants the counter of each in that share, a
 * share ({@code line}, a quarter by default) always spearmen, the line the archers stand behind. A building then trains, among the units it
 * can train and the stock pays for, the one furthest below its wanted share.
 */
final class Composition {
    static final TemplateId ARCHER = new TemplateId("strategos:unit/archer");
    static final TemplateId HORSEMAN = new TemplateId("strategos:unit/horseman");

    private static final int MASK = 0xFFFF;

    private Composition() {}

    /** Re-plans when {@code battle.replan()} ticks have passed since the last plan. */
    static void replan(AiView view, StrategyScript.Battle battle, AiMemory memory) {
        long planned = memory.get(Slots.PLAN_AT);
        if (battle.replan() <= 0 || (planned != 0 && view.tick() - (planned - 1) < battle.replan())) {
            return;
        }
        int[] seen = new int[4];
        for (UnitView e : view.enemyUnits()) {
            seen[view.role(e)]++;
        }
        int total = seen[AiView.ROLE_SPEAR] + seen[AiView.ROLE_ARCHER] + seen[AiView.ROLE_HORSE];
        long spear = 1000;
        long archer = 0;
        long horse = 0;
        if (total > 0) {
            long share = 1000L - battle.line();
            archer = share * seen[AiView.ROLE_SPEAR] / total;
            horse = share * seen[AiView.ROLE_ARCHER] / total;
            spear = 1000 - archer - horse; // the line and the counter to horsemen take the rest
        }
        memory.set(Slots.PLAN_AT, view.tick() + 1);
        memory.set(Slots.WANT, spear | archer << 16 | horse << 32);
    }

    /** The wanted share of {@code role} (a soldier's), in permille; spearmen alone before any plan. */
    static long want(AiMemory memory, int role) {
        long w = memory.get(Slots.WANT);
        if (w == 0) {
            return role == AiView.ROLE_SPEAR ? 1000 : 0;
        }
        return switch (role) {
            case AiView.ROLE_SPEAR -> w & MASK;
            case AiView.ROLE_ARCHER -> w >> 16 & MASK;
            default -> w >> 32 & MASK;
        };
    }

    /**
     * The soldier building {@code building} should train next, or null when it trains none the stock pays for. {@code
     * have} counts own soldiers by role, queued ones included; the caller adds the one picked. Under a reserve of spearmen
     * ({@code battle.spears()}) a spearman below the line's share is waited for when the stock cannot pay for him yet:
     * only spearmen burn buildings, and another kind bought meanwhile would keep the wood from him.
     */
    static @Nullable TemplateId pick(
            AiView view, StrategyScript script, AiMemory memory, TemplateId building, int[] have) {
        TemplateId[] units = {new TemplateId(script.soldier()), ARCHER, HORSEMAN};
        int total = have[AiView.ROLE_SPEAR] + have[AiView.ROLE_ARCHER] + have[AiView.ROLE_HORSE];
        if (script.battle().spears() > 0
                && view.trains(building, units[0])
                && have[AiView.ROLE_SPEAR] < script.battle().line() / 1000.0 * (total + 1.0)) {
            return view.affords(units[0]) ? units[0] : null;
        }
        TemplateId best = null;
        double deficit = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < units.length; k++) {
            if (!view.trains(building, units[k]) || !view.affords(units[k])) {
                continue;
            }
            int role = AiView.ROLE_SPEAR + k;
            double d = want(memory, role) / 1000.0 - have[role] / (total + 1.0);
            if (d > deficit) {
                deficit = d;
                best = units[k];
            }
        }
        return best;
    }
}
