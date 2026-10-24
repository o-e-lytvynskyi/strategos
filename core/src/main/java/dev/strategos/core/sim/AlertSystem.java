package dev.strategos.core.sim;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.build.BuildingStore;
import java.util.List;

/**
 * P7, WORLD: under-attack alerts (UI-12). Each of the tick's blows by one faction on another's unit or building is an
 * attack at the target; an attack within {@value #RADIUS} blocks of an alert of the same faction whose last blow is
 * less than {@value #QUIET} ticks old sustains that alert, any other raises a new one ({@link UnderAttack}). The alerts
 * live in the state's {@link Alerts}, saved but not hashed: a new one wakes its computer player, so a restored core
 * must remember them (MATCH-04).
 */
final class AlertSystem implements MatchSystem {
    /** How far apart two attacks are still one. */
    static final double RADIUS = 32;

    /** How long an alert lasts without a blow: 30 seconds. */
    static final long QUIET = 600;

    @Override
    public Phase phase() {
        return Phase.WORLD;
    }

    @Override
    public void run(MatchState s) {
        long tick = s.tick();
        List<Alerts.Alert> alerts = s.alerts().list;
        alerts.removeIf(a -> tick - a.last() >= QUIET);
        List<OutputEvent> outputs = s.outputs();
        int n = outputs.size(); // the alerts this adds are not attacks
        for (int k = 0; k < n; k++) {
            OutputEvent e = outputs.get(k);
            int target;
            int attacker;
            if (e instanceof UnitHurt hurt) {
                target = hurt.handle();
                attacker = hurt.attacker();
            } else if (e instanceof BuildingHurt hurt) {
                target = hurt.handle();
                attacker = hurt.attacker();
            } else {
                continue;
            }
            if (attacker == Handle.NONE || !s.entities().isAlive(attacker)) {
                continue;
            }
            int by = s.identity().faction(Handle.index(attacker));
            double x;
            double z;
            int faction;
            if (BuildingStore.isBuilding(target)) {
                if (!s.buildings().alive(target)) {
                    continue;
                }
                faction = s.buildings().faction(target);
                Footprint at = s.footprintOf(target);
                x = at.anchorX() + at.spanX() / 2.0;
                z = at.anchorZ() + at.spanZ() / 2.0;
            } else {
                if (!s.entities().isAlive(target)) {
                    continue;
                }
                int i = Handle.index(target);
                faction = s.identity().faction(i);
                x = s.motion().x(i);
                z = s.motion().z(i);
            }
            if (faction == by || faction >= s.config().factions()) { // Gaia is never alerted
                continue;
            }
            attacked(s, faction, x, z, tick);
        }
    }

    private void attacked(MatchState s, int faction, double x, double z, long tick) {
        List<Alerts.Alert> alerts = s.alerts().list;
        for (int k = 0; k < alerts.size(); k++) {
            Alerts.Alert a = alerts.get(k);
            if (a.faction() == faction && DetMath.length(a.x() - x, a.z() - z) <= RADIUS) {
                alerts.set(k, new Alerts.Alert(faction, a.x(), a.z(), tick));
                return;
            }
        }
        alerts.add(new Alerts.Alert(faction, x, z, tick));
        s.outputs().add(new UnderAttack(new FactionSlot(faction), x, z));
        s.aiSlots().wake(faction); // a computer player under attack thinks at once (AI-01)
    }
}
