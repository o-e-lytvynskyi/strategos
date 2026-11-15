package dev.strategos.core.testkit;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.SetAi;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnDeposit;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.ai.Levels;
import java.util.ArrayList;
import java.util.List;

/**
 * PERF-09's MVP match (M6 spec §6) on the first skirmish map (MAP-02): each of two factions holds {@code population}
 * population all placed at the start, since a real match may never hold 2 × 150 at one tick: workers (two fifths),
 * spearmen, archers and horsemen of the M5 roster and rams in a squad (3 population each), and {@code buildings}
 * finished buildings (a civic centre, barracks, an arsenal, storehouses and houses for the rest) packed round its start,
 * with the scenario's quarry and iron mine beside it. Both factions are handed to the computer at Hard ({@link
 * Config#withAi}), which sends the workers to the nodes and the soldiers to war; the scene sends nothing after the
 * first tick. The match runs under fog and {@code Victory.NONE}, so it cannot end early; that is the match's rules, not
 * the scene's. The east is the west's turned half round.
 */
public final class MvpScene implements SoakScene {
    /** The soak's length: 30 game minutes at 20 ticks a second. */
    public static final int SOAK_TICKS = 36_000;

    public static final TemplateId RAM = new TemplateId("strategos:unit/ram");
    public static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    public static final TemplateId BARRACKS = new TemplateId("strategos:building/barracks");
    public static final TemplateId ARSENAL = new TemplateId("strategos:building/arsenal");
    public static final TemplateId STOREHOUSE = new TemplateId("strategos:building/storehouse");
    public static final TemplateId QUARRY = new TemplateId("strategos:building/quarry");
    public static final TemplateId IRON_MINE = new TemplateId("strategos:building/iron_mine");

    /** The scenario's stock a faction starts with: no wood. */
    private static final String[] STOCK_ID = {"food", "stone", "iron"};

    private static final long[] STOCK = {300, 100, 100};
    /** A ram's population. */
    private static final int RAM_POPULATION = 3;

    /** The most map columns of a start's side that buildings take, in the west's frame: x from 6 to 100. */
    private static final int X_MIN = 6;

    private static final int X_MAX = 100;
    private static final int Z_MIN = 98;
    private static final int Z_MAX = 190;
    /** Cells left free between two buildings. */
    private static final int GAP = 1;

    /**
     * What stays free in the west's frame, {x0, z0, x1, z1} inclusive: the groves, the bushes, the room for the fields
     * (MAP-02's generator) and the two yards the units come into.
     */
    private static final int[][] RESERVED = {
        {56, 104, 76, 124},
        {18, 150, 38, 170},
        {56, 144, 63, 157},
        {52, 160, 84, 184},
        {15, 127, 33, 145}, // the soldiers' yard
        {60, 129, 76, 143} // the workers' yard
    };

    private static final Vec2d SOLDIER_YARD = new Vec2d(24.5, 136.5);
    private static final double SOLDIER_SPREAD = 9;
    private static final Vec2d WORKER_YARD = new Vec2d(68.5, 136.5);
    private static final double WORKER_SPREAD = 7;
    private static final int[] QUARRY_AT = {-14, -22};
    private static final int[] IRON_AT = {6, -26};

    /**
     * A scale: the population and the finished buildings a faction holds, and the computer's script. {@link #SOAK} is
     * 150 and 30, {@link #STRESS} twice that (M6 spec §6); neither has a script until {@link #withAi} gives it Hard's,
     * as {@code Levels.resolve} makes it from the shipped {@code ai/skirmish.json}.
     */
    public record Config(int population, int buildings, String ai) implements SoakScene.Spec {
        public static final Config SOAK = new Config(150, 30, "");
        public static final Config STRESS = new Config(300, 60, "");

        public Config {
            if (population < 30 || buildings < 6) {
                throw new IllegalArgumentException("a scale of " + population + " and " + buildings + " is too small");
            }
        }

        /** This scale, its factions played by {@code skirmishJson}'s Hard level. */
        public Config withAi(String skirmishJson) {
            return new Config(population, buildings, Levels.resolve(skirmishJson, Levels.TOP));
        }

        int workers() {
            return population * 2 / 5;
        }

        int spearmen() {
            return population * 6 / 25;
        }

        int archers() {
            return population * 4 / 25;
        }

        int rams() {
            return population / 30;
        }

        int horsemen() {
            return population - workers() - spearmen() - archers() - rams() * RAM_POPULATION;
        }

        /** A faction's units: the population less what its rams take beyond one each. */
        int unitsPerFaction() {
            return workers() + spearmen() + archers() + horsemen() + rams();
        }

        int barracks() {
            return buildings / 15;
        }

        int arsenals() {
            return buildings / 30;
        }

        int storehouses() {
            return buildings / 15;
        }

        int houses() {
            return buildings - 1 - barracks() - arsenals() - storehouses();
        }

        @Override
        public int units() {
            return 2 * unitsPerFaction();
        }

        @Override
        public int unitsAtStart() {
            return units(); // everything is there from the first tick
        }

        @Override
        public SoakScene create(List<StartSlot> starts) {
            return new MvpScene(this, starts);
        }
    }

    private final Config config;
    private final List<StartSlot> starts;

    public MvpScene(Config config, List<StartSlot> starts) {
        if (starts.size() < 2) {
            throw new IllegalArgumentException("the scene needs two start slots");
        }
        this.config = config;
        this.starts = starts;
    }

    @Override
    public List<Issue> setup(TemplateSet templates) {
        List<int[]> taken = new ArrayList<>();
        for (int[] r : RESERVED) {
            taken.add(r);
        }
        Placed quarry = deposit(templates, QUARRY, QUARRY_AT, taken);
        Placed iron = deposit(templates, IRON_MINE, IRON_AT, taken);
        List<Placed> layout = layout(templates, taken);
        List<Issue> out = new ArrayList<>();
        for (int f = 0; f < 2; f++) {
            FactionSlot faction = new FactionSlot(f);
            boolean east = east(f);
            if (!config.ai().isEmpty()) {
                out.add(admin(new SetAi(faction, Levels.PROFILE, config.ai())));
            }
            for (int k = 0; k < STOCK_ID.length; k++) {
                out.add(admin(new Grant(faction, STOCK_ID[k], STOCK[k])));
            }
            for (Placed p : layout) {
                out.add(admin(new SpawnBuilding(p.template(), faction, anchorX(p, east), anchorZ(p, east), 0)));
            }
            for (Placed d : List.of(quarry, iron)) {
                out.add(admin(new SpawnDeposit(d.template(), anchorX(d, east), anchorZ(d, east))));
            }
            units(out, faction, east);
        }
        return out;
    }

    private void units(List<Issue> out, FactionSlot faction, boolean east) {
        Vec2d soldiers = at(SOLDIER_YARD, east);
        spawn(out, TemplateSet.INFANTRY, faction, soldiers, config.spearmen(), SOLDIER_SPREAD);
        spawn(out, BattleScene.ARCHER, faction, soldiers, config.archers(), SOLDIER_SPREAD);
        spawn(out, BattleScene.HORSEMAN, faction, soldiers, config.horsemen(), SOLDIER_SPREAD);
        spawn(out, RAM, faction, soldiers, config.rams(), SOLDIER_SPREAD);
        spawn(out, TemplateSet.WORKER, faction, at(WORKER_YARD, east), config.workers(), WORKER_SPREAD);
    }

    private static void spawn(
            List<Issue> out, TemplateId template, FactionSlot faction, Vec2d at, int count, double r) {
        for (int left = count; left > 0; left -= 300) { // a spawn takes at most 300
            out.add(admin(new SpawnUnits(template, faction, at, Math.min(left, 300), r)));
        }
    }

    // ---- the layout, in the west's frame

    /** A building of the layout: its template, corner and span. */
    private record Placed(TemplateId template, int x, int z, int spanX, int spanZ) {}

    private static Placed deposit(TemplateSet templates, TemplateId id, int[] offset, List<int[]> taken) {
        int[] span = span(templates, id);
        int x = FirstSkirmish.START_X + offset[0] - span[0] / 2;
        int z = FirstSkirmish.START_Z + offset[1] - span[1] / 2;
        taken.add(new int[] {x, z, x + span[0] - 1, z + span[1] - 1});
        return new Placed(id, x, z, span[0], span[1]);
    }

    private List<Placed> layout(TemplateSet templates, List<int[]> taken) {
        List<Placed> out = new ArrayList<>();
        int[] cc = span(templates, BattleScene.CIVIC_CENTRE);
        Placed centre = new Placed(
                BattleScene.CIVIC_CENTRE,
                FirstSkirmish.START_X - cc[0] / 2,
                FirstSkirmish.START_Z - cc[1] / 2,
                cc[0],
                cc[1]);
        take(centre, taken);
        out.add(centre);
        List<TemplateId> order = new ArrayList<>();
        for (int k = 0; k < config.barracks(); k++) {
            order.add(BARRACKS);
        }
        for (int k = 0; k < config.arsenals(); k++) {
            order.add(ARSENAL);
        }
        for (int k = 0; k < config.storehouses(); k++) {
            order.add(STOREHOUSE);
        }
        for (int k = 0; k < config.houses(); k++) {
            order.add(HOUSE);
        }
        for (TemplateId id : order) {
            int[] span = span(templates, id);
            Placed p = nearest(id, span, taken);
            take(p, taken);
            out.add(p);
        }
        return out;
    }

    /** The free spot for a building of {@code span}, nearest the start by Chebyshev distance, then by x and z. */
    private static Placed nearest(TemplateId id, int[] span, List<int[]> taken) {
        Placed best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int x = X_MIN; x + span[0] - 1 <= X_MAX; x++) {
            for (int z = Z_MIN; z + span[1] - 1 <= Z_MAX; z++) {
                int distance = Math.max(
                        Math.abs(x + span[0] / 2 - FirstSkirmish.START_X),
                        Math.abs(z + span[1] / 2 - FirstSkirmish.START_Z));
                if (distance < bestDistance && free(x, z, span, taken)) {
                    best = new Placed(id, x, z, span[0], span[1]);
                    bestDistance = distance;
                }
            }
        }
        if (best == null) {
            throw new IllegalStateException("no room for a " + id.value() + " on the start's side");
        }
        return best;
    }

    private static boolean free(int x, int z, int[] span, List<int[]> taken) {
        for (int[] r : taken) {
            if (x - GAP <= r[2] && x + span[0] - 1 + GAP >= r[0] && z - GAP <= r[3] && z + span[1] - 1 + GAP >= r[1]) {
                return false;
            }
        }
        return true;
    }

    private static void take(Placed p, List<int[]> taken) {
        taken.add(new int[] {p.x(), p.z(), p.x() + p.spanX() - 1, p.z() + p.spanZ() - 1});
    }

    private static int[] span(TemplateSet templates, TemplateId id) {
        UnitTemplate t = templates.byId(id);
        Blueprint bp = t == null ? null : templates.blueprint(t.netIndex());
        if (bp == null) {
            throw new IllegalArgumentException("no blueprint for " + id);
        }
        Footprint probe = Footprint.of(bp, 0, 0, 0);
        return new int[] {probe.spanX(), probe.spanZ()};
    }

    // ---- the east, the west turned half round

    private boolean east(int f) {
        return starts.get(f).x() >= FirstSkirmish.SIZE / 2;
    }

    private static int anchorX(Placed p, boolean east) {
        return east ? FirstSkirmish.SIZE - p.x() - p.spanX() : p.x();
    }

    private static int anchorZ(Placed p, boolean east) {
        return east ? FirstSkirmish.SIZE - p.z() - p.spanZ() : p.z();
    }

    private static Vec2d at(Vec2d west, boolean east) {
        return east ? new Vec2d(FirstSkirmish.SIZE - west.x(), FirstSkirmish.SIZE - west.z()) : west;
    }

    @Override
    public List<Issue> next(MatchQueries q) {
        return List.of(); // everything is placed at the start; the computer plays on
    }

    @Override
    public void placementResult(boolean accepted) {}

    @Override
    public long placed() {
        return 0;
    }

    @Override
    public long refusedPlacements() {
        return 0;
    }

    @Override
    public int waves() {
        return 0;
    }

    private static Issue admin(dev.strategos.core.protocol.Command c) {
        return new Issue(PlayerSlot.ADMIN, c);
    }
}
