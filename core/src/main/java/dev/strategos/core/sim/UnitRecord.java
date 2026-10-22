package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.nav.PathStore;
import org.jspecify.annotations.Nullable;

/**
 * One unit in a snapshot; its template by string id, never by net index, so a changed set still loads. {@code ai} is
 * the unit's machine: its leaf and data words, the words 0 in every M0 save. {@code path} is {@link
 * PathStore.UnitPath#NONE} for a unit that never asked for one, and {@code motion} {@link MotionStore.Extra#NONE} for
 * one that never touched terrain: so is every unit of an M0 save.
 */
@CoreApi
public record UnitRecord(
        int handle,
        TemplateId template,
        FactionSlot faction,
        double x,
        double y,
        double z,
        float yaw,
        AiStateStore.Saved ai,
        @Nullable Vec2d target,
        PathStore.UnitPath path,
        MotionStore.Extra motion) {
    /** A unit that never touched terrain. */
    public UnitRecord(
            int handle,
            TemplateId template,
            FactionSlot faction,
            double x,
            double y,
            double z,
            float yaw,
            AiStateStore.Saved ai,
            @Nullable Vec2d target,
            PathStore.UnitPath path) {
        this(handle, template, faction, x, y, z, yaw, ai, target, path, MotionStore.Extra.NONE);
    }

    /** A unit without a path. */
    public UnitRecord(
            int handle,
            TemplateId template,
            FactionSlot faction,
            double x,
            double y,
            double z,
            float yaw,
            AiStateStore.Saved ai,
            @Nullable Vec2d target) {
        this(handle, template, faction, x, y, z, yaw, ai, target, PathStore.UnitPath.NONE);
    }
}
