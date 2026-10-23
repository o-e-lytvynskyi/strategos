package dev.strategos.core.sim.econ;

import dev.strategos.core.shared.CoreApi;
import java.util.List;

/** A copy of one faction's treasury (ECO-01), per resource in the match's order and in thousandths. */
@CoreApi
public record FactionTreasury(
        List<Long> stock, List<Long> granted, List<Long> gathered, List<Long> spent, List<Long> refunded) {
    public FactionTreasury {
        stock = List.copyOf(stock);
        granted = List.copyOf(granted);
        gathered = List.copyOf(gathered);
        spent = List.copyOf(spent);
        refunded = List.copyOf(refunded);
    }
}
