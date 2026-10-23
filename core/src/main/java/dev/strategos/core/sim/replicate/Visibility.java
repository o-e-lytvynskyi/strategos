package dev.strategos.core.sim.replicate;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.UnitView;

/** Whether {@code viewer} sees an enemy unit now; fog decides from M6 on, until then every unit is visible. */
@CoreApi
@FunctionalInterface
public interface Visibility {
    Visibility ALL = (viewer, unit) -> true;

    boolean sees(FactionSlot viewer, UnitView unit);
}
