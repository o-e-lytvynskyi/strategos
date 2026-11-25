package dev.strategos.core.sim.ability;

import dev.strategos.core.content.AbilityDef;
import dev.strategos.core.content.AbilityKinds;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.combat.Engagement;
import java.util.List;

/**
 * The target pickers of autocast abilities (UNIT-10), a closed registry under the ids {@link AbilityKinds#pickers()}
 * lists: given a user and an ability, the handle of the unit to use it on, or {@link Handle#NONE} when there is
 * none. {@code self} is the user itself; {@code nearest_enemy_in_range} the nearest enemy within the ability's range
 * (the lower index on a tie). A picker reads the state, writes nothing (but the filter it caches once) and allocates nothing per call; CMB-12 adds
 * {@code most_wounded_ally} here and in the vocabulary together.
 */
public final class AbilityTargets {
    private AbilityTargets() {}

    /** The ids of the registry, ascending: the same as {@link AbilityKinds#pickers()}. */
    public static List<String> ids() {
        return List.of("nearest_enemy_in_range", "self");
    }

    /** The unit picker {@code id} chooses for unit handle {@code user} using {@code def}, or {@link Handle#NONE}. */
    public static int pick(String id, MatchState s, int user, AbilityDef def) {
        return switch (id) {
            case AbilityKinds.SELF -> user;
            case AbilityKinds.NEAREST_ENEMY_IN_RANGE -> nearestEnemy(s, user, def);
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
}
