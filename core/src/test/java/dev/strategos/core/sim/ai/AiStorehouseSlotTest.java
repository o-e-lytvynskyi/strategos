package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.econ.NodeView;
import dev.strategos.core.sim.nav.MaterialKind;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * QA-12's storehouse defect in L1: with stone plentiful the dropsite rule put a storehouse over the work slot beside a
 * grove where a gatherer stood, and the unit walked for ever to a cell it could not stand on (a "WALKING without
 * progress" exception; seeds 5, 7 and 12 of the AI-09 match did it, and a storehouse flush with a slot's cell left a one-block lane that jammed the same way). A site now keeps every live node's work slots
 * free, and a lane beside them: no building the computer players place stands on one, and the matches end without an exception.
 */
final class AiStorehouseSlotTest {
    static final long[] SEEDS = {5, 7, 12};
    static final long GRANT_AT = 12_000;
    static final long TICKS = 32_000;

    @Test
    void noBuildingStandsOnALiveNodesWorkSlot() {
        String normal = Levels.resolve(HeadlessMatch.read("ai/skirmish.json"), "normal");
        for (long seed : SEEDS) {
            List<String> covered = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();
            HeadlessMatch.Result r = HeadlessMatch.run(
                    HeadlessMatch.Ground.firstSkirmish(),
                    DifficultyTest.firstSkirmish(),
                    seed,
                    TICKS,
                    List.of(normal, normal),
                    (h, out) -> {
                        var q = h.core().queries();
                        if (out.tick() == GRANT_AT) {
                            var treasury = KernelAccess.state(h.core()).treasury();
                            var resources = q.config().rules().economy().resources();
                            for (int f = 0; f < 2; f++) {
                                for (int k = 0; k < resources.size(); k++) {
                                    String id = resources.get(k).id();
                                    if (id.equals("stone") || id.equals("iron")) {
                                        treasury.grant(f, k, 1_500_000);
                                    }
                                }
                            }
                        }
                        if (out.tick() % 100 != 0) {
                            return;
                        }
                        for (var b : q.buildings()) {
                            {
                                if (!seen.add(b.handle())) {
                                    continue;
                                }
                                var f = b.footprint();
                                for (var n : q.nodes()) {
                                    if (n.amount() <= 0) {
                                        continue;
                                    }
                                    for (int cell : q.nodeSlotCells(n.id())) {
                                        int x = cell >>> 16;
                                        int z = cell & 0xFFFF;
                                        if (x >= f.anchorX()
                                                && x < f.anchorX() + f.spanX()
                                                && z >= f.anchorZ()
                                                && z < f.anchorZ() + f.spanZ()) {
                                            covered.add("seed " + seed + " " + b.template() + " at " + f.anchorX() + ","
                                                    + f.anchorZ() + " covers a slot of node " + n.id());
                                        }
                                    }
                                }
                            }
                        }
                    },
                    false,
                    HeadlessMatch.FOGGED);
            assertThat(r.summary().failure()).as("seed " + seed).isEmpty();
            assertThat(covered).as("buildings on work slots").isEmpty();
        }
    }

    /** A grove's work slots: a column of cells on open ground, no footprint beside them to lean the rule on. */
    private static final int SLOT_X = 40;

    private static final int[] SLOTS = {(SLOT_X << 16) | 38, (SLOT_X << 16) | 40, (SLOT_X << 16) | 42};

    /**
     * The site {@code SiteFinder} gives a storehouse wanted with its middle at ({@code mx}, {@code mz}), over queries
     * that hold one live node whose work slots are {@link #SLOTS}.
     */
    private static int[] siteNear(DepthScene s, int mx, int mz) {
        MatchQueries real = s.h.core().queries();
        NodeView grove = new NodeView(
                1,
                MaterialKind.LOG,
                "wood",
                "",
                1_000,
                SLOT_X,
                40,
                1,
                0,
                SLOTS.length,
                SLOTS.length,
                1_000,
                0,
                SLOT_X,
                8,
                40,
                0);
        MatchQueries q = (MatchQueries) Proxy.newProxyInstance(
                MatchQueries.class.getClassLoader(), new Class<?>[] {MatchQueries.class}, (proxy, m, args) -> {
                    if (m.getName().equals("nodes")) {
                        return List.of(grove);
                    }
                    if (m.getName().equals("nodeSlotCells")) {
                        return SLOTS.clone();
                    }
                    return m.invoke(real, args);
                });
        AiBudget budget = new AiBudget(AiBudget.CAP);
        AiView view = new AiView(q, DepthScene.OWN.value(), budget);
        int[] site = SiteFinder.find(view, budget, Base.of(view), DepthScene.STOREHOUSE, mx + 0.5, mz + 0.5, -1, true);
        assertThat(site).as("a site near " + mx + "," + mz).isNotNull();
        return site;
    }

    private static boolean laneTouches(int[] corner, Footprint probe) {
        for (int cell : SLOTS) {
            int x = cell >>> 16;
            int z = cell & 0xFFFF;
            if (x >= corner[0] - SiteFinder.GAP / 2
                    && x < corner[0] + probe.spanX() + SiteFinder.GAP / 2
                    && z >= corner[1] - SiteFinder.GAP / 2
                    && z < corner[1] + probe.spanZ() + SiteFinder.GAP / 2) {
                return true;
            }
        }
        return false;
    }

    /** The rule alone: a site over a slot, or flush with one, is refused; one a lane's width clear is taken. */
    @Test
    void aSiteOverOrFlushWithAWorkSlotIsRefusedAndOneALaneAwayIsTaken() {
        DepthScene s = new DepthScene();
        var queries = s.h.core().queries();
        Footprint probe = Footprint.of(
                queries.templates()
                        .blueprint(
                                queries.templates().byId(DepthScene.STOREHOUSE).netIndex()),
                0,
                0,
                0);
        int half = probe.spanX() / 2;
        int zMid = 40; // the middle row of the column
        int over = SLOT_X - 1; // corners: on the column
        int flush = SLOT_X + 1; // beside it, no lane
        int clear = SLOT_X + 1 + SiteFinder.GAP / 2; // a lane's width away
        for (int corner : new int[] {over, flush}) {
            int[] site = siteNear(s, corner + half, zMid + probe.spanZ() / 2);
            assertThat(laneTouches(site, probe))
                    .as("corner " + corner + " got " + site[0] + "," + site[1])
                    .isFalse();
        }
        int[] free = siteNear(s, clear + half, zMid + probe.spanZ() / 2);
        assertThat(free[0]).as("a clear site is the wanted one").isEqualTo(clear);
        assertThat(free[1]).isEqualTo(zMid);
    }
}
