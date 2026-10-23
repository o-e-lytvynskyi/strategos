package dev.strategos.core.sim.combat;

import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.Attacked;
import dev.strategos.core.sim.EntityStore;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MatchSystem;
import dev.strategos.core.sim.Notice;
import dev.strategos.core.sim.Phase;
import dev.strategos.core.sim.UnitDied;
import dev.strategos.core.sim.UnitHurt;
import dev.strategos.core.sim.stats.StatService;
import java.util.ArrayList;
import java.util.List;

/**
 * The COMBAT phase's damage (CMB-01): every packet queued this tick, vanilla blows from INGEST and the units' own hits,
 * applies in its order to a target still alive, as {@link DamageFormula} gives it or, lethal, all that is left. Each
 * hit is a {@link UnitHurt}, and a unit that lives through another unit's hit hears it as {@link Attacked}; a unit
 * whose hit points reach 0 takes no more this tick, and once all have applied the
 * dead leave the match in handle order, each a {@link UnitDied} and a notice to its faction. No hit is ever lost:
 * nothing like vanilla's invulnerability frames exists here.
 */
public final class CombatSystem implements MatchSystem {
    @Override
    public Phase phase() {
        return Phase.COMBAT;
    }

    @Override
    public void run(MatchState s) {
        List<Damage> packets = s.takeDamage();
        if (packets.isEmpty()) {
            return;
        }
        EntityStore entities = s.entities();
        StatService stats = s.stats();
        HealthStore health = s.health();
        List<int[]> dying = new ArrayList<>(); // handle, last attacker
        double[] resistance = new double[3];
        for (Damage d : packets) {
            int target = d.target();
            if (!entities.isAlive(target)) {
                continue;
            }
            int i = Handle.index(target);
            double max = stats.maxHealth(i);
            double taken = health.damage(i);
            double left = max - taken;
            if (left <= 0) {
                continue; // already dying this tick
            }
            double dealt;
            if (d.lethal()) {
                dealt = left;
            } else {
                for (DamageType type : DamageType.values()) {
                    resistance[type.index()] = stats.resistance(i, type.index());
                }
                dealt = DamageFormula.dealt(d, resistance, stats.classes(i));
            }
            health.setDamage(i, taken + dealt);
            s.outputs().add(new UnitHurt(target, d.attacker(), (float) dealt));
            if (taken + dealt >= max) {
                dying.add(new int[] {target, d.attacker()});
            } else if (d.attacker() != Handle.NONE && entities.isAlive(d.attacker())) {
                s.bus().publish(new Attacked(target, d.attacker())); // an aggressive unit hits back (CMB-02)
            }
        }
        dying.sort((a, b) -> Integer.compare(a[0], b[0]));
        for (int[] dead : dying) {
            int handle = dead[0];
            FactionSlot faction = new FactionSlot(s.identity().faction(Handle.index(handle)));
            s.outputs().add(new UnitDied(handle, faction, dead[1]));
            s.outputs().add(new Notice(faction, NoticeKind.UNIT_LOST, handle, "died"));
            s.removeUnit(handle);
        }
    }
}
