package dev.strategos.core.testkit;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.AttackMove;
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
public final class FirstLoopScene {
    public static final int EVERY = 20;
    public static final int WAVE_TICKS = 600;
    public static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");
    public static final TemplateId BARRACKS = new TemplateId("strategos:building/barracks");
    public static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    public static final TemplateId FIELD = new TemplateId("strategos:building/field");

    /** The soak's scale and the stress pass's, twice it (M3 spec §6). */
    public record Config(int workers, int soldiers, int constructions) {
        public static final Config SOAK = new Config(60, 40, 3);
        public static final Config STRESS = new Config(120, 80, 6);

        public int units() {
            return 2 * (workers + soldiers);
        }
    }

    /** A command and who sends it: the admin, or a faction's seat for a placement. */
    public record Issue(PlayerSlot issuer, Command command) {}

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

    private static final double[] FORD_Z = {143.5, 239.5};
    private static final double BANK = 20.5;
    private static final double OWN_FIELD = 30;
    private static final double YARD = 26;

    private final Config config;
    private final List<StartSlot> starts;
    private final List<List<int[]>> sites = new ArrayList<>();
    private final int[] nextSite = new int[2];
    private long placed;
    private long refused;
    private int waves;
    private int waveTicks;
    private boolean sendWave;

    public FirstLoopScene(Config config, List<StartSlot> starts) {
        this.config = config;
        this.starts = starts;
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
    public List<Issue> setup(TemplateSet templates) {
        List<Issue> out = new ArrayList<>();
        for (int f = 0; f < 2; f++) {
            FactionSlot faction = new FactionSlot(f);
            StartSlot s = starts.get(f);
            int m = mirror(f);
            out.add(admin(new Grant(faction, "wood", 1_000_000)));
            out.add(admin(new Grant(faction, "food", 1_000_000)));
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

    /** The commands after the step of {@code q.tick()}, for the next. */
    public List<Issue> next(MatchQueries q) {
        List<Issue> out = new ArrayList<>();
        wave(q, out);
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

    /** Placements accepted and refused so far, as the caller reported them. */
    public void placementResult(boolean accepted) {
        if (accepted) {
            placed++;
        } else {
            refused++;
        }
    }

    public long placed() {
        return placed;
    }

    public long refusedPlacements() {
        return refused;
    }

    public int waves() {
        return waves;
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

    /** The waves: both sides filled up to {@code soldiers} at their bank's fords, then sent across to the other's. */
    private void wave(MatchQueries q, List<Issue> out) {
        int[][] sides = new int[2][];
        for (int f = 0; f < 2; f++) {
            List<Integer> soldiers = new ArrayList<>();
            for (int handle : q.unitHandlesOf(new FactionSlot(f))) {
                UnitView u = q.unit(handle);
                if (u != null && u.template().equals(TemplateSet.INFANTRY)) {
                    soldiers.add(handle);
                }
            }
            sides[f] = soldiers.stream().mapToInt(Integer::intValue).toArray();
        }
        if (sendWave) {
            sendWave = false;
            waves++;
            waveTicks = 0;
            for (int f = 0; f < 2; f++) {
                for (int k = 0; k < 2; k++) {
                    List<Integer> here = new ArrayList<>();
                    for (int unit : sides[f]) {
                        UnitView u = q.unit(unit);
                        if (u != null && (u.z() < 191.5) == (k == 0)) {
                            here.add(unit);
                        }
                    }
                    if (!here.isEmpty()) {
                        out.add(admin(AttackMove.replace(
                                UnitSet.of(here.stream()
                                        .mapToInt(Integer::intValue)
                                        .toArray()),
                                new Vec2d(bank(1 - f), FORD_Z[k]))));
                    }
                }
            }
            return;
        }
        if (++waveTicks < WAVE_TICKS && sides[0].length > 0 && sides[1].length > 0 && waves > 0) {
            return;
        }
        for (int f = 0; f < 2; f++) {
            int count = config.soldiers() - sides[f].length;
            for (int k = 0; k < 2 && count > 0; k++) {
                int here = k == 0 ? (count + 1) / 2 : count / 2;
                if (here > 0) {
                    out.add(admin(new SpawnUnits(
                            TemplateSet.INFANTRY, new FactionSlot(f), new Vec2d(bank(f), FORD_Z[k]), here, 5)));
                }
            }
        }
        sendWave = true; // the tick after, when the new units are in the match
    }

    /** Where workers come in: open ground 26 blocks north of the west start, beyond its town, south of the east one. */
    private Vec2d yard(int f) {
        StartSlot s = starts.get(f);
        return new Vec2d(s.x() + 0.5, s.z() + 0.5 - mirror(f) * YARD);
    }

    private static double bank(int f) {
        return f == 0 ? ProvingGround.RIVER_X - BANK : ProvingGround.RIVER_X + BANK;
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
                    || n.topY() < 0
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
