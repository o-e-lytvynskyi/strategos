package dev.strategos.core.sim.prod;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.econ.EconomyRules;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;

/** A production test's match: a flat 64², faction 0's civic centre at (2, 2) and as much food and wood as asked. */
final class ProdScene {
    static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    static final PlayerSlot P0 = new PlayerSlot(0);

    final SimHarness h;
    final MatchState s;
    final int centre;

    ProdScene(SimHarness h, int centre) {
        this.h = h;
        this.s = KernelAccess.state(h.core());
        this.centre = centre;
    }

    static ProdScene of(long food, long wood) {
        return of(food, wood, MatchRules.DEFAULTS, VoxelFixtures.flat(64, 64, 32, 1), SimHarness.DEFAULT_SEED);
    }

    static ProdScene of(long food, long wood, MatchRules rules, VoxelWorld world, long seed) {
        SimHarness h = SimHarness.builder()
                .size(64, 64)
                .yBand(0, 31, 1)
                .rules(rules)
                .seed(seed)
                .build();
        h.publishAll(world);
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(0), 2, 2, 0));
        if (food > 0) {
            h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "food", food));
        }
        if (wood > 0) {
            h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "wood", wood));
        }
        h.step();
        MatchState s = KernelAccess.state(h.core());
        assertThat(s.buildings().handles()).hasSize(1);
        return new ProdScene(h, s.buildings().handles()[0]);
    }

    static MatchRules withMax(int max) {
        MatchRules d = MatchRules.DEFAULTS;
        return new MatchRules(
                d.commandsPerTick(),
                d.unitCap(),
                d.delivery(),
                d.pathK(),
                d.pageK(),
                d.admissionW(),
                d.maxOutstanding(),
                d.busyRetry(),
                d.searchCap(),
                d.joinCap(),
                d.shortMove(),
                d.groupMin(),
                new EconomyRules(d.economy().resources(), max));
    }

    /** Faction 0 queues a worker at the centre; the result after the step. */
    CommandResult train() {
        h.submit(P0, new Train(centre, TemplateSet.WORKER));
        h.step();
        return h.results().get(h.results().size() - 1);
    }

    /** The population counted afresh by scanning the match, to check the counters against: used, housing. */
    int[] scanned(int faction) {
        int used = 0;
        for (int i = 0; i < s.entities().highWater(); i++) {
            if (s.entities().aliveAt(i) && s.identity().faction(i) == faction) {
                used += s.unitPopulation(i);
            }
        }
        int housing = 0;
        BuildingStore b = s.buildings();
        for (int handle : b.handles()) {
            if (b.faction(handle) != faction) {
                continue;
            }
            if (b.state(handle) == BuildingStore.COMPLETE) {
                housing += Production.housing(s, handle);
            }
            if (b.queueSize(handle) > 0 && b.reserved(handle)) {
                used += s.templatePopulation(b.queue(handle)[0], faction);
            }
        }
        return new int[] {used, housing};
    }
}
