package dev.strategos.core.sim.replicate;

import dev.strategos.core.protocol.UnitHud;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.UnitView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One player's {@code unit_hud} memory (NET-08, CMB-06): the hit points it was last sent for each unit index, whole for
 * an index never sent. {@link #changes} lists the units it sees whose hit points differ from that, and remembers them;
 * a unit that left the frame keeps its value, so it is resent only once it changes.
 */
@CoreApi
public final class HudMemory {
    private int[] sent = new int[0];

    public UnitHud changes(MatchQueries q, int matchId, FactionSlot viewer, Visibility visibility) {
        List<UnitHud.Entry> changed = new ArrayList<>();
        for (int handle : q.unitHandles()) {
            UnitView unit = q.unit(handle);
            if (unit == null || (!unit.faction().equals(viewer) && !visibility.sees(viewer, unit))) {
                continue;
            }
            int index = Handle.index(handle);
            int health = UnitHud.Entry.health(q.hitPoints(handle), q.maxHitPoints(handle));
            if (index >= sent.length) {
                int old = sent.length;
                sent = Arrays.copyOf(sent, Math.max(index + 1, old * 2));
                Arrays.fill(sent, old, sent.length, UnitHud.WHOLE);
            }
            if (sent[index] != health && changed.size() < UnitHud.MAX_UNITS) {
                sent[index] = health;
                changed.add(new UnitHud.Entry(index, health));
            }
        }
        return new UnitHud(matchId, q.tick(), changed);
    }
}
