package dev.strategos.core.client.territory;

import dev.strategos.core.protocol.TerritoryUpdate;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.TerritoryGrid;
import java.util.BitSet;
import org.jspecify.annotations.Nullable;

/**
 * The client's territory (M9 spec §2.4): parts of a version are applied to a staging grid, and the version becomes
 * the visible one when its last part arrives. The tiles that changed with the last applied version are kept for the
 * borders and the minimap ({@link #changed()}).
 */
@CoreApi
public final class ClientTerritory {
    private @Nullable TerritoryGrid grid;
    private @Nullable TerritoryGrid staging;
    private int version = -1;
    private int stagingVersion = -1;
    private int nextPart;
    private final BitSet changed = new BitSet();

    /** The visible grid, or null before the first version. */
    public @Nullable TerritoryGrid grid() {
        return grid;
    }

    public int version() {
        return version;
    }

    /** The tiles changed by the last applied version (all of them after a full one). */
    public BitSet changed() {
        return changed;
    }

    /** Applies a part; true when it completed a version. A part out of order drops its version (a full one follows). */
    public boolean accept(TerritoryUpdate u) {
        if (u.part() == 0) {
            TerritoryGrid base = grid;
            TerritoryGrid s = new TerritoryGrid(u.tilesX(), u.tilesZ());
            if (!u.full()) {
                if (base == null || base.tilesX() != u.tilesX() || base.tilesZ() != u.tilesZ()) {
                    return false; // a delta without its base: wait for the next full version
                }
                s.copyFrom(base);
            }
            staging = s;
            stagingVersion = u.version();
            nextPart = 0;
        }
        TerritoryGrid s = staging;
        if (s == null || u.version() != stagingVersion || u.part() != nextPart) {
            staging = null;
            return false;
        }
        if (u.full()) {
            int at = u.start();
            for (int i = 0; i < u.entries(); i++) {
                for (int k = 0; k < u.key(i); k++) {
                    s.set(at++, u.value(i));
                }
            }
        } else {
            for (int i = 0; i < u.entries(); i++) {
                s.set(u.key(i), u.value(i));
            }
        }
        nextPart++;
        if (!u.last()) {
            return false;
        }
        changed.clear();
        TerritoryGrid old = grid;
        for (int t = 0; t < s.size(); t++) {
            if (old == null || old.size() != s.size() || old.value(t) != s.value(t)) {
                changed.set(t);
            }
        }
        grid = s;
        version = stagingVersion;
        staging = null;
        return true;
    }

    /** Forgets the match's territory (a world switch). */
    public void clear() {
        grid = null;
        staging = null;
        version = -1;
        changed.clear();
    }
}
