package dev.strategos.core.content;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/** The numeric value paths of a template set, interned in alphabetical order, so ids are the same on every machine. */
public final class ValuePaths {
    private final List<ValuePath> paths;

    public ValuePaths(Collection<String> paths) {
        List<ValuePath> interned = new ArrayList<>();
        for (String path : new TreeSet<>(paths)) {
            interned.add(new ValuePath(interned.size(), path));
        }
        this.paths = List.copyOf(interned);
    }

    public @Nullable ValuePath byPath(String path) {
        int lo = 0;
        int hi = paths.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int c = paths.get(mid).path().compareTo(path);
            if (c == 0) {
                return paths.get(mid);
            }
            if (c < 0) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return null;
    }

    public ValuePath byId(int id) {
        return paths.get(id);
    }

    public int size() {
        return paths.size();
    }

    public List<ValuePath> all() {
        return paths;
    }
}
