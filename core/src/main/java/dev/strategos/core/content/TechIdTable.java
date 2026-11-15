package dev.strategos.core.content;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.TechId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The technologies' indices for a server's session (TECH-01, spec row 20): append-only, so a reload keeps every index
 * it gave, gives a new tech the next free one, new ids in id order, and retires the index of a tech that is gone. What
 * is saved or sent names a tech by its index, so an index never moves.
 */
@CoreApi
public final class TechIdTable {
    public static final TechIdTable EMPTY = new TechIdTable(List.of());

    private final List<TechId> ids;

    /** The table that gives {@code ids.get(k)} index {@code k}; ids are distinct. */
    public TechIdTable(List<TechId> ids) {
        if (new TreeSet<>(ids.stream().map(TechId::value).toList()).size() != ids.size()) {
            throw new IllegalArgumentException("a tech id twice in " + ids);
        }
        this.ids = List.copyOf(ids);
    }

    /** This table with every id of {@code present} it lacks appended, in id order. */
    public TechIdTable with(Collection<TechId> present) {
        TreeSet<String> fresh = new TreeSet<>();
        for (TechId id : present) {
            if (indexOf(id) < 0) {
                fresh.add(id.value());
            }
        }
        if (fresh.isEmpty()) {
            return this;
        }
        List<TechId> next = new ArrayList<>(ids);
        for (String id : fresh) {
            next.add(new TechId(id));
        }
        return new TechIdTable(next);
    }

    /** The index of {@code id}, or -1 when the table never gave it one. */
    public int indexOf(TechId id) {
        for (int k = 0; k < ids.size(); k++) {
            if (ids.get(k).equals(id)) {
                return k;
            }
        }
        return -1;
    }

    public @Nullable TechId id(int index) {
        return index >= 0 && index < ids.size() ? ids.get(index) : null;
    }

    public int size() {
        return ids.size();
    }

    /** The ids in index order. */
    public List<TechId> ids() {
        return ids;
    }

    /** The SHA-256 of the ids in index order, one per line. */
    public byte[] sha() {
        StringBuilder text = new StringBuilder();
        for (TechId id : ids) {
            text.append(id.value()).append('\n');
        }
        return CanonicalJson.sha256(text.toString());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TechIdTable other && ids.equals(other.ids);
    }

    @Override
    public int hashCode() {
        return ids.hashCode();
    }

    @Override
    public String toString() {
        return "TechIdTable" + ids;
    }
}
