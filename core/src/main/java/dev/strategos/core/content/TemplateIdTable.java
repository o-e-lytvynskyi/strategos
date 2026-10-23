package dev.strategos.core.content;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.TemplateId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The templates' network indices for a server's session (DATA-03, ADR-0012): append-only, so a reload keeps every index
 * it gave, gives a new template the next free one, new ids in id order, and retires the index of a template that is
 * gone. Clients get it with the set and check it by its hash, the SHA-256 of the ids in index order.
 */
@CoreApi
public final class TemplateIdTable {
    public static final TemplateIdTable EMPTY = new TemplateIdTable(List.of());

    private final List<TemplateId> ids;

    /** The table that gives {@code ids.get(k)} index {@code k}; ids are distinct. */
    public TemplateIdTable(List<TemplateId> ids) {
        if (new TreeSet<>(ids.stream().map(TemplateId::value).toList()).size() != ids.size()) {
            throw new IllegalArgumentException("a template id twice in " + ids);
        }
        this.ids = List.copyOf(ids);
    }

    /** This table with every id of {@code present} it lacks appended, in id order. */
    public TemplateIdTable with(Collection<TemplateId> present) {
        TreeSet<String> fresh = new TreeSet<>();
        for (TemplateId id : present) {
            if (indexOf(id) < 0) {
                fresh.add(id.value());
            }
        }
        if (fresh.isEmpty()) {
            return this;
        }
        List<TemplateId> next = new ArrayList<>(ids);
        for (String id : fresh) {
            next.add(new TemplateId(id));
        }
        return new TemplateIdTable(next);
    }

    /** The index of {@code id}, or -1 when the table never gave it one. */
    public int indexOf(TemplateId id) {
        for (int k = 0; k < ids.size(); k++) {
            if (ids.get(k).equals(id)) {
                return k;
            }
        }
        return -1;
    }

    public @Nullable TemplateId id(int index) {
        return index >= 0 && index < ids.size() ? ids.get(index) : null;
    }

    public int size() {
        return ids.size();
    }

    /** The ids in index order. */
    public List<TemplateId> ids() {
        return ids;
    }

    /** The SHA-256 of the ids in index order, one per line. */
    public byte[] sha() {
        StringBuilder text = new StringBuilder();
        for (TemplateId id : ids) {
            text.append(id.value()).append('\n');
        }
        return CanonicalJson.sha256(text.toString());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TemplateIdTable other && ids.equals(other.ids);
    }

    @Override
    public int hashCode() {
        return ids.hashCode();
    }

    @Override
    public String toString() {
        return "TemplateIdTable" + ids;
    }
}
