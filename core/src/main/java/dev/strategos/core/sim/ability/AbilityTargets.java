package dev.strategos.core.sim.ability;

import dev.strategos.core.content.AbilityDef;
import dev.strategos.core.content.AbilityKinds;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.combat.Engagement;
import dev.strategos.core.sim.spatial.UnitFilter;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import org.jspecify.annotations.Nullable;

/**
 * The target pickers of autocast abilities (UNIT-10), a closed registry under the ids {@link AbilityKinds#pickers()}
 * lists: given a user and an ability, the handle of the unit to use it on, or {@link Handle#NONE} when there is
 * none. {@code self} is the user itself; {@code nearest_enemy_in_range} the nearest enemy within the ability's range
 * (the lower index on a tie); {@code most_wounded_ally} the ally (the user's faction or an allied one, never the user itself)
 * within the range that lost the largest share of its maximum hit points and that a heal can mend ({@code Organic},
 * not {@code Siege}), the lower index on a tie. A picker reads the state, writes nothing (but the filter it caches
 * once) and allocates nothing per call.
 */
public final class AbilityTargets {
    private AbilityTargets() {}

    /** The ids of the registry, ascending: the same as {@link AbilityKinds#pickers()}. */
    public static List<String> ids() {
        return List.of("most_wounded_ally", "nearest_enemy_in_range", "self");
    }

    /** The unit picker {@code id} chooses for unit handle {@code user} using {@code def}, or {@link Handle#NONE}. */
    public static int pick(String id, MatchState s, int user, AbilityDef def) {
        return switch (id) {
            case AbilityKinds.SELF -> user;
            case AbilityKinds.NEAREST_ENEMY_IN_RANGE -> nearestEnemy(s, user, def);
            case AbilityKinds.MOST_WOUNDED_ALLY ->
                s.play().abilities().woundedScan().run(s, user, def);
            default -> throw new IllegalArgumentException("no target picker '" + id + "'");
        };
    }

    private static int nearestEnemy(MatchState s, int user, AbilityDef def) {
        int i = Handle.index(user);
        int faction = s.identity().faction(i);
        // body radii widen the reach a little: look a margin further, then hold the nearest to the real gap
        double look = def.range() + AbilityRules.BODY_MARGIN;
        int j = s.spatial()
                .nearest(
                        s.motion().x(i),
                        s.motion().z(i),
                        look,
                        s.play().abilities().enemiesOf(faction, s.config().factions()));
        if (j < 0
                || !s.entities().aliveAt(j)
                || s.garrisoned(j)
                || !s.allies()
                        .enemies(faction, s.identity().faction(j), s.config().factions())) {
            return Handle.NONE;
        }
        return Engagement.gap(s, i, j) <= def.range() ? s.entities().handleAt(j) : Handle.NONE;
    }

    /**
     * The scan behind {@code most_wounded_ally}: one instance per match, its fields set per call, so a pick allocates
     * nothing. The units it visits come in the grid's order; the result does not depend on it (the lower index wins a tie).
     */
    static final class WoundedScan implements IntConsumer {
        private @Nullable MatchState s;
        private int user;
        private double range;
        private int best;
        private double bestShare;
        private @Nullable UnitFilter filter;
        private int filterMask = -1;

        int run(MatchState state, int userHandle, AbilityDef def) {
            int u = Handle.index(userHandle);
            int self = state.identity().faction(u);
            int players = state.config().factions();
            int mask = 0;
            for (int f = 0; f < players; f++) {
                if (f == self || state.allies().allied(self, f)) {
                    mask |= 1 << f;
                }
            }
            if (filter == null || mask != filterMask) {
                filter = new UnitFilter(mask, 0);
                filterMask = mask;
            }
            s = state;
            user = u;
            range = def.range();
            best = -1;
            bestShare = 0;
            state.spatial()
                    .units()
                    .forEachInRadiusAnyOrder(
                            state.motion().x(u), state.motion().z(u), range + AbilityRules.BODY_MARGIN, filter, this);
            s = null;
            return best < 0 ? Handle.NONE : state.entities().handleAt(best);
        }

        @Override
        public void accept(int j) {
            MatchState state = Objects.requireNonNull(s);
            if (j == user || !state.entities().aliveAt(j) || state.garrisoned(j)) {
                return;
            }
            double taken = state.health().damage(j);
            if (taken <= 0 || !AbilityEffects.mendable(state, state.entities().handleAt(j))) {
                return;
            }
            double share = taken / state.stats().maxHealth(j);
            if (share < bestShare || (share == bestShare && best >= 0 && j > best)) {
                return;
            }
            if (Engagement.gap(state, user, j) <= range) {
                best = j;
                bestShare = share;
            }
        }
    }
}
