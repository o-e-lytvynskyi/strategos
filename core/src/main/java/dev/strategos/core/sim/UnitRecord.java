package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import org.jspecify.annotations.Nullable;

/** One unit in a snapshot; its template by string id, never by net index, so a changed set still loads. */
@CoreApi
public record UnitRecord(
        int handle,
        TemplateId template,
        FactionSlot faction,
        double x,
        double y,
        double z,
        float yaw,
        UnitState state,
        @Nullable Vec2d target) {}
