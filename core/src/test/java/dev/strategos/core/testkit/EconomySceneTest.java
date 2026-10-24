package dev.strategos.core.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitDied;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.econ.NodeView;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.sim.order.OrderKind;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * PERF-07's scene (M4 spec §6) on the core alone: the economy as the server's soak plays it, its 15 game minutes on the
 * proving ground. Both towns stand within the first ten seconds, about 60 buildings of the players'; the five
 * constructions run in at least 95 % of the seconds after the first half minute and the two repairs in at least 90 %;
 * workers are at trees, fields, quarries, mines and carcasses all along; the waves fight and die.
 */
class EconomySceneTest {
    private static final int TICKS = 18_000;

    @Test
    void theEconomyRunsAsTheSoakNeedsIt() {
        ProvingGround ground = ProvingGround.generate();
        SimHarness h = SimHarness.builder()
                .size(ProvingGround.SIZE, ProvingGround.SIZE)
                .yBand(0, ProvingGround.HEIGHT, 16)
                .seed(SimHarness.DEFAULT_SEED) // the server soak's match
                .factions(2)
                .build();
        h.publishAll(ground.voxels());
        h.step();
        EconomyScene.Config config = EconomyScene.Config.SOAK;
        EconomyScene scene = new EconomyScene(config, ground.startSlots());
        for (SoakScene.Issue i : scene.setup(TemplateSet.builtin())) {
            h.submit(i.issuer(), i.command());
        }
        long deaths = 0;
        int sampled = 0; // seconds after the first half minute
        int constructing = 0;
        int repairing = 0;
        Map<String, Long> at = new TreeMap<>();
        int townAt = -1;
        int ownBuildings = 0;
        for (int t = 0; t < TICKS; t++) {
            TickOutputs out = h.step();
            MatchQueries q = h.core().queries();
            for (OutputEvent e : out.events()) {
                if (e instanceof UnitDied) {
                    deaths++;
                } else if (e instanceof CommandResult r && r.kind().equals("strategos:place_building")) {
                    scene.placementResult(r.outcome() == CommandOutcome.ACCEPTED);
                }
            }
            if (townAt < 0 && scene.townDone(0) && scene.townDone(1)) {
                townAt = t;
                ownBuildings = (int)
                        q.buildings().stream().filter(b -> b.faction() < 2).count();
            }
            if (t > 600 && t % 20 == 0) {
                sampled++;
                int sites = 0;
                int damaged = 0;
                for (BuildingView b : q.buildings()) {
                    if (b.faction() < 2 && b.state() != BuildingStore.COMPLETE) {
                        sites++;
                    } else if (b.faction() < 2 && b.hitPoints() < b.maxHitPoints()) {
                        damaged++;
                    }
                }
                constructing += sites >= config.constructions() ? 1 : 0;
                repairing += damaged >= config.repairs() ? 1 : 0;
                count(q, at);
            }
            for (SoakScene.Issue i : scene.next(q)) {
                h.submit(i.issuer(), i.command());
            }
            for (ExternalDamage d : scene.damage(q)) {
                h.damage(d);
            }
        }
        assertThat(townAt).as("both towns laid out within ten seconds").isBetween(0, 200);
        assertThat(ownBuildings).as("the players' buildings once laid out").isBetween(56, 60);
        assertThat(constructing).as("seconds with five constructions").isGreaterThanOrEqualTo(sampled * 95 / 100);
        assertThat(repairing).as("seconds with two repairs").isGreaterThanOrEqualTo(sampled * 90 / 100);
        for (String kind : List.of("wood", "crop", "stone", "iron", "meat")) {
            assertThat(at.getOrDefault(kind, 0L) / (double) sampled)
                    .as("gatherers at " + kind + " on average")
                    .isGreaterThanOrEqualTo(2);
        }
        assertThat(scene.waves()).as("waves").isGreaterThanOrEqualTo(25);
        assertThat(deaths).as("deaths in the waves").isGreaterThan(100);
    }

    /** The workers of both factions on a gather order, by what they gather. */
    private static void count(MatchQueries q, Map<String, Long> at) {
        Map<Integer, NodeView> nodes = new TreeMap<>();
        for (NodeView n : q.nodes()) {
            nodes.put(n.id(), n);
        }
        for (int handle : q.unitHandles()) {
            var orders = q.orders(handle);
            if (orders.isEmpty() || orders.get(0).kind() != OrderKind.GATHER) {
                continue;
            }
            NodeView n = nodes.get(orders.get(0).target());
            if (n == null) {
                continue;
            }
            String kind =
                    n.kind() == MaterialKind.CROP ? "crop" : n.kind() == MaterialKind.CARCASS ? "meat" : n.resource();
            at.merge(kind, 1L, Long::sum);
        }
    }
}
