package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.sim.Allies;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * The gates of the match (M9 spec §2.9, PATH-12): each a few columns with an owner faction. A gate's columns are
 * blocked in the base grid for everyone (the caller blocks them through the footprint mechanism, {@code
 * MatchState#addGate}), so the components split there; this overlay says who may cross, and {@link GateLinks}, kept
 * per grid and per {@link #version()}, joins the surfaces on the gate's sides for an allied faction. Saved ({@code
 * Play.gates}) and hashed only while a gate exists, so matches without gates keep their hashes.
 *
 * <p>A gate's id is the lowest free one, so a saved set restores to the same ids. Columns are match-local, packed
 * {@code x << 16 | z}.
 */
@CoreApi
public final class GateOverlay {
    private static final int MAX_CACHED_GRIDS = 8;

    private static final class Gate {
        private final int owner;
        private final int[] columns;

        Gate(int owner, int[] columns) {
            this.owner = owner;
            this.columns = columns;
        }
    }

    private final Map<Integer, Gate> gates = new TreeMap<>();
    private int version;
    private final List<GateLinks> links = new ArrayList<>(); // by grid, compared by identity: an array, not a hash
    private int linksVersion = -1;

    /** Adds a gate of {@code owner} over {@code columns} (packed {@code x << 16 | z}); returns its id. */
    public int add(int owner, int[] columns) {
        if (columns.length == 0) {
            throw new IllegalArgumentException("a gate needs at least one column");
        }
        int id = 0;
        while (gates.containsKey(id)) {
            id++;
        }
        gates.put(id, new Gate(owner, columns.clone()));
        version++;
        return id;
    }

    /** Removes gate {@code id}; false when there is none. */
    public boolean remove(int id) {
        if (gates.remove(id) == null) {
            return false;
        }
        version++;
        return true;
    }

    /** Moves the version: for a change the overlay cannot see, such as the diplomacy {@link Allies} answers by. */
    public void touch() {
        version++;
    }

    public boolean empty() {
        return gates.isEmpty();
    }

    public int count() {
        return gates.size();
    }

    /** Grows with every change of the gate set. */
    public int version() {
        return version;
    }

    /** The gates' ids, ascending. */
    public int[] ids() {
        return gates.keySet().stream().mapToInt(Integer::intValue).toArray();
    }

    public boolean has(int id) {
        return gates.containsKey(id);
    }

    public int owner(int id) {
        return gate(id).owner;
    }

    /** Gate {@code id}'s columns, packed {@code x << 16 | z}; a copy. */
    public int[] columns(int id) {
        return gate(id).columns.clone();
    }

    private Gate gate(int id) {
        Gate g = gates.get(id);
        if (g == null) {
            throw new IllegalArgumentException("no gate " + id);
        }
        return g;
    }

    /** What the gates join on {@code grid}; null while there are none. */
    @Nullable GateLinks links(SurfaceGrid grid) {
        if (gates.isEmpty()) {
            return null;
        }
        if (linksVersion != version) {
            links.clear();
            linksVersion = version;
        } else if (links.size() >= MAX_CACHED_GRIDS) {
            links.clear();
        }
        GateLinks cached = null;
        for (GateLinks l : links) {
            if (l.grid() == grid) {
                cached = l;
                break;
            }
        }
        if (cached == null) {
            int[] ids = ids();
            int[] owners = new int[ids.length];
            int[][] columns = new int[ids.length][];
            for (int k = 0; k < ids.length; k++) {
                Gate gate = gate(ids[k]);
                owners[k] = gate.owner;
                columns[k] = gate.columns;
            }
            cached = GateLinks.of(grid, ids, owners, columns);
            links.add(cached);
        }
        return cached;
    }

    /**
     * What a unit of {@code faction} may cross on {@code grid}: the surfaces beside the gates of its allies, with
     * the crossings between them; null while there are no gates (or none it may use), so a search without gates is
     * the search of before.
     */
    public GateLinks.@Nullable Pass passFor(SurfaceGrid grid, int faction, Allies allies) {
        GateLinks l = links(grid);
        if (l == null) {
            return null;
        }
        GateLinks.Pass pass = l.pass(faction, allies);
        return pass.empty() ? null : pass;
    }

    public long mix(long h) {
        for (Map.Entry<Integer, Gate> e : gates.entrySet()) {
            h = Hasher.mix(h, e.getKey());
            h = Hasher.mix(h, e.getValue().owner);
            for (int c : e.getValue().columns) {
                h = Hasher.mix(h, c);
            }
        }
        return h;
    }

    /** {@code [id, owner, columns, column…]…}; empty without gates. */
    public List<Long> save() {
        List<Long> out = new ArrayList<>();
        for (Map.Entry<Integer, Gate> e : gates.entrySet()) {
            out.add((long) e.getKey());
            out.add((long) e.getValue().owner);
            out.add((long) e.getValue().columns.length);
            for (int c : e.getValue().columns) {
                out.add((long) c);
            }
        }
        return out;
    }

    public void restore(List<Long> saved) {
        gates.clear();
        int i = 0;
        while (i < saved.size()) {
            if (i + 3 > saved.size()) {
                throw new IllegalArgumentException("gate state cut short at " + i);
            }
            int id = (int) (long) saved.get(i);
            int owner = (int) (long) saved.get(i + 1);
            int n = (int) (long) saved.get(i + 2);
            if (n <= 0 || i + 3 + n > saved.size()) {
                throw new IllegalArgumentException("gate " + id + " with " + n + " columns in " + saved.size());
            }
            int[] columns = new int[n];
            for (int k = 0; k < n; k++) {
                columns[k] = (int) (long) saved.get(i + 3 + k);
            }
            gates.put(id, new Gate(owner, columns));
            i += 3 + n;
        }
        version++;
    }
}
