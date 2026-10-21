package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import org.jspecify.annotations.Nullable;

/** A read-only view of one unit; {@code target} is its move target, or null without an order. */
@CoreApi
public record UnitView(
        int handle,
        TemplateId template,
        int templateIndex,
        FactionSlot faction,
        double x,
        double y,
        double z,
        float yaw,
        UnitState state,
        @Nullable Vec2d target) {}
