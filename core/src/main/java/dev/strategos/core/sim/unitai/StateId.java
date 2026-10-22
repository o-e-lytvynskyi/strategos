package dev.strategos.core.sim.unitai;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.UnitState;
import org.jspecify.annotations.Nullable;

/**
 * A state of a unit's machine (UNIT-02). Stores and hashes use {@code code}, saves use {@code name}; both are assigned
 * once and never reused, like wire ids. {@code activity} is the top-level state a unit in this state reports to views
 * and events; every leaf has one, a state with children need not.
 */
@CoreApi
public record StateId(int code, String name, @Nullable UnitState activity) {
    public StateId {
        if (code < 0 || code > Short.MAX_VALUE) {
            throw new IllegalArgumentException("state code " + code + " outside 0.." + Short.MAX_VALUE);
        }
        if (!plainName(name)) {
            throw new IllegalArgumentException(
                    "state name '" + name + "' is not lower-case letters, digits, '_' or '.'");
        }
    }

    private static boolean plainName(String name) {
        if (name.isEmpty() || name.charAt(0) < 'a' || name.charAt(0) > 'z') {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.')) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return name;
    }
}
