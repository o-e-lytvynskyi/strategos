package dev.strategos.core.testkit;

import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.MatchQueries;
import java.util.List;

/**
 * A perf soak's scene on the proving ground (PERF-06's first loop, PERF-07's economy): the commands that build and keep
 * its load, the same for the same match, so the server's soak and an L1 run play alike.
 */
public interface SoakScene {
    /** A command and who sends it: the admin, or a faction's seat for a placement. */
    record Issue(PlayerSlot issuer, Command command) {}

    /** A scene's scale: how a soak makes it from the map's start slots, and the units it waits for. */
    interface Spec {
        SoakScene create(List<StartSlot> starts);

        /** The units the scene holds at its fullest, the per-unit figures' count. */
        int units();

        /** The units its setup spawns: a soak's window waits for them and their bodies. */
        int unitsAtStart();
    }

    /** The first tick's commands. */
    List<Issue> setup(TemplateSet templates);

    /** The commands after the step of {@code q.tick()}, for the next. */
    List<Issue> next(MatchQueries q);

    /** The blows the scene deals with them, as vanilla damage: none but a repair's. */
    default List<ExternalDamage> damage(MatchQueries q) {
        return List.of();
    }

    /** A placement's outcome, as the caller heard it. */
    void placementResult(boolean accepted);

    long placed();

    long refusedPlacements();

    int waves();
}
