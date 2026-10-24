package dev.strategos.core.testkit;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.Build;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Gather;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.Reseed;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnDeposit;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.econ.NodeView;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * PERF-07's economy (M4 spec §6), as commands for the proving ground. Each of two factions lays out a town of 30 round
 * its start, one a tick on the first free site of a ring 8 blocks a cell that the player's placement rules take: a
 * civic centre, a barracks, two storehouses, a farmstead, 14 houses, 9 fields and a large quarry and iron mine (the
 * deposits nobody's); {@code workers} workers gather by their handle's turn at trees, quarries, mines, fields and
 * animals (reseeding a field that runs out), {@code animals} sheep a side stand by their start and come back as they
 * are eaten; {@code soldiers} spearmen trade attack-moves across the fords in M3's waves; {@code constructions}
 * barracks go up at any time, one builder each, on the ring's next free sites; and {@code repairs} houses are under
 * repair: one crushed to 60 % as vanilla damage, then two workers sent to mend it. The same match gets the same
 * commands, so the server's soak and an L1 run play alike.
 */
public final class EconomyScene implements SoakScene {
    public static final int EVERY = 20;
    public static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");
    public static final TemplateId BARRACKS = new TemplateId("strategos:building/barracks");
    public static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    public static final TemplateId FIELD = new TemplateId("strategos:building/field");
    public static final TemplateId STOREHOUSE = new TemplateId("strategos:building/storehouse");
    public static final TemplateId FARMSTEAD = new TemplateId("strategos:building/farmstead");
    public static final TemplateId QUARRY = new TemplateId("strategos:building/quarry_large");
    public static final TemplateId IRON_MINE = new TemplateId("strategos:building/iron_mine_large");
    public static final TemplateId SHEEP = new TemplateId("strategos:unit/sheep");

    /** The soak's scale, M4's row of the budget matrix, and the stress pass's, twice it (M4 spec §6). */
    public record Config(int workers, int soldiers, int constructions, int repairs, int animals)
            implements SoakScene.Spec {
        public static final Config SOAK = new Config(75, 75, 5, 2, 8);
        public static final Config STRESS = new Config(150, 150, 10, 4, 16);

        @Override
        public int units() {
            return 2 * (workers + soldiers + animals);
        }

        @Override
        public int unitsAtStart() {
            return 2 * (workers + animals); // the soldiers come with the waves
        }

        @Override
        public SoakScene create(List<StartSlot> starts) {
            return new EconomyScene(this, starts);
        }
    }

    /** The town after its centre, in the order it is laid out: the deposits first, while the ring's near cells are free. */
    private static final List<TemplateId> TOWN = town();

    /** The ring's cells: 8 blocks apart, 12 to 52 blocks out, nearest first. */
    private static final int STEP = 8;

    private static final int NEAR = 12;
    private static final int FAR = 52;
    /** Where workers and sheep come in, from the start, toward the map's middle row and away from it. */
    private static final double YARD = 26;

    private static final double PASTURE = 30;
    /** How much of a house a repair's blow takes. */
    private static final double CRUSH = 0.4;

    private final Config config;
    private final List<StartSlot> starts;
    private final SoakWaves waves;
    private final List<List<int[]>> rings = new ArrayList<>();
    private final int[] laid = new int[2];
    private final int[] nextSite = new int[2];
    private final int[] repairing = {-1, -1};
    private final List<ExternalDamage> blows = new ArrayList<>();
    private long placed;
    private long refused;

    public EconomyScene(Config config, List<StartSlot> starts) {
        this.config = config;
        this.starts = starts;
        this.waves = new SoakWaves(config.soldiers());
        for (int f = 0; f < 2; f++) {
            List<int[]> ring = new ArrayList<>();
            for (int dx = -FAR; dx <= FAR; dx += STEP) {
                for (int dz = -FAR; dz <= FAR; dz += STEP) {
                    long d2 = (long) dx * dx + (long) dz * dz;
                    if (d2 >= (long) NEAR * NEAR && d2 <= (long) FAR * FAR) {
                        ring.add(new int[] {dx, dz});
                    }
                }
            }
            ring.sort((a, b) -> {
                int c = Long.compare((long) a[0] * a[0] + (long) a[1] * a[1], (long) b[0] * b[0] + (long) b[1] * b[1]);
                return c != 0 ? c : a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]);
            });
            rings.add(ring);
        }
    }

    private static List<TemplateId> town() {
        List<TemplateId> out = new ArrayList<>(List.of(QUARRY, IRON_MINE, STOREHOUSE, FARMSTEAD, BARRACKS, STOREHOUSE));
        for (int k = 0; k < 9; k++) {
            out.add(FIELD);
        }
        for (int k = 0; k < 14; k++) {
            out.add(HOUSE);
        }
        return List.copyOf(out);
    }

    /** The first tick's commands: the stock, the civic centres, the workers and the sheep. */
    @Override
    public List<Issue> setup(TemplateSet templates) {
        List<Issue> out = new ArrayList<>();
        for (int f = 0; f < 2; f++) {
            FactionSlot faction = new FactionSlot(f);
            StartSlot s = starts.get(f);
            for (String r : List.of("food", "wood", "stone", "iron")) {
                out.add(admin(new Grant(faction, r, 1_000_000)));
            }
            out.add(admin(spawn(templates, CIVIC_CENTRE, faction, s.x() + 0.5, s.z() + 0.5)));
            out.add(admin(new SpawnUnits(TemplateSet.WORKER, faction, yard(f), config.workers(), 8)));
            out.add(admin(new SpawnUnits(SHEEP, gaia(), pasture(f), config.animals(), 3)));
        }
        return out;
    }

    @Override
    public List<Issue> next(MatchQueries q) {
        List<Issue> out = new ArrayList<>();
        blows.clear();
        waves.next(q, out);
        for (int f = 0; f < 2; f++) {
            if (laid[f] < TOWN.size()) {
                layOut(q, f, out);
                continue;
            }
            construct(q, f, out);
            repair(q, f, out);
        }
        if (q.tick() % EVERY != 0) {
            return out;
        }
        List<NodeView> nodes = q.nodes();
        Map<Integer, Integer> taken = new TreeMap<>();
        for (int f = 0; f < 2; f++) {
            gather(q, f, nodes, taken, out);
            reseed(q, f, out);
        }
        int sheep = 0;
        for (int handle : q.unitHandlesOf(gaia())) {
            sheep += q.unit(handle) != null ? 1 : 0;
        }
        for (int f = 0; f < 2 && sheep < 2 * config.animals(); f++) {
            out.add(admin(new SpawnUnits(SHEEP, gaia(), pasture(f), 1, 3))); // eaten ones come back, a pair a second
        }
        return out;
    }

    @Override
    public List<ExternalDamage> damage(MatchQueries q) {
        return List.copyOf(blows);
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

    /** Whether faction {@code f}'s town is laid out. */
    public boolean townDone(int f) {
        return laid[f] >= TOWN.size();
    }

    /** The town's next building on the ring's first site the rules take; a tick each. */
    private void layOut(MatchQueries q, int f, List<Issue> out) {
        TemplateId id = TOWN.get(laid[f]);
        int[] at = site(q, f, id);
        if (at == null) {
            laid[f] = TOWN.size(); // the ring is full: the town is what fits
            return;
        }
        laid[f]++;
        Command c = id.equals(QUARRY) || id.equals(IRON_MINE)
                ? new SpawnDeposit(id, at[0], at[1])
                : new SpawnBuilding(id, new FactionSlot(f), at[0], at[1], 0);
        out.add(admin(c));
    }

    /** The corner of the ring's next site for {@code id} that faction {@code f}'s placement rules take, or null. */
    private int @Nullable [] site(MatchQueries q, int f, TemplateId id) {
        UnitTemplate t = q.templates().byId(id);
        Blueprint bp = t == null ? null : q.templates().blueprint(t.netIndex());
        if (bp == null) {
            return null;
        }
        Footprint probe = Footprint.of(bp, 0, 0, 0);
        List<int[]> ring = rings.get(f);
        StartSlot s = starts.get(f);
        while (nextSite[f] < ring.size()) {
            int[] offset = ring.get(nextSite[f]++);
            int x = (int) Math.floor(s.x() + 0.5 + mirror(f) * offset[0]) - probe.spanX() / 2;
            int z = (int) Math.floor(s.z() + 0.5 + mirror(f) * offset[1]) - probe.spanZ() / 2;
            if (x >= 0 && z >= 0 && q.placementRefusal(t.netIndex(), x, z, f) == null) {
                return new int[] {x, z};
            }
        }
        return null;
    }

    /** Faction {@code f}'s share of the constructions: a barracks with one builder on the ring's next free site. */
    private void construct(MatchQueries q, int f, List<Issue> out) {
        int running = 0;
        for (BuildingView b : q.buildings()) {
            if (b.faction() == f && b.state() != BuildingStore.COMPLETE) {
                running++;
            }
        }
        if (running >= share(config.constructions(), f)) {
            return;
        }
        int[] at = site(q, f, BARRACKS);
        if (at == null) {
            return;
        }
        UnitTemplate t = q.templates().byId(BARRACKS);
        Blueprint bp = t == null ? null : q.templates().blueprint(t.netIndex());
        if (bp == null) {
            return;
        }
        Footprint f0 = Footprint.of(bp, at[0], at[1], 0);
        int builder = nearestWorker(q, f, f0.anchorX() + f0.spanX() / 2.0, f0.anchorZ() + f0.spanZ() / 2.0, -1);
        if (builder >= 0) {
            out.add(new Issue(
                    new PlayerSlot(f),
                    new PlaceBuilding(BARRACKS, at[0], at[1], 0, UnitSet.of(builder), QueueMode.REPLACE)));
        }
    }

    /**
     * Faction {@code f}'s share of the repairs: while its house under repair is damaged, two workers mend it; once it
     * is whole, the next house standing is crushed to 60 % and the workers sent the tick after.
     */
    private void repair(MatchQueries q, int f, List<Issue> out) {
        if (share(config.repairs(), f) == 0) {
            return;
        }
        BuildingView house = null;
        for (BuildingView b : q.buildings()) {
            if (b.handle() == repairing[f]) {
                house = b;
            }
        }
        if (house != null && house.hitPoints() < house.maxHitPoints()) {
            if (q.tick() % EVERY == 0) {
                int menders = 0;
                for (int handle : q.unitHandlesOf(new FactionSlot(f))) {
                    menders += buildingSite(q, handle) == house.handle() ? 1 : 0;
                }
                Footprint at = house.footprint();
                for (int k = menders; k < 2; k++) {
                    int w = nearestWorker(q, f, at.anchorX() + 2.5, at.anchorZ() + 2.5, house.handle());
                    if (w >= 0) {
                        out.add(admin(Build.replace(UnitSet.of(w), house.handle())));
                    }
                }
            }
            return;
        }
        for (BuildingView b : q.buildings()) { // the lowest handle after the last one, round again past the end
            if (b.faction() == f
                    && b.template().equals(HOUSE)
                    && b.state() == BuildingStore.COMPLETE
                    && b.handle() > repairing[f]) {
                repairing[f] = b.handle();
                blows.add(new ExternalDamage(
                        b.handle(),
                        ExternalDamage.Kind.CRUSH,
                        (float) (CRUSH * b.maxHitPoints()),
                        ExternalDamage.SourceKind.NONE));
                return;
            }
        }
        repairing[f] = -1;
    }

    /** Idle workers to work by their handle's turn (three of eight at trees, two at fields, one each at the rest). */
    private void gather(MatchQueries q, int f, List<NodeView> nodes, Map<Integer, Integer> taken, List<Issue> out) {
        int workers = 0;
        for (int handle : q.unitHandlesOf(new FactionSlot(f))) {
            UnitView u = q.unit(handle);
            if (u == null || !u.template().equals(TemplateSet.WORKER)) {
                continue;
            }
            workers++;
            if (u.state() != UnitState.IDLE) {
                continue;
            }
            NodeView n = nearest(nodes, turn(handle), u, f, taken);
            if (n == null) {
                n = nearest(nodes, Kind.WOOD, u, f, taken);
            }
            if (n != null) {
                taken.merge(n.id(), 1, Integer::sum);
                out.add(admin(Gather.replace(UnitSet.of(handle), n.topX(), n.topY(), n.topZ())));
            }
        }
        if (workers < config.workers()) {
            out.add(admin(
                    new SpawnUnits(TemplateSet.WORKER, new FactionSlot(f), yard(f), config.workers() - workers, 8)));
        }
    }

    /** Faction {@code f}'s fields reaped bare are sown again (BLD-08's manual reseed; their wood is granted). */
    private void reseed(MatchQueries q, int f, List<Issue> out) {
        for (BuildingView b : q.buildings()) {
            if (b.faction() != f || !b.template().equals(FIELD) || b.state() != BuildingStore.COMPLETE) {
                continue;
            }
            for (NodeView n : q.nodes()) {
                if (n.kind() == MaterialKind.CROP
                        && n.amount() <= 0
                        && n.ripening() == 0
                        && inside(b.footprint(), n.anchorX(), n.anchorZ())) {
                    out.add(new Issue(new PlayerSlot(f), new Reseed(b.handle())));
                }
            }
        }
    }

    private enum Kind {
        WOOD,
        CROP,
        STONE,
        IRON,
        MEAT
    }

    private static Kind turn(int handle) {
        return switch (Math.floorMod(handle, 8)) {
            case 0, 1, 2 -> Kind.WOOD;
            case 3, 4 -> Kind.CROP;
            case 5 -> Kind.STONE;
            case 6 -> Kind.IRON;
            default -> Kind.MEAT;
        };
    }

    private @Nullable NodeView nearest(
            List<NodeView> nodes, Kind kind, UnitView u, int f, Map<Integer, Integer> taken) {
        StartSlot own = starts.get(f);
        NodeView best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (NodeView n : nodes) {
            boolean wanted = switch (kind) {
                case WOOD -> n.resource().equals("wood");
                case CROP -> n.kind() == MaterialKind.CROP;
                case STONE -> n.resource().equals("stone");
                case IRON -> n.resource().equals("iron");
                case MEAT -> n.kind() == MaterialKind.CARCASS;
            };
            if (!wanted
                    || n.amount() <= 0
                    || n.ripening() > 0
                    || !n.hasBlock()
                    || n.freeSlots() - taken.getOrDefault(n.id(), 0) <= 0
                    || (kind != Kind.WOOD && DetMath.length(n.anchorX() - own.x(), n.anchorZ() - own.z()) > FAR + 8)) {
                continue; // only wood is fetched from afar; the rest is the town's own
            }
            double d = DetMath.length(n.anchorX() - u.x(), n.anchorZ() - u.z());
            if (d < bestD) {
                bestD = d;
                best = n;
            }
        }
        return best;
    }

    /** Faction {@code f}'s share of {@code n}: the first takes the odd one. */
    private static int share(int n, int f) {
        return f == 0 ? (n + 1) / 2 : n / 2;
    }

    private static int nearestWorker(MatchQueries q, int f, double x, double z, int besides) {
        int best = -1;
        double bestD = Double.POSITIVE_INFINITY;
        for (int handle : q.unitHandlesOf(new FactionSlot(f))) {
            UnitView u = q.unit(handle);
            if (u == null
                    || !u.template().equals(TemplateSet.WORKER)
                    || u.state() == UnitState.BUILDING
                    || buildingSite(q, handle) == besides) {
                continue;
            }
            double d = DetMath.length(u.x() - x, u.z() - z);
            if (d < bestD) {
                bestD = d;
                best = handle;
            }
        }
        return best;
    }

    /** The building {@code handle}'s current order builds, or 0. */
    private static int buildingSite(MatchQueries q, int handle) {
        List<OrderQueues.Order> orders = q.orders(handle);
        return orders.isEmpty() || orders.get(0).kind() != OrderKind.BUILD
                ? 0
                : orders.get(0).target();
    }

    private static boolean inside(Footprint f, double x, double z) {
        return x >= f.anchorX() && x < f.anchorX() + f.spanX() && z >= f.anchorZ() && z < f.anchorZ() + f.spanZ();
    }

    private Vec2d yard(int f) {
        StartSlot s = starts.get(f);
        return new Vec2d(s.x() + 0.5, s.z() + 0.5 - mirror(f) * YARD);
    }

    private Vec2d pasture(int f) {
        StartSlot s = starts.get(f);
        return new Vec2d(s.x() + 0.5 + mirror(f) * PASTURE, s.z() + 0.5 - mirror(f) * YARD);
    }

    /** +1 for the west start, -1 for the east one, whose sites turn round. */
    private int mirror(int f) {
        return starts.get(f).x() < ProvingGround.SIZE / 2 ? 1 : -1;
    }

    private static FactionSlot gaia() {
        return new FactionSlot(2);
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

    private static Issue admin(Command c) {
        return new Issue(PlayerSlot.ADMIN, c);
    }
}
