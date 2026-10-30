package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.diplomacy.DiplomacyState;

/**
 * Trusted server setup for a match's first tick, journaled before commands. This is not a client payload or a grant
 * command: an adapter must authorize lobby/start intents before creating it. A locked setup can be installed once;
 * later binding changes and player grant/revoke intents belong to their own validated command paths (M7 T8).
 */
@CoreApi
public record DiplomacySetup(DiplomacyState.Snapshot snapshot) implements InputEvent {
    public DiplomacySetup {
        DiplomacyState validated = DiplomacyState.restore(snapshot);
        if (!validated.teamsLocked()) {
            throw new IllegalArgumentException("match diplomacy setup must lock teams");
        }
        snapshot = validated.snapshot();
    }
}
