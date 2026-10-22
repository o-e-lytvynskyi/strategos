package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the core lays over the terrain (04 §9.2): building footprints, which remove the surfaces of the columns they
 * cover, so our own building blocks never become terrain. Immutable; M1 has no buildings, so matches use
 * {@link #EMPTY} and tests build their own.
 */
@CoreApi
public final class NavOverlay {
    public static final NavOverlay EMPTY = new NavOverlay(new LinkedHashMap<>());

    private final Map<PageCoord, long[]> blocked;

    private NavOverlay(Map<PageCoord, long[]> blocked) {
        this.blocked = blocked;
    }

    /** Whether column {@code x * 16 + z} of the page lies under a footprint. */
    public boolean blocked(PageCoord page, int column) {
        long[] mask = blocked.get(page);
        return mask != null && (mask[column >> 6] & (1L << (column & 63))) != 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Collects footprint columns, match-local. */
    public static final class Builder {
        private final Map<PageCoord, long[]> blocked = new LinkedHashMap<>();

        public Builder block(int localX, int localZ) {
            long[] mask = blocked.computeIfAbsent(PageCoord.of(localX, localZ), p -> new long[4]);
            int column = SurfacePage.column(localX & 15, localZ & 15);
            mask[column >> 6] |= 1L << (column & 63);
            return this;
        }

        public NavOverlay build() {
            Map<PageCoord, long[]> copy = new LinkedHashMap<>();
            blocked.forEach((page, mask) -> copy.put(page, mask.clone()));
            return new NavOverlay(copy);
        }
    }
}
