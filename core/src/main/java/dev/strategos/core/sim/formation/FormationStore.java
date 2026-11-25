package dev.strategos.core.sim.formation;

import dev.strategos.core.port.JobScratch;
import dev.strategos.core.protocol.FormationShape;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Hasher;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The formations of a match (ORD-06, 04 §9.4), by id, in id order; an id is a group id of the match, so never reused. Empty until a formation is
 * ordered, and then absent from the hash and the save, so every match without one hashes as before. Saved and hashed
 * whole: the units' orders name their formation by id, and their states are saved with them.
 */
public final class FormationStore {
    /** One formation as a save keeps it; its derived values follow from these. */
    @CoreApi
    public record Row(
            int id,
            int shape,
            List<Double> route,
            boolean more,
            double goalX,
            double goalZ,
            double along,
            double facingX,
            double facingZ,
            boolean holding,
            boolean advancing,
            int waited,
            List<Integer> members,
            List<Double> lateral,
            List<Double> forward) {
        public Row {
            route = List.copyOf(route);
            members = List.copyOf(members);
            lateral = List.copyOf(lateral);
            forward = List.copyOf(forward);
        }
    }

    /** What a save keeps: the formations. */
    @CoreApi
    public record Saved(List<Row> formations) {
        public static final Saved EMPTY = new Saved(List.of());

        public Saved {
            formations = List.copyOf(formations);
        }
    }

    private final List<FormationController> formations = new ArrayList<>();
    private final JobScratch scratch = new JobScratch();

    public boolean empty() {
        return formations.isEmpty();
    }

    public int count() {
        return formations.size();
    }

    public FormationController at(int k) {
        return formations.get(k);
    }

    /** Formation {@code id}, or null once it is gone. */
    public @Nullable FormationController byId(int id) {
        for (int k = 0; k < formations.size(); k++) {
            FormationController c = formations.get(k);
            if (c.id() == id) {
                return c;
            }
        }
        return null;
    }

    /**
     * A new formation {@code id} (a group id of the match, which are never reused) walking {@code route} (x, z pairs, at
     * least one point), with no members yet.
     */
    FormationController create(int id, FormationShape shape, double[] route, boolean more, double goalX, double goalZ) {
        FormationController c = new FormationController(id, shape, route, more, goalX, goalZ);
        formations.add(c);
        return c;
    }

    void removeAt(int k) {
        formations.remove(k);
    }

    /** The working memory of the route searches this match's formations make. */
    JobScratch scratch() {
        return scratch;
    }

    /** The formations as a save keeps them: {@link Saved#EMPTY} while there are none. */
    public Saved save() {
        if (formations.isEmpty()) {
            return Saved.EMPTY;
        }
        List<Row> rows = new ArrayList<>(formations.size());
        for (FormationController c : formations) {
            rows.add(new Row(
                    c.id(),
                    c.shape().code(),
                    doubles(c.route()),
                    c.more(),
                    c.goalX(),
                    c.goalZ(),
                    c.along(),
                    c.facingX(),
                    c.facingZ(),
                    c.holding(),
                    c.advancing(),
                    c.waited(),
                    ints(c.membersCopy()),
                    doubles(c.lateralCopy()),
                    doubles(c.forwardCopy())));
        }
        return new Saved(rows);
    }

    /** Replaces the formations with a save's. */
    public void restore(Saved saved) {
        formations.clear();
        for (Row row : saved.formations()) {
            formations.add(new FormationController(
                    row.id(),
                    FormationShape.ofCode(row.shape()),
                    doubles(row.route()),
                    row.more(),
                    row.goalX(),
                    row.goalZ(),
                    row.along(),
                    row.facingX(),
                    row.facingZ(),
                    row.holding(),
                    row.advancing(),
                    row.waited(),
                    ints(row.members()),
                    doubles(row.lateral()),
                    doubles(row.forward())));
        }
    }

    /** Mixes the formations into the state hash {@code h}. */
    public long mix(long h) {
        h = Hasher.mix(h, 0x464f524d4154L); // "FORMAT"
        h = Hasher.mix(h, formations.size());
        for (int f = 0; f < formations.size(); f++) {
            FormationController c = formations.get(f);
            h = Hasher.mix(h, c.id());
            h = Hasher.mix(h, c.shape().code());
            h = Hasher.mix(h, c.more() ? 1 : 0);
            h = Hasher.mix(h, Double.doubleToLongBits(c.goalX()));
            h = Hasher.mix(h, Double.doubleToLongBits(c.goalZ()));
            h = Hasher.mix(h, Double.doubleToLongBits(c.along()));
            h = Hasher.mix(h, Double.doubleToLongBits(c.facingX()));
            h = Hasher.mix(h, Double.doubleToLongBits(c.facingZ()));
            h = Hasher.mix(h, (c.holding() ? 1 : 0) | (c.advancing() ? 2 : 0));
            h = Hasher.mix(h, c.waited());
            int points = c.routePoints();
            h = Hasher.mix(h, points);
            for (int p = 0; p < points; p++) {
                h = Hasher.mix(h, Double.doubleToLongBits(c.routeX(p)));
                h = Hasher.mix(h, Double.doubleToLongBits(c.routeZ(p)));
            }
            h = Hasher.mix(h, c.size());
            for (int k = 0; k < c.size(); k++) {
                h = Hasher.mix(h, c.member(k));
                h = Hasher.mix(h, Double.doubleToLongBits(c.lateral(k)));
                h = Hasher.mix(h, Double.doubleToLongBits(c.forward(k)));
            }
        }
        return h;
    }

    private static List<Double> doubles(double[] values) {
        List<Double> out = new ArrayList<>(values.length);
        for (double v : values) {
            out.add(v);
        }
        return out;
    }

    private static List<Integer> ints(int[] values) {
        List<Integer> out = new ArrayList<>(values.length);
        for (int v : values) {
            out.add(v);
        }
        return out;
    }

    private static double[] doubles(List<Double> values) {
        double[] out = new double[values.size()];
        for (int k = 0; k < out.length; k++) {
            out[k] = values.get(k);
        }
        return out;
    }

    private static int[] ints(List<Integer> values) {
        int[] out = new int[values.size()];
        for (int k = 0; k < out.length; k++) {
            out[k] = values.get(k);
        }
        return out;
    }
}
