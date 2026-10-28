package dev.strategos.core.sim.combat;

import dev.strategos.core.shared.CoreApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Immutable in-flight shots in launch-id order; allocator history survives even an empty store (MATCH-04). */
public final class ShotStore {
    private long nextId = 1;
    private final ArrayList<Shot> rows = new ArrayList<>();

    /** Where and when a shot left its shooter, frozen with the shot so its impact feedback keeps both ends (CMB-04). */
    @CoreApi
    public record Release(double x, double y, double z, long tick) {
        public Release {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || tick < 0) {
                throw new IllegalArgumentException("invalid shot release");
            }
        }
    }

    @CoreApi
    public record Launch(
            double fromX,
            double fromY,
            double fromZ,
            double targetX,
            double targetY,
            double targetZ,
            double targetVx,
            double targetVz,
            double speed,
            double spread,
            Damage damage) {
        public Launch {
            for (double value :
                    new double[] {fromX, fromY, fromZ, targetX, targetY, targetZ, targetVx, targetVz, speed, spread}) {
                if (!Double.isFinite(value)) throw new IllegalArgumentException("nonfinite shot launch");
            }
            if (speed <= 0 || spread < 0) throw new IllegalArgumentException("invalid shot speed or spread");
            Objects.requireNonNull(damage);
        }

        /** A launch whose release height is the target's. */
        public Launch(
                double fromX,
                double fromZ,
                double targetX,
                double targetY,
                double targetZ,
                double targetVx,
                double targetVz,
                double speed,
                double spread,
                Damage damage) {
            this(fromX, targetY, fromZ, targetX, targetY, targetZ, targetVx, targetVz, speed, spread, damage);
        }
    }

    @CoreApi
    public record Shot(
            long id,
            int shooter,
            int target,
            long landTick,
            double landingX,
            double landingY,
            double landingZ,
            Damage damage,
            @Nullable Release release) {
        public Shot {
            Objects.requireNonNull(damage);
            if (id <= 0
                    || landTick < 0
                    || !Double.isFinite(landingX)
                    || !Double.isFinite(landingY)
                    || !Double.isFinite(landingZ)) {
                throw new IllegalArgumentException("invalid saved shot");
            }
            if (shooter != damage.attacker() || target != damage.target()) {
                throw new IllegalArgumentException("shot handles differ from its frozen damage");
            }
        }

        /** A shot without a frozen release (saved before releases existed). */
        public Shot(
                long id,
                int shooter,
                int target,
                long landTick,
                double landingX,
                double landingY,
                double landingZ,
                Damage damage) {
            this(id, shooter, target, landTick, landingX, landingY, landingZ, damage, null);
        }
    }

    @CoreApi
    public record Snapshot(long nextId, List<Shot> rows) {
        public static final Snapshot EMPTY = new Snapshot(1, List.of());

        public Snapshot {
            if (nextId < 1) throw new IllegalArgumentException("invalid next shot id");
            rows = List.copyOf(rows);
            long previous = 0;
            for (Shot shot : rows) {
                if (shot.id() <= previous || shot.id() >= nextId) {
                    throw new IllegalArgumentException("saved shot ids must increase below nextId");
                }
                previous = shot.id();
            }
        }
    }

    public Shot launch(long seed, long tick, Launch launch) {
        if (nextId > (Long.MAX_VALUE - 1) / 2) throw new IllegalStateException("shot ids exhausted");
        ShotAim.Aim aim = ShotAim.aim(seed, tick, nextId, launch);
        Damage damage = launch.damage();
        Shot shot = new Shot(
                nextId,
                damage.attacker(),
                damage.target(),
                aim.landTick(),
                aim.landingX(),
                aim.landingY(),
                aim.landingZ(),
                damage,
                new Release(launch.fromX(), launch.fromY(), launch.fromZ(), tick));
        rows.add(shot);
        nextId++;
        return shot;
    }

    public int count() {
        return rows.size();
    }

    public Shot shot(int index) {
        return rows.get(index);
    }

    public long nextId() {
        return nextId;
    }

    public boolean pristine() {
        return nextId == 1 && rows.isEmpty();
    }

    public Snapshot save() {
        if (pristine()) return Snapshot.EMPTY;
        return new Snapshot(nextId, rows);
    }

    public void restore(Snapshot snapshot) {
        rows.clear();
        rows.addAll(snapshot.rows());
        nextId = snapshot.nextId();
    }

    /** Each due or overdue row once, in launch order, with bounded linear compaction of the remaining rows. */
    public List<Shot> takeDue(long tick) {
        if (tick < 0) throw new IllegalArgumentException("negative impact tick");
        if (rows.isEmpty()) return List.of();
        List<Shot> due = null;
        int kept = 0;
        for (int i = 0; i < rows.size(); i++) {
            Shot shot = rows.get(i);
            if (shot.landTick() <= tick) {
                if (due == null) due = new ArrayList<>();
                due.add(shot);
            } else {
                if (kept != i) rows.set(kept, shot);
                kept++;
            }
        }
        if (kept != rows.size()) rows.subList(kept, rows.size()).clear();
        return due == null ? List.of() : List.copyOf(due);
    }
}
