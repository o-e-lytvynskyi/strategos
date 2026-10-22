package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import org.jspecify.annotations.Nullable;

/**
 * Encoded voxel slices by hash, for rebuilding a snapshot's terrain pages: a journal segment's, or none. A page whose
 * slice is missing stays unpublished until the adapter publishes it again.
 */
@CoreApi
@FunctionalInterface
public interface SliceSource {
    SliceSource NONE = hash -> null;

    byte @Nullable [] slice(long hash);
}
