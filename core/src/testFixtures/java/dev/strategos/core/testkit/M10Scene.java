package dev.strategos.core.testkit;

import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.FormationMove;
import dev.strategos.core.protocol.FormationShape;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.Research;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Target;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UseAbility;
import dev.strategos.core.shared.AbilityId;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import org.jspecify.annotations.Nullable;

/**
 * PERF-17's M10 scene (M10 spec §2.20) on the first skirmish map: four factions of {@code population} population each,
 * placed at the start and handed to the computer at Hard as {@link TerritoryScene} does, and on top of that match, the
 * load M10 adds:
 *
 * <ul>
 *   <li>formations: each faction's soldiers (spearmen, then archers) in {@code formationsPerFaction} formations of
 *       {@code formationSize}, box and line by turns, marched {@value #STEP} blocks toward the middle of the map and
 *       back every {@value #LEG} ticks, and ordered again every {@value #FORM_EVERY} (the computer takes its soldiers
 *       for itself within a few ticks, and no flag keeps them from it, so the scene forms them again at once and
 *       measures the share that stands formed, {@link #formedFraction()});
 *   <li>fauna: {@code herdsPerQuadrant} wild herds of {@code herdSize} wild boar in each quadrant of the map, Gaia's;
 *   <li>abilities: every faction's horsemen use {@code charge} each {@value #CHARGE_EVERY} ticks, its cooldown;
 *   <li>the City phase: each faction is given the stock and researches Town, then City, as soon as its requirements are
 *       met, the tower and the temple the City needs (three Town buildings) spawned when Town is in.
 * </ul>
 *
 * Everything the scene sends is an admin command, the same for the same match.
 */
public final class M10Scene implements SoakScene {
    /** The soak's length: 15 game minutes at 20 ticks a second. */
    public static final int SOAK_TICKS = 18_000;

    /** The factions. */
    public static final int FACTIONS = TerritoryScene.FACTIONS;

    public static final TemplateId BOAR = new TemplateId("strategos:unit/wild_boar");
    public static final TemplateId TOWER = new TemplateId("strategos:building/tower");
    public static final TemplateId TEMPLE = new TemplateId("strategos:building/temple");
    public static final TechId TOWN = new TechId("strategos:phase_town");
    public static final TechId CITY = new TechId("strategos:phase_city");
    public static final AbilityId CHARGE = new AbilityId("charge");

    /** The ticks between two orders to a formation: the computer takes its soldiers for itself, so they are formed again. */
    static final int FORM_EVERY = 10;

    /** The ticks between two counts of the members that stand formed. */
    static final int COUNT_EVERY = 3;

    /** The ticks a formation's march takes before it turns back. */
    static final int LEG = 300;

    /** The ticks between two uses of {@code charge}: its cooldown. */
    static final int CHARGE_EVERY = 600;

    /** The ticks between two looks at a faction's phase. */
    static final int PHASE_EVERY = 100;

    /** The ticks after which a research that has not come in is asked for again. */
    private static final int RESEARCH_PATIENCE = 2_400;

    /** How far a formation marches toward the middle, in blocks. */
    static final int STEP = 24;

    /** How far from its centre a tower or temple is tried, in blocks. */
    private static final int REACH = 36;

    private static final int TRIES = 60;
    private static final long SALT = 0x4D313000L;

    /** The stock each faction is given so that Town and City are paid for. */
    private static final String[] STOCK_ID = {"food", "wood", "stone", "iron"};

    private static final long STOCK = 2_000;

    /** What Town and City cost, in {@link #STOCK_ID}'s order. */
    private static final long[] TOWN_COST = {500, 600, 300, 200};

    private static final long[] CITY_COST = {600, 700, 350, 250};

    /** The herds' places in the west's top-left quadrant, {x, z}; the others are its mirrors. */
    private static final double[][] HERDS = {{130.5, 40.5}, {135.5, 100.5}, {128.5, 70.5}, {138.5, 20.5}};

    /**
     * A scale: the population of each faction, how many formations of how many units it keeps, how many herds of how
     * many boars lie in each quadrant, and the computer's script. {@link #SOAK} is 150, one formation of 40 and two
     * herds of 6; {@link #STRESS} twice that in everything but the herd's size, with population 300 to have the
     * soldiers; neither has a script until {@link #withAi} gives it Hard's.
     */
    public record Config(
            int population,
            int buildings,
            int formationsPerFaction,
            int formationSize,
            int herdsPerQuadrant,
            int herdSize,
            String ai)
            implements SoakScene.Spec {
        public static final Config SOAK = new Config(150, 30, 1, 40, 2, 6, "");
        public static final Config STRESS = new Config(300, 60, 2, 40, 4, 6, "");

        public Config {
            if (population < 30
                    || buildings < 6
                    || formationsPerFaction < 0
                    || formationSize < 2
                    || formationSize > UnitSet.MAX_SIZE
                    || herdsPerQuadrant < 0
                    || herdsPerQuadrant > HERDS.length
                    || herdSize < 1) {
                throw new IllegalArgumentException("this scale is not valid");
            }
            MvpScene.Config soldiers = new MvpScene.Config(population, buildings, ai);
            if (formationsPerFaction * formationSize > soldiers.spearmen() + soldiers.archers()) {
                throw new IllegalArgumentException(formationsPerFaction + " formations of " + formationSize
                        + " need more soldiers than " + population + " population holds");
            }
        }

        /** This scale, its factions played by {@code skirmishJson}'s Hard level. */
        public Config withAi(String skirmishJson) {
            return new Config(
                    population,
                    buildings,
                    formationsPerFaction,
                    formationSize,
                    herdsPerQuadrant,
                    herdSize,
                    MvpScene.Config.SOAK.withAi(skirmishJson).ai());
        }

        MvpScene.Config perFaction() {
            return new MvpScene.Config(population, buildings, ai);
        }

        /** The wild animals the map holds: four quadrants' herds. */
        public int animals() {
            return 4 * herdsPerQuadrant * herdSize;
        }

        @Override
        public int units() {
            return FACTIONS * perFaction().unitsPerFaction() + animals();
        }

        @Override
        public int unitsAtStart() {
            return units();
        }

        @Override
        public SoakScene create(List<StartSlot> starts) {
            return new M10Scene(this, starts);
        }
    }

    /** A formation: its members' handles, where it stands at home and the way it marches. */
    private static final class Formation {
        final int[] members;
        final Vec2d home;
        final Vec2d away;
        final FormationShape shape;

        Formation(int[] members, Vec2d home, Vec2d away, FormationShape shape) {
            this.members = members;
            this.home = home;
            this.away = away;
            this.shape = shape;
        }
    }

    private final Config config;
    private final MvpScene base;
    private final List<Formation> formations = new ArrayList<>();
    private final long[] researchSent = new long[FACTIONS * 2];
    private @Nullable SplittableRandom random;
    private boolean formed;
    private boolean justFormed;
    private long marches;
    private long charges;
    private long researches;
    private long spawned;
    private long refused;
    private long formedSum; // members that stood formed, summed over the counts of the window
    private long memberSum; // members alive at those counts

    public M10Scene(Config config, List<StartSlot> starts) {
        this.config = config;
        this.base = new MvpScene(config.perFaction(), starts, FACTIONS, TerritoryScene.NORTH_SHIFT);
        Arrays.fill(researchSent, -RESEARCH_PATIENCE);
    }

    @Override
    public List<Issue> setup(TemplateSet templates) {
        List<Issue> out = new ArrayList<>(base.setup(templates));
        for (int f = 0; f < FACTIONS; f++) {
            for (String resource : STOCK_ID) {
                out.add(admin(new Grant(new FactionSlot(f), resource, STOCK)));
            }
        }
        FactionSlot gaia = new FactionSlot(FACTIONS);
        for (int h = 0; h < config.herdsPerQuadrant(); h++) {
            double x = HERDS[h][0];
            double z = HERDS[h][1];
            double mx = FirstSkirmish.SIZE - x; // the mirrors: east, south
            double mz = FirstSkirmish.SIZE - z;
            for (double[] at : new double[][] {{x, z}, {mx, z}, {x, mz}, {mx, mz}}) {
                out.add(admin(new SpawnUnits(BOAR, gaia, new Vec2d(at[0], at[1]), config.herdSize(), 3)));
            }
        }
        return out;
    }

    @Override
    public List<Issue> next(MatchQueries q) {
        long tick = q.tick();
        SplittableRandom rng = random;
        if (rng == null) {
            rng = new SplittableRandom(q.config().seed() ^ SALT);
            random = rng;
        }
        List<Issue> out = new ArrayList<>();
        if (formed && tick % COUNT_EVERY == 0) {
            count(q);
        }
        if (!formed) {
            form(q);
            formed = true;
            justFormed = true;
        }
        boolean first = justFormed;
        if (first || tick % FORM_EVERY == 0) {
            march(q, tick / LEG + 1, out); // the first leg goes away, the next back
        }
        justFormed = false;
        if (tick % CHARGE_EVERY == CHARGE_EVERY / 2) {
            charge(q, out);
        }
        if (first || tick % PHASE_EVERY == 0) {
            phases(q, tick, rng, out);
        }
        return out;
    }

    // ---- the formations

    /** Picks each faction's formations from its spearmen and then its archers, the lowest handles first. */
    private void form(MatchQueries q) {
        for (int f = 0; f < FACTIONS; f++) {
            List<Integer> soldiers = new ArrayList<>();
            for (int handle : q.unitHandlesOf(new FactionSlot(f))) {
                UnitView u = q.unit(handle);
                if (u != null && u.template().equals(TemplateSet.INFANTRY)) {
                    soldiers.add(handle);
                }
            }
            for (int handle : q.unitHandlesOf(new FactionSlot(f))) {
                UnitView u = q.unit(handle);
                if (u != null && u.template().equals(BattleScene.ARCHER)) {
                    soldiers.add(handle);
                }
            }
            for (int k = 0; k < config.formationsPerFaction(); k++) {
                int[] members = new int[config.formationSize()];
                double x = 0;
                double z = 0;
                for (int m = 0; m < members.length; m++) {
                    members[m] = soldiers.get(k * members.length + m);
                    UnitView u = q.unit(members[m]);
                    if (u != null) {
                        x += u.x();
                        z += u.z();
                    }
                }
                Vec2d home = new Vec2d(x / members.length, z / members.length);
                double dx = home.x() < FirstSkirmish.SIZE / 2.0 ? STEP : -STEP;
                // two formations of a faction march on different rows, so that they do not walk through each other
                double dz = (k % 2 == 0 ? 1 : -1) * 6.0 * k;
                formations.add(new Formation(
                        members,
                        home,
                        new Vec2d(home.x() + dx, home.z() + dz),
                        (f + k) % 2 == 0 ? FormationShape.BOX : FormationShape.LINE));
            }
        }
    }

    private void march(MatchQueries q, long round, List<Issue> out) {
        for (Formation f : formations) {
            int[] alive = alive(q, f.members);
            if (alive.length < 2) {
                continue;
            }
            Vec2d to = round % 2 == 0 ? f.home : f.away;
            out.add(admin(FormationMove.replace(UnitSet.of(alive), to, f.shape)));
            marches++;
        }
    }

    /** Counts the members that stand in a formation now (ORD-06): one look at {@value #COUNT_EVERY} ticks. */
    private void count(MatchQueries q) {
        for (Formation f : formations) {
            for (int handle : f.members) {
                if (q.unit(handle) != null) {
                    memberSum++;
                    if (q.formationOf(handle) != 0) {
                        formedSum++;
                    }
                }
            }
        }
    }

    /** Starts a window of the count: the share reported covers the counts since. */
    public void startWindow() {
        formedSum = 0;
        memberSum = 0;
    }

    /** The share of the formations' members that stood formed at the window's counts (1 when none was made). */
    public double formedFraction() {
        return memberSum == 0 ? 1.0 : (double) formedSum / memberSum;
    }

    /** What each faction has researched so far: {@code village}, {@code town} or {@code city}. */
    public String[] phaseNames(MatchQueries q) {
        String[] names = new String[FACTIONS];
        for (int f = 0; f < FACTIONS; f++) {
            List<TechId> done = q.researched(new FactionSlot(f));
            names[f] = done.contains(CITY) ? "city" : done.contains(TOWN) ? "town" : "village";
        }
        return names;
    }

    private static int[] alive(MatchQueries q, int[] handles) {
        int n = 0;
        int[] kept = new int[handles.length];
        for (int handle : handles) {
            if (q.unit(handle) != null) {
                kept[n++] = handle;
            }
        }
        return Arrays.copyOf(kept, n);
    }

    // ---- the abilities

    private void charge(MatchQueries q, List<Issue> out) {
        for (int f = 0; f < FACTIONS; f++) {
            int[] riders = new int[UnitSet.MAX_SIZE];
            int n = 0;
            for (int handle : q.unitHandlesOf(new FactionSlot(f))) {
                UnitView u = q.unit(handle);
                if (u != null && n < riders.length && u.template().equals(BattleScene.HORSEMAN)) {
                    riders[n++] = handle;
                }
            }
            if (n > 0) {
                out.add(admin(UseAbility.replace(UnitSet.of(Arrays.copyOf(riders, n)), CHARGE, Target.none())));
                charges++;
            }
        }
    }

    // ---- the City phase

    private void phases(MatchQueries q, long tick, SplittableRandom rng, List<Issue> out) {
        for (int f = 0; f < FACTIONS; f++) {
            FactionSlot faction = new FactionSlot(f);
            List<TechId> done = q.researched(faction);
            BuildingView centre = centre(q, f);
            if (centre == null || done.contains(CITY)) {
                continue;
            }
            if (!done.contains(TOWN)) {
                research(centre, TOWN, TOWN_COST, 2 * f, tick, out);
            } else if (!has(q, f, TOWER)) { // one at a time: two in a tick may ask for the same cells
                spawn(q, rng, f, TOWER, out);
            } else if (!has(q, f, TEMPLE)) {
                spawn(q, rng, f, TEMPLE, out);
            } else {
                research(centre, CITY, CITY_COST, 2 * f + 1, tick, out);
            }
        }
    }

    private static boolean has(MatchQueries q, int faction, TemplateId template) {
        for (BuildingView b : q.buildings()) {
            if (b.faction() == faction
                    && b.state() == BuildingStore.COMPLETE
                    && b.template().equals(template)) {
                return true;
            }
        }
        return false;
    }

    /** Asks for {@code tech} at {@code centre}, with its cost given first so that the computer's spending cannot refuse it. */
    private void research(BuildingView centre, TechId tech, long[] cost, int slot, long tick, List<Issue> out) {
        if (tick - researchSent[slot] >= RESEARCH_PATIENCE) {
            researchSent[slot] = tick;
            for (int r = 0; r < STOCK_ID.length; r++) {
                out.add(admin(new Grant(new FactionSlot(centre.faction()), STOCK_ID[r], cost[r])));
            }
            out.add(admin(new Research(centre.handle(), tech)));
            researches++;
        }
    }

    private static @Nullable BuildingView centre(MatchQueries q, int faction) {
        for (BuildingView b : q.buildings()) {
            if (b.faction() == faction
                    && b.state() == BuildingStore.COMPLETE
                    && b.template().equals(BattleScene.CIVIC_CENTRE)) {
                return b;
            }
        }
        return null;
    }

    /** A new {@code template} within {@value #REACH} blocks of {@code faction}'s centre, on cells nothing stands on. */
    private void spawn(MatchQueries q, SplittableRandom rng, int faction, TemplateId template, List<Issue> out) {
        BuildingView centre = centre(q, faction);
        if (centre == null) {
            return;
        }
        int[] span = MvpScene.span(q.templates(), template);
        for (int k = 0; k < TRIES; k++) {
            int x = centre.footprint().anchorX() + rng.nextInt(2 * REACH + 1) - REACH;
            int z = centre.footprint().anchorZ() + rng.nextInt(2 * REACH + 1) - REACH;
            if (free(q, x, z, span)) {
                out.add(admin(new SpawnBuilding(template, new FactionSlot(faction), x, z, 0)));
                spawned++;
                return;
            }
        }
        refused++;
    }

    private static boolean free(MatchQueries q, int x, int z, int[] span) {
        if (x < 1 || z < 1) {
            return false;
        }
        for (int dx = -1; dx <= span[0]; dx++) {
            for (int dz = -1; dz <= span[1]; dz++) {
                if (q.footprintAt(x + dx, z + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void placementResult(boolean accepted) {}

    @Override
    public long placed() {
        return spawned;
    }

    @Override
    public long refusedPlacements() {
        return refused;
    }

    @Override
    public int waves() {
        return 0;
    }

    /** The formations the scene keeps, and the units of each at the start. */
    public int formationCount() {
        return formations.size();
    }

    public int formationSize() {
        return config.formationSize();
    }

    /** The formations' marches, the charges and the researches asked for so far. */
    public long marches() {
        return marches;
    }

    public long charges() {
        return charges;
    }

    public long researches() {
        return researches;
    }

    private static Issue admin(Command c) {
        return new Issue(PlayerSlot.ADMIN, c);
    }
}
