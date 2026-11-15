package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.sim.nav.MobilityClass;
import org.jspecify.annotations.Nullable;

/**
 * The mobility class of each template, and how many live units are of the large class (UNIT-06): derived from the
 * template set, neither hashed nor saved. Read through the match's current templates and identity store.
 */
final class MobilityCache {
    private final MatchState state;
    private @Nullable MobilityClass[] byTemplate = new MobilityClass[0];
    private boolean hasLarge;
    private int liveLarge; // live units of the large class; valid unless liveLargeStale
    private boolean liveLargeStale = true; // after a build, restore or template swap: recounted on demand

    MobilityCache(MatchState state) {
        this.state = state;
    }

    /** Caches the match's current template set; the live count is recounted on demand. */
    void rebuild() {
        TemplateSet templates = state.templates();
        byTemplate = new MobilityClass[templates.indexCount()];
        hasLarge = false;
        liveLargeStale = true;
        for (UnitTemplate template : templates.all()) {
            MobilityClass mobility = switch (template.mobility()) {
                case "infantry" -> MobilityClass.INFANTRY;
                case "large" -> MobilityClass.LARGE;
                default -> null; // Preserve the original error when an unsupported mobility is queried.
            };
            byTemplate[template.netIndex()] = mobility;
            hasLarge |= mobility == MobilityClass.LARGE;
        }
    }

    MobilityClass of(int index) {
        int template = state.identity().templateIndex(index);
        if (template >= 0 && template < byTemplate.length) {
            MobilityClass cached = byTemplate[template];
            if (cached != null) return cached;
        }
        return MobilityClass.of(state.templates().byIndex(template).mobility());
    }

    boolean hasLarge() {
        return hasLarge;
    }

    boolean hasLiveLarge() {
        if (!hasLarge) {
            return false;
        }
        if (liveLargeStale) {
            liveLarge = 0;
            EntityStore entities = state.entities();
            for (int i = 0; i < entities.highWater(); i++) {
                if (entities.aliveAt(i) && of(i) == MobilityClass.LARGE) {
                    liveLarge++;
                }
            }
            liveLargeStale = false;
        }
        return liveLarge > 0;
    }

    /** A unit of index {@code index} came into being. */
    void spawned(int index) {
        if (!liveLargeStale && of(index) == MobilityClass.LARGE) {
            liveLarge++;
        }
    }

    /** The unit at {@code index} is about to be removed. */
    void removing(int index) {
        if (!liveLargeStale && of(index) == MobilityClass.LARGE) {
            liveLarge--;
        }
    }
}
