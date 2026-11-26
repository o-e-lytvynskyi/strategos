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
import dev.strategos.core.sim.build.DamageStages;
import dev.strategos.core.sim.econ.Livestock;
import dev.strategos.core.sim.econ.Loot;
import dev.strategos.core.sim.rank.Ranks;
import dev.strategos.core.sim.stats.StatService;
import java.util.Arrays;
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
    // Per-tick scratch, reused across ticks (cleared by each run, never state): a death or a fall costs no row
    // allocation (the events it emits still allocate).
    private int[] dyingRows = new int[3 * 16]; // handle, last attacker, the faction its kill pays (CMB-13)
    private int dyingCount;
    private int[] fallingRows = new int[2 * 4]; // handle, the faction its destruction pays
    private int fallingCount;
    private long[] order = new long[16]; // handle << 32 | row, sorted to give the handle order
    // By unit index: hit points at 0 this tick. A live unit never starts a tick at 0 (the rank rescale keeps the ratio
    // below the new most, and a lethal unit leaves in the death pass of its own tick), so the bits are all a dying
    // attacker's check needs.
    private long[] dyingBits = new long[16];
    private final double[] resistance = new double[3]; // a unit's hack, pierce and crush: no unit takes fire

    @Override
    public Phase phase() {
        return Phase.COMBAT;
    }

    @Override
    public void run(MatchState s) {
        long start = s.combatTimes().start();
        apply(s);
        s.combatTimes().damage(start);
    }

    private void apply(MatchState s) {
        List<Damage> packets = s.takeDamage();
        if (packets.isEmpty()) {
            return;
        }
        EntityStore entities = s.entities();
        StatService stats = s.stats();
        HealthStore health = s.health();
        dyingCount = 0;
        fallingCount = 0;
        int words = (entities.highWater() >>> 6) + 1;
        if (dyingBits.length < words) {
            dyingBits = new long[Math.max(words, 2 * dyingBits.length)];
        } else {
            Arrays.fill(dyingBits, 0, words, 0L);
        }
        long[] bits = dyingBits;
        for (int k = 0, n = packets.size(); k < n; k++) {
            Damage d = packets.get(k);
            int target = d.target();
            if (BuildingStore.isBuilding(target)) {
                hitBuilding(s, d);
                continue;
            }
            if (!entities.isAlive(target)) {
                continue;
            }
            int i = Handle.index(target);
            if (s.garrisoned(i) && !d.lethal()) {
                continue; // inside a building nothing reaches it (BLD-16)
            }
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
            boolean dies = taken + dealt >= max;
            if (dies) {
                bits[i >>> 6] |=
                        1L << i; // before the credit: a lethal self-hit earns nothing, as the damage check had it
            }
            s.outputs().add(new UnitHurt(target, d.attacker(), (float) dealt));
            Ranks.credit(
                    s,
                    d.attacker(),
                    target,
                    Math.min(dealt, left),
                    max,
                    bits); // earned by what it took off, no more (CMB-11)
            if (dies) {
                addDying(target, d.attacker(), Loot.killer(s, d.attacker()));
            } else if (d.attacker() != Handle.NONE && entities.isAlive(d.attacker())) {
                s.bus().publish(new Attacked(target, d.attacker())); // an aggressive unit hits back (CMB-02)
            }
        }
        int[] fell = fallingRows;
        sortRows(fell, 2, fallingCount);
        for (int k = 0; k < fallingCount; k++) {
            int row = 2 * (int) order[k];
            int handle = fell[row];
            FactionSlot faction = new FactionSlot(s.buildings().faction(handle));
            s.outputs().add(new BuildingDestroyed(handle, faction));
            if (faction.value() < s.config().factions()) {
                s.matchStats().buildingLost(faction.value());
            }
            s.aiSlots().wake(faction.value()); // a computer player that lost a building thinks at once
            s.recordLoss(handle); // a computer player's rebuilder places it again (AI-04)
            s.outputs().add(new Notice(faction, NoticeKind.UNIT_LOST, handle, "destroyed"));
            Loot.building(s, fell[row + 1], handle); // before the building leaves the store (CMB-13)
            s.destroyBuilding(handle);
        }
        int[] dead = dyingRows;
        sortRows(dead, 3, dyingCount);
        for (int k = 0; k < dyingCount; k++) {
            int row = 3 * (int) order[k];
            int handle = dead[row];
            FactionSlot faction = new FactionSlot(s.identity().faction(Handle.index(handle)));
            s.outputs().add(new UnitDied(handle, faction, dead[row + 1]));
            if (faction.value() < s.config().factions()) {
                s.matchStats().unitLost(faction.value());
            }
            if (faction.value() == s.gaia()) {
                Livestock.died(s, handle); // an animal leaves its carcass (ECO-16)
            } else {
                s.outputs().add(new Notice(faction, NoticeKind.UNIT_LOST, handle, "died"));
            }
            Loot.unit(s, dead[row + 2], handle); // its carry moves to the killer before the unit drops it
            s.removeUnit(handle);
        }
    }

    /**
     * A blow on a building (BLD-01, CMB-03): as {@link DamageFormula} gives it against the building's template's
     * resistances, fire's from {@code Resistance/fire}, or all that is left for a lethal one; a building that reaches
     * its most falls once the tick's blows are dealt.
     */
    private void hitBuilding(MatchState s, Damage d) {
        int target = d.target();
        if (!s.buildings().alive(target) || fallen(target)) {
            return;
        }
        double max = s.buildingMaxHp(target);
        double taken = s.buildings().damage(target);
        double dealt = d.lethal() ? max - taken : DamageFormula.dealt(d, resistance(s, target), classesOf(s, target));
        s.buildings().setDamage(target, Math.min(max, taken + dealt));
        if (taken + dealt >= max) {
            addFalling(target, Loot.killer(s, d.attacker()));
        } else {
            DamageStages.update(s, target); // its blocks fall at each threshold (BLD-05)
        }
        s.outputs().add(new BuildingHurt(target, d.attacker(), (float) dealt));
    }

    private boolean fallen(int building) {
        for (int k = 0; k < fallingCount; k++) {
            if (fallingRows[2 * k] == building) {
                return true;
            }
        }
        return false;
    }

    private void addDying(int handle, int attacker, int killer) {
        int at = 3 * dyingCount++;
        if (at + 3 > dyingRows.length) {
            dyingRows = Arrays.copyOf(dyingRows, 2 * dyingRows.length);
        }
        dyingRows[at] = handle;
        dyingRows[at + 1] = attacker;
        dyingRows[at + 2] = killer;
    }

    private void addFalling(int handle, int killer) {
        int at = 2 * fallingCount++;
        if (at + 2 > fallingRows.length) {
            fallingRows = Arrays.copyOf(fallingRows, 2 * fallingRows.length);
        }
        fallingRows[at] = handle;
        fallingRows[at + 1] = killer;
    }

    /** Fills {@code order[0..count)} with the rows' numbers in the order of their handles (the first int of a row). */
    private void sortRows(int[] rows, int width, int count) {
        if (order.length < count) {
            order = new long[Math.max(count, 2 * order.length)];
        }
        for (int k = 0; k < count; k++) {
            order[k] = ((long) rows[width * k] << 32) | k;
        }
        Arrays.sort(order, 0, count);
        for (int k = 0; k < count; k++) {
            order[k] &= 0xFFFFFFFFL;
        }
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
