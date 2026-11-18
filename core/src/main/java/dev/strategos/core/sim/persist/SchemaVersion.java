package dev.strategos.core.sim.persist;

import dev.strategos.core.shared.CoreApi;

/** The snapshot schema version this build writes, as the {@code "v"} field. */
@CoreApi
public final class SchemaVersion {
    /**
     * 3 persists stable template bindings for building slots and production queues (#25). Older
     * builds must refuse these saves rather than silently interpreting the session indices.
     * Versions 1 and 2 migrate without inventing identities; legacy custom indices fail explicitly.
     * <p>4 (M7): explicit diplomacy authority; older versions migrate to identity bindings, while a current save
     * must contain its authority block. Older readers reject this version instead of dropping control rights.
     * <p>5 freezes hostile shot source factions for locked-team combat. Legacy unlocked shots retain their
     * behavior; a locked legacy shot needs a matching saved live source identity for conservative migration.
     * <p>6 records an explicit terminal winner mask; legacy individual/draw hashes remain unchanged.
     */
    public static final int CURRENT = 6;

    private SchemaVersion() {}
}
