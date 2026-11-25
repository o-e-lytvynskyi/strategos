package dev.strategos.core.sim.civ;

import dev.strategos.core.content.PlacementRule;
import dev.strategos.core.shared.CoreApi;
import org.jspecify.annotations.Nullable;

/**
 * The eight places a civilisation may vary the rules (ADR-0028), each with the name a civilisation file keys it by
 * ({@code rules.<name>}) and the interface its variants implement.
 */
@CoreApi
public enum SeamKind {
    POPULATION("population", PopulationRule.class),
    DELIVERY("delivery", DeliveryRule.class),
    CONSTRUCTION("construction", ConstructionRule.class),
    UPKEEP("upkeep", UpkeepRule.class),
    RESOURCES("resources", ResourceRule.class),
    PHASE("phase", PhaseRule.class),
    PLACEMENT("placement", PlacementRule.class),
    DEFEAT("defeat", DefeatRule.class);

    private final String dataName;
    private final Class<?> type;

    SeamKind(String dataName, Class<?> type) {
        this.dataName = dataName;
        this.type = type;
    }

    /** The name a civilisation's {@code rules} object keys the seam by. */
    public String dataName() {
        return dataName;
    }

    /** The interface a variant of this seam implements. */
    public Class<?> type() {
        return type;
    }

    /** The seam a data name denotes, or null for a name no seam has. */
    public static @Nullable SeamKind ofDataName(String name) {
        for (SeamKind k : values()) {
            if (k.dataName.equals(name)) {
                return k;
            }
        }
        return null;
    }
}
