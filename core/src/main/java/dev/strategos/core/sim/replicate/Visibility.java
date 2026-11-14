package dev.strategos.core.sim.replicate;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.UnitView;

/** Whether {@code viewer} sees an enemy unit now; fed from {@code VisibilityIndex} under solo fog (FOW-07 / UI-16). */
@CoreApi
@FunctionalInterface
public interface Visibility {
    Visibility ALL = (viewer, unit) -> true;

    boolean sees(FactionSlot viewer, UnitView unit);
}
