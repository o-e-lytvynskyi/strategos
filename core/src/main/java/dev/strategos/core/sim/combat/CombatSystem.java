package dev.strategos.core.sim.combat;

import dev.strategos.core.content.CorePaths;
import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.Attacked;
import dev.strategos.core.sim.BuildingDestroyed;
import dev.strategos.core.sim.BuildingHurt;
import dev.strategos.core.sim.EntityStore;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MatchSystem;
import dev.strategos.core.sim.Notice;
import dev.strategos.core.sim.Phase;
import dev.strategos.core.sim.UnitDied;
import dev.strategos.core.sim.UnitHurt;
import dev.strategos.core.sim.build.BuildingStore;
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
        List<Integer> falling = new ArrayList<>();
        double[] resistance = new double[3]; // a unit's hack, pierce and crush: no unit takes fire
        for (Damage d : packets) {
            int target = d.target();
            if (BuildingStore.isBuilding(target)) {
                hitBuilding(s, d, falling);
                continue;
            }
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
                for (int type = 0; type < resistance.length; type++) {
                    resistance[type] = stats.resistance(i, type);
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
        falling.sort(null);
        for (int handle : falling) {
            FactionSlot faction = new FactionSlot(s.buildings().faction(handle));
            s.outputs().add(new BuildingDestroyed(handle, faction));
            s.outputs().add(new Notice(faction, NoticeKind.UNIT_LOST, handle, "destroyed"));
            s.destroyBuilding(handle);
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

    /**
     * A blow on a building (BLD-01, CMB-03): as {@link DamageFormula} gives it against the building's template's
     * resistances, fire's from {@code Resistance/fire}, or all that is left for a lethal one; a building that reaches
     * its most falls once the tick's blows are dealt.
     */
    private static void hitBuilding(MatchState s, Damage d, List<Integer> falling) {
        int target = d.target();
        if (!s.buildings().alive(target) || falling.contains(target)) {
            return;
        }
        double max = s.buildingMaxHp(target);
        double taken = s.buildings().damage(target);
        double dealt = d.lethal() ? max - taken : DamageFormula.dealt(d, resistance(s, target), classesOf(s, target));
        s.buildings().setDamage(target, Math.min(max, taken + dealt));
        if (taken + dealt >= max) {
            falling.add(target);
        }
        s.outputs().add(new BuildingHurt(target, d.attacker(), (float) dealt));
    }

    /** The path of a building's fire resistance (CMB-03), read like its template's other values. */
    public static final String RESIST_FIRE = "Resistance/fire";

    /** A torch's fire a blow, its wind-up and its period in ticks (CMB-03, the template's structure attack). */
    public static final String TORCH_FIRE = "Attack/Structure/fire";

    public static final String TORCH_PREPARE = "Attack/Structure/prepare";
    public static final String TORCH_REPEAT = "Attack/Structure/repeat";

    /** Building {@code handle}'s resistance levels: hack, pierce, crush and fire, from its template. */
    static double[] resistance(MatchState s, int handle) {
        int template = s.buildings().template(handle);
        int faction = s.buildings().faction(handle);
        String[] paths = {CorePaths.RESIST_HACK, CorePaths.RESIST_PIERCE, CorePaths.RESIST_CRUSH, RESIST_FIRE};
        double[] out = new double[paths.length];
        for (int k = 0; k < paths.length; k++) {
            int p = s.stats().path(paths[k]);
            double v = p < 0 ? Double.NaN : s.stats().blockValue(template, faction, p);
            out[k] = Double.isFinite(v) ? v : 0;
        }
        return out;
    }

    private static List<String> classesOf(MatchState s, int handle) {
        return s.templates().byIndex(s.buildings().template(handle)).classes();
    }
}
