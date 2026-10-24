package dev.strategos.core.testkit;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Gather;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.econ.NodeView;
import dev.strategos.core.sim.nav.MaterialKind;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * PERF-06's first loop (M3 spec §6), as commands for the proving ground: each of two factions gets a civic centre, a
 * barracks, five houses and three fields (20 buildings), {@code workers} workers gathering wood and food, {@code
 * soldiers} spearmen trading attack-moves across the river's fords in waves, and {@code constructions} buildings under
 * construction at any time between them: a barracks with one builder, placed on the next free site once the last one
 * stands. Every {@value #EVERY} ticks idle workers go back to work, and a wave ends when a side is gone or after
 * {@value #WAVE_TICKS} ticks, when both are filled up again. The same match gets the same commands, so the server's soak
 * and an L1 run play alike.
 */
public final class FirstLoopScene implements SoakScene {
    public static final int EVERY = 20;
    public static final int WAVE_TICKS = SoakWaves.WAVE_TICKS;
    public static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");
    public static final TemplateId BARRACKS = new TemplateId("strategos:building/barracks");
    public static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    public static final TemplateId FIELD = new TemplateId("strategos:building/field");

    /** The soak's scale and the stress pass's, twice it (M3 spec §6). */
    public record Config(int workers, int soldiers, int constructions) implements SoakScene.Spec {
        public static final Config SOAK = new Config(60, 40, 3);
        public static final Config STRESS = new Config(120, 80, 6);

        @Override
        public int units() {
            return 2 * (workers + soldiers);
        }

        @Override
        public int unitsAtStart() {
            return 2 * workers; // the soldiers come with the waves
        }

        @Override
        public SoakScene create(List<StartSlot> starts) {
            return new FirstLoopScene(this, starts);
        }
    }

    /** The first loop's own building sites round a start, east of it as for the west start (the AI's first nine, two moved a block or two so no footprints touch). */
    private static final int[][] TOWN = {
        {0, 15}, {11, 15}, {-11, 15}, {15, 0}, {16, 10}, {-15, 0}, {0, -15}, {15, -11}, {-16, 12}
    };

    private static final TemplateId[] TOWN_BUILDINGS = {BARRACKS, HOUSE, HOUSE, HOUSE, HOUSE, HOUSE, FIELD, FIELD, FIELD
    };

    /** Where constructions go: a ring of cells 8 apart, 22 to 52 blocks out, nearest first. */
    private static final int SITE_STEP = 8;

    private static final int SITE_NEAR = 22;
    private static final int SITE_FAR = 52;

    private static final double OWN_FIELD = 30;
    private static final double YARD = 26;

    private final Config config;
    private final List<StartSlot> starts;
    private final List<List<int[]>> sites = new ArrayList<>();
    private final int[] nextSite = new int[2];
    private final SoakWaves waves;
    private long placed;
    private long refused;

    public FirstLoopScene(Config config, List<StartSlot> starts) {
        this.config = config;
        this.starts = starts;
        this.waves = new SoakWaves(config.soldiers());
        for (int f = 0; f < 2; f++) {
            List<int[]> ring = new ArrayList<>();
            for (int dx = -SITE_FAR; dx <= SITE_FAR; dx += SITE_STEP) {
                for (int dz = -SITE_FAR; dz <= SITE_FAR; dz += SITE_STEP) {
                    long d2 = (long) dx * dx + (long) dz * dz;
                    if (d2 >= (long) SITE_NEAR * SITE_NEAR && d2 <= (long) SITE_FAR * SITE_FAR) {
                        ring.add(new int[] {dx, dz});
                    }
                }
            }
            ring.sort((a, b) ->
                    Long.compare((long) a[0] * a[0] + (long) a[1] * a[1], (long) b[0] * b[0] + (long) b[1] * b[1]));
            sites.add(ring);
        }
    }

    /** The first tick's commands: the resources, the 20 buildings, and the workers by their centres. */
    @Override
    public List<Issue> setup(TemplateSet templates) {
        List<Issue> out = new ArrayList<>();
        for (int f = 0; f < 2; f++) {
            FactionSlot faction = new FactionSlot(f);
            StartSlot s = starts.get(f);
            int m = mirror(f);
            out.add(admin(new Grant(faction, "wood", 1_000_000)));
            out.add(admin(new Grant(faction, "food", 1_000_000)));
            out.add(admin(new Grant(faction, "stone", 1_000_000))); // the barracks' stone and iron (M4 T5, T6)
            out.add(admin(new Grant(faction, "iron", 1_000_000)));
            out.add(admin(spawn(templates, CIVIC_CENTRE, faction, s.x() + 0.5, s.z() + 0.5)));
            for (int k = 0; k < TOWN.length; k++) {
                out.add(admin(spawn(
                        templates,
                        TOWN_BUILDINGS[k],
                        faction,
                        s.x() + 0.5 + m * TOWN[k][0],
                        s.z() + 0.5 + m * TOWN[k][1])));
            }
            out.add(admin(new SpawnUnits(TemplateSet.WORKER, faction, yard(f), config.workers(), 8)));
        }
        return out;
    }

    @Override
    public List<Issue> next(MatchQueries q) {
        List<Issue> out = new ArrayList<>();
        waves.next(q, out);
        for (int f = 0; f < 2; f++) {
            construct(q, f, out); // each tick, so a refused site costs one
        }
        if (q.tick() % EVERY != 0) {
            return out;
        }
        List<NodeView> nodes = q.nodes();
        TreeMap<Integer, Integer> taken = new TreeMap<>();
        for (int f = 0; f < 2; f++) {
            FactionSlot faction = new FactionSlot(f);
            StartSlot s = starts.get(f);
            int workers = 0;
            for (int handle : q.unitHandlesOf(faction)) {
                UnitView u = q.unit(handle);
                if (u == null || !u.template().equals(TemplateSet.WORKER)) {
                    continue;
                }
                workers++;
                if (u.state() == UnitState.IDLE) {
                    NodeView n = nearest(nodes, handle % 2 == 0 ? "wood" : "food", u, s, taken);
                    if (n == null) {
                        n = nearest(nodes, handle % 2 == 0 ? "food" : "wood", u, s, taken);
                    }
                    if (n != null) {
                        taken.merge(n.id(), 1, Integer::sum);
                        out.add(admin(Gather.replace(UnitSet.of(handle), n.topX(), n.topY(), n.topZ())));
                    }
                }
            }
            if (workers < config.workers()) {
                out.add(admin(new SpawnUnits(TemplateSet.WORKER, faction, yard(f), config.workers() - workers, 8)));
            }
        }
        return out;
    }

    @Override
    public void placementResult(boolean accepted) {
        if (accepted) {
            placed++;
        } else {
            refused++;
        }
    }

    @Override
    public long placed() {
        return placed;
    }

    @Override
    public long refusedPlacements() {
        return refused;
    }

    @Override
    public int waves() {
        return waves.waves();
    }

    /** Faction {@code f}'s share of the constructions: the first takes the odd one. */
    int quota(int f) {
        return f == 0 ? (config.constructions() + 1) / 2 : config.constructions() / 2;
    }

    private void construct(MatchQueries q, int f, List<Issue> out) {
        int running = 0;
        for (BuildingView b : q.buildings()) {
            if (b.faction() == f && b.state() != BuildingStore.COMPLETE) {
                running++;
            }
        }
        List<int[]> ring = sites.get(f);
        if (running >= quota(f) || nextSite[f] >= ring.size()) {
            return;
        }
        UnitTemplate t = q.templates().byId(BARRACKS);
        Blueprint bp = t == null ? null : q.templates().blueprint(t.netIndex());
        if (bp == null) {
            return;
        }
        StartSlot s = starts.get(f);
        int[] offset = ring.get(nextSite[f]++);
        double sx = s.x() + 0.5 + mirror(f) * offset[0];
        double sz = s.z() + 0.5 + mirror(f) * offset[1];
        int builder = nearestWorker(q, new FactionSlot(f), sx, sz);
        if (builder < 0) {
            return;
        }
        Footprint probe = Footprint.of(bp, 0, 0, 0);
        out.add(new Issue(
                new PlayerSlot(f),
                new PlaceBuilding(
                        BARRACKS,
                        (int) Math.floor(sx) - probe.spanX() / 2,
                        (int) Math.floor(sz) - probe.spanZ() / 2,
                        0,
                        UnitSet.of(builder),
                        QueueMode.REPLACE)));
    }

    /** Where workers come in: open ground 26 blocks north of the west start, beyond its town, south of the east one. */
    private Vec2d yard(int f) {
        StartSlot s = starts.get(f);
        return new Vec2d(s.x() + 0.5, s.z() + 0.5 - mirror(f) * YARD);
    }

    /** +1 for the west start, -1 for the east one, whose sites turn round. */
    private int mirror(int f) {
        return starts.get(f).x() < ProvingGround.SIZE / 2 ? 1 : -1;
    }

    private static SpawnBuilding spawn(TemplateSet templates, TemplateId id, FactionSlot faction, double x, double z) {
        UnitTemplate t = templates.byId(id);
        Blueprint bp = t == null ? null : templates.blueprint(t.netIndex());
        if (bp == null) {
            throw new IllegalArgumentException("no blueprint for " + id);
        }
        Footprint probe = Footprint.of(bp, 0, 0, 0);
        return new SpawnBuilding(
                id, faction, (int) Math.floor(x) - probe.spanX() / 2, (int) Math.floor(z) - probe.spanZ() / 2, 0);
    }

    private static @Nullable NodeView nearest(
            List<NodeView> nodes, String resource, UnitView u, StartSlot own, TreeMap<Integer, Integer> taken) {
        @Nullable NodeView best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (NodeView n : nodes) {
            if (!n.resource().equals(resource)
                    || n.amount() <= 0
                    || n.ripening() > 0
                    || !n.hasBlock()
                    || n.freeSlots() - taken.getOrDefault(n.id(), 0) <= 0) {
                continue;
            }
            if (n.kind() == MaterialKind.CROP && Math.hypot(n.anchorX() - own.x(), n.anchorZ() - own.z()) > OWN_FIELD) {
                continue;
            }
            double d = Math.hypot(n.anchorX() - u.x(), n.anchorZ() - u.z());
            if (d < bestD || (d == bestD && best != null && n.id() < best.id())) {
                bestD = d;
                best = n;
            }
        }
        return best;
    }

    private static int nearestWorker(MatchQueries q, FactionSlot faction, double x, double z) {
        int best = -1;
        double bestD = Double.POSITIVE_INFINITY;
        for (int handle : q.unitHandlesOf(faction)) {
            UnitView u = q.unit(handle);
            if (u == null || !u.template().equals(TemplateSet.WORKER) || u.state() == UnitState.BUILDING) {
                continue;
            }
            double d = Math.hypot(u.x() - x, u.z() - z);
            if (d < bestD) {
                bestD = d;
                best = handle;
            }
        }
        return best;
    }

    private static Issue admin(Command c) {
        return new Issue(PlayerSlot.ADMIN, c);
    }
}
