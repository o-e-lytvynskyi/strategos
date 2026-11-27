package dev.strategos.core.sim.civ;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.Construction;

/**
 * How a faction's buildings are raised (ADR-0028), at two points. {@link #onPlaced} runs when a placement command has
 * made a foundation: the default does nothing and the first builder's arrival commits it; {@link #selfBuilding}'s
 * variant commits it at once. {@link #progress} is asked once per tick for each site under construction: the work it
 * adds, in millionths of a tick at one builder's rate. The default is {@code r × n^0.7} from the builders at work;
 * {@link #selfBuilding} adds a fixed rate and needs none. Stateless: a rule keeps nothing per site.
 */
@CoreApi
public interface ConstructionRule {
    /** Today's behaviour: a builder's arrival commits the foundation, and the builders at work are the progress. */
    ConstructionRule DEFAULT = new ConstructionRule() {
        @Override
        public void onPlaced(MatchState s, int site) {}

        @Override
        public long progress(MatchState s, int site) {
            BuildingStore b = s.buildings();
            return Construction.work(b.tickBuilders(site), b.tickRates(site));
        }
    };

    /**
     * The AI hints this rule implies, a mask of {@link AiHint}s (the default needs builders). A later variant brings its
     * own hints by overriding this method; {@link CivRules} combines the seams' masks once at bind.
     */
    default int hints() {
        return AiHint.NEEDS_BUILDERS.mask();
    }

    /** Called as the placement command creates foundation {@code site}, before any builder is assigned. */
    void onPlaced(MatchState s, int site);

    /**
     * The work site {@code site} gains this tick: the builders at work are {@code s.buildings().tickBuilders(site)}, their
     * rates {@code tickRates(site)}. Zero adds nothing.
     */
    long progress(MatchState s, int site);

    /** The foundation is committed at placement and the site gains {@code rate} of one builder's work a tick, builders or none. */
    static ConstructionRule selfBuilding(double rate) {
        long perTick = Math.round(Construction.MICRO * rate);
        return new ConstructionRule() {
            @Override
            public void onPlaced(MatchState s, int site) {
                Construction.commit(s, site);
            }

            @Override
            public long progress(MatchState s, int site) {
                return perTick;
            }

            @Override
            public int hints() {
                return 0; // a foundation builds itself: the AI assigns no builder
            }
        };
    }
}
