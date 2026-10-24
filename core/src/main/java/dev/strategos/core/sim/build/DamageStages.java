package dev.strategos.core.sim.build;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.ValuePath;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.BlockFell;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.WorldEffect;
import dev.strategos.core.sim.world.WritePriority;

/**
 * A finished building's damage stages (BLD-05, 04 §11.8): its hit points are abstract, and three thresholds of them, in
 * hundredths of its most ({@code DamageStages/first}, {@code second} and {@code third}, 85, 65 and 35 by default), mark
 * stages 1 to 3. Below a threshold its stage rises at once, and as many of its {@link DamageRanks} as the share of hit
 * points the threshold lost fall, through the writer, with a falling block's dust; above one again it comes back only
 * once {@value #HYSTERESIS_PERCENT} % over it, so hit points that hover give no flicker of blocks, and the blocks come
 * back in the reverse order. The stage is the building's, saved and hashed.
 */
public final class DamageStages {
    public static final String FIRST = "DamageStages/first";
    public static final String SECOND = "DamageStages/second";
    public static final String THIRD = "DamageStages/third";

    /** Stages after the whole: 1, 2 and 3. */
    public static final int STAGES = 3;

    /** How far above a threshold, in hundredths, hit points must come back to restore its stage. */
    public static final int HYSTERESIS_PERCENT = 2;

    private DamageStages() {}

    /** Threshold {@code k} (1 to 3) of building {@code handle}, as a fraction of its most. */
    public static double threshold(MatchState s, int handle, int k) {
        String path = switch (k) {
            case 1 -> FIRST;
            case 2 -> SECOND;
            default -> THIRD;
        };
        double otherwise = switch (k) {
            case 1 -> 85;
            case 2 -> 65;
            default -> 35;
        };
        ValuePath p = s.templates().valuePaths().byPath(path);
        double v = p == null ? Double.NaN : s.templates().value(s.buildings().template(handle), p.id());
        return (Double.isFinite(v) && v > 0 && v < 100 ? v : otherwise) / 100;
    }

    /**
     * The stage a building showing stage {@code now} takes at {@code fraction} of its hit points, thresholds {@code
     * t1}, {@code t2}, {@code t3}: every threshold below which it is, at once; one it has come back above only {@value
     * #HYSTERESIS_PERCENT} % over it.
     */
    public static int stageFor(double fraction, int now, double t1, double t2, double t3) {
        int down = (fraction < t1 ? 1 : 0) + (fraction < t2 ? 1 : 0) + (fraction < t3 ? 1 : 0);
        if (down >= now) {
            return down;
        }
        int stage = now;
        while (stage > down && fraction >= at(stage, t1, t2, t3) + HYSTERESIS_PERCENT / 100.0) {
            stage--;
        }
        return stage;
    }

    /** How many of {@code n} falling blocks are down at {@code stage}: the share of hit points its threshold lost. */
    public static int fallen(int stage, int n, double t1, double t2, double t3) {
        return stage == 0 ? 0 : (int) Math.round(n * (1 - at(stage, t1, t2, t3)));
    }

    private static double at(int stage, double t1, double t2, double t3) {
        return switch (stage) {
            case 1 -> t1;
            case 2 -> t2;
            default -> t3;
        };
    }

    /**
     * Building {@code handle}'s hit points changed: a finished one takes the stage they give, and the writer is asked
     * for the blocks between the two stages, taken away or put back.
     */
    public static void update(MatchState s, int handle) {
        BuildingStore buildings = s.buildings();
        if (!buildings.alive(handle) || buildings.state(handle) != BuildingStore.COMPLETE) {
            return;
        }
        double max = s.buildingMaxHp(handle);
        double fraction = max <= 0 ? 0 : (max - buildings.damage(handle)) / max;
        double t1 = threshold(s, handle, 1);
        double t2 = threshold(s, handle, 2);
        double t3 = threshold(s, handle, 3);
        int now = buildings.stage(handle);
        int want = stageFor(fraction, now, t1, t2, t3);
        if (want == now) {
            return;
        }
        Blueprint b = s.blueprintOf(handle);
        int[] ranks = s.damageRanks(b);
        int from = fallen(now, ranks.length, t1, t2, t3);
        int to = fallen(want, ranks.length, t1, t2, t3);
        Footprint at = s.footprintOf(handle);
        int baseY = buildings.baseY(handle);
        int site = Handle.index(handle);
        for (int r = from; r < to; r++) { // falling, in rank order
            int k = ranks[r];
            int x = at.worldX(b.x(k), b.z(k));
            int z = at.worldZ(b.x(k), b.z(k));
            String state = b.palette().get(b.state(k));
            s.outputs()
                    .add(new WorldEffect(site, WritePriority.DAMAGE_STAGE, x, baseY + b.y(k), z, WorldEffect.AIR, 0));
            s.outputs().add(new BlockFell(x, baseY + b.y(k), z, state));
        }
        for (int r = from - 1; r >= to; r--) { // coming back, the last fallen first
            int k = ranks[r];
            s.outputs()
                    .add(new WorldEffect(
                            site,
                            WritePriority.DAMAGE_STAGE,
                            at.worldX(b.x(k), b.z(k)),
                            baseY + b.y(k),
                            at.worldZ(b.x(k), b.z(k)),
                            b.palette().get(b.state(k)),
                            handle));
        }
        buildings.setStage(handle, want);
    }
}
