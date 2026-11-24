package dev.strategos.core.testkit;

import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Demolish;
import dev.strategos.core.protocol.Garrison;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.jspecify.annotations.Nullable;

/**
 * PERF-16's territory and garrison scene (M9 spec §2.13) on the first skirmish map: four factions of {@code population}
 * population each, all placed at the start as {@link MvpScene} places them and handed to the computer at Hard, two on
 * the map's two starts and two more beside them ({@value #NORTH_SHIFT} blocks north on the west, the east's mirror), so
 * the neighbours' territories meet. On top of the match:
 *
 * <ul>
 *   <li>a churn driver: every {@code churnTicks} ticks, rotating over the factions, one of a house or a sentry tower is
 *       completed, destroyed or transferred, in that order for a faction's turns, its pick drawn from the match's seed.
 *       {@code BuildingLife.transfer} has no command, so a transfer is the building's demolition and its respawn on the
 *       same cells for the next faction: the territory system sees the same source change;
 *   <li>a garrison keeper: every {@value #KEEP_EVERY} ticks it tops each faction up to {@code garrisoned / 4} units
 *       inside its centre, houses and towers (workers in the centre and houses, infantry in the centre and towers),
 *       since the computer takes units out again.
 * </ul>
 *
 * Everything the scene sends is an admin command, the same for the same match.
 */
// AFTER-M7: PERF-10's four-client pass of this scene.
public final class TerritoryScene implements SoakScene {
    /** The soak's length: 15 game minutes at 20 ticks a second. */
    public static final int SOAK_TICKS = 18_000;

    /** The 30 seconds between two churn events of the soak. */
    public static final int CHURN_TICKS = 600;

    public static final TemplateId SENTRY_TOWER = new TemplateId("strategos:building/sentry_tower");

    /** The factions. */
    public static final int FACTIONS = 4;

    /** How far north of the west's pad the second west faction stands. */
    static final int NORTH_SHIFT = 90;

    /** The ticks between two garrison top-ups. */
    static final int KEEP_EVERY = 100;

    /** The ticks after which a unit's garrison order that has not arrived is given again. */
    private static final int ORDER_PATIENCE = 400;

    /** How far from its centre a completed building is tried, in blocks. */
    private static final int REACH = 36;

    private static final int TRIES = 60;
    private static final long SALT = 0x7E221700L;

    /**
     * A scale: the population of each faction, the units held garrisoned in all, the ticks between churn events and the
     * computer's script. {@link #SOAK} is 150, 40 and 30 seconds, {@link #STRESS} twice the churn and the garrisons;
     * neither has a script until {@link #withAi} gives it Hard's.
     */
    public record Config(int population, int garrisoned, int churnTicks, String ai) implements SoakScene.Spec {
        public static final Config SOAK = new Config(150, 40, CHURN_TICKS, "");
        public static final Config STRESS = new Config(150, 80, CHURN_TICKS / 2, "");

        public Config {
            if (population < 30 || garrisoned < 0 || churnTicks < 1) {
                throw new IllegalArgumentException(
                        "a scale of " + population + ", " + garrisoned + " and " + churnTicks + " is not valid");
            }
        }

        /** This scale, its factions played by {@code skirmishJson}'s Hard level. */
        public Config withAi(String skirmishJson) {
            return new Config(
                    population,
                    garrisoned,
                    churnTicks,
                    MvpScene.Config.SOAK.withAi(skirmishJson).ai());
        }

        /** This scale with {@code ticks} between two churn events. */
        public Config withChurnTicks(int ticks) {
            return new Config(population, garrisoned, ticks, ai);
        }

        MvpScene.Config perFaction() {
            return new MvpScene.Config(population, MvpScene.Config.SOAK.buildings(), ai);
        }

        @Override
        public int units() {
            return FACTIONS * perFaction().unitsPerFaction();
        }

        @Override
        public int unitsAtStart() {
            return units();
        }

        @Override
        public SoakScene create(List<StartSlot> starts) {
            return new TerritoryScene(this, starts);
        }
    }

    private final Config config;
    private final MvpScene base;
    private final Map<Integer, Long> ordered = new HashMap<>();
    private @Nullable SplittableRandom random;
    private long churnEvents;
    private long completed;
    private long destroyed;
    private long transferred;
    private long refused;
    private long garrisonOrders;

    public TerritoryScene(Config config, List<StartSlot> starts) {
        this.config = config;
        this.base = new MvpScene(config.perFaction(), starts, FACTIONS, NORTH_SHIFT);
    }

    @Override
    public List<Issue> setup(TemplateSet templates) {
        return base.setup(templates);
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
        if (tick > 0 && tick % config.churnTicks() == 0) {
            churn(q, rng, out);
        }
        if (config.garrisoned() > 0 && tick % KEEP_EVERY == KEEP_EVERY / 2) {
            keep(q, tick, out);
        }
        return out;
    }

    // ---- the churn

    private void churn(MatchQueries q, SplittableRandom rng, List<Issue> out) {
        long n = churnEvents++;
        int faction = (int) (n % FACTIONS);
        switch ((int) ((n / FACTIONS) % 3)) {
            case 0 -> complete(q, rng, faction, (n / (3 * FACTIONS)) % 2 == 0 ? MvpScene.HOUSE : SENTRY_TOWER, out);
            case 1 -> {
                BuildingView b = pick(q, rng, faction);
                if (b != null) {
                    out.add(admin(new Demolish(b.handle())));
                    destroyed++;
                }
            }
            default -> {
                BuildingView b = pick(q, rng, faction);
                if (b != null) {
                    int next = (faction + 1) % FACTIONS;
                    out.add(admin(new Demolish(b.handle())));
                    out.add(admin(new SpawnBuilding(
                            b.template(),
                            new FactionSlot(next),
                            b.footprint().anchorX(),
                            b.footprint().anchorZ(),
                            b.footprint().rotation())));
                    transferred++;
                }
            }
        }
    }

    /** A finished house or sentry tower of {@code faction}, drawn from the match's seed; null if it has none. */
    private static @Nullable BuildingView pick(MatchQueries q, SplittableRandom rng, int faction) {
        List<BuildingView> own = new ArrayList<>();
        for (BuildingView b : q.buildings()) {
            if (b.faction() == faction
                    && b.state() == BuildingStore.COMPLETE
                    && (b.template().equals(MvpScene.HOUSE) || b.template().equals(SENTRY_TOWER))) {
                own.add(b);
            }
        }
        return own.isEmpty() ? null : own.get(rng.nextInt(own.size()));
    }

    /** A new {@code template} within {@value #REACH} blocks of {@code faction}'s centre, on cells nothing stands on. */
    private void complete(MatchQueries q, SplittableRandom rng, int faction, TemplateId template, List<Issue> out) {
        BuildingView centre = null;
        for (BuildingView b : q.buildings()) {
            if (b.faction() == faction && b.template().equals(BattleScene.CIVIC_CENTRE)) {
                centre = b;
                break;
            }
        }
        if (centre == null) {
            return;
        }
        int[] span = MvpScene.span(q.templates(), template);
        for (int k = 0; k < TRIES; k++) {
            int x = centre.footprint().anchorX() + rng.nextInt(2 * REACH + 1) - REACH;
            int z = centre.footprint().anchorZ() + rng.nextInt(2 * REACH + 1) - REACH;
            if (free(q, x, z, span)) {
                out.add(admin(new SpawnBuilding(template, new FactionSlot(faction), x, z, 0)));
                completed++;
                return;
            }
        }
        refused++;
    }

    /** Whether the span cells from ({@code x}, {@code z}) and a cell's margin around them hold no building. */
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

    // ---- the garrison keeper

    private void keep(MatchQueries q, long tick, List<Issue> out) {
        int perFaction = (config.garrisoned() + FACTIONS - 1) / FACTIONS;
        ordered.values().removeIf(at -> tick - at > ORDER_PATIENCE);
        List<BuildingView> all = q.buildings();
        for (int f = 0; f < FACTIONS; f++) {
            int inside = 0;
            int pending = 0;
            List<UnitView> free = new ArrayList<>();
            for (int handle : q.unitHandlesOf(new FactionSlot(f))) {
                UnitView u = q.unit(handle);
                if (u == null) {
                    continue;
                }
                if (u.state() == UnitState.GARRISONED) {
                    inside++;
                } else if (ordered.containsKey(handle)) {
                    pending++;
                } else {
                    free.add(u);
                }
            }
            int need = perFaction - inside - pending;
            if (need <= 0) {
                continue;
            }
            List<BuildingView> holders = new ArrayList<>();
            for (BuildingView b : all) {
                if (b.faction() == f && b.state() == BuildingStore.COMPLETE) {
                    holders.add(b);
                }
            }
            Map<Integer, Integer> taken = new HashMap<>();
            for (UnitView u : free) {
                if (need <= 0) {
                    break;
                }
                for (BuildingView b : holders) {
                    if (q.garrisonRoom(u.handle(), b.handle()) - taken.getOrDefault(b.handle(), 0) > 0) {
                        taken.merge(b.handle(), 1, Integer::sum);
                        ordered.put(u.handle(), tick);
                        out.add(admin(new Garrison(UnitSet.of(u.handle()), b.handle())));
                        garrisonOrders++;
                        need--;
                        break;
                    }
                }
            }
        }
    }

    /** The units of the whole match that are inside a building now. */
    public static int garrisoned(MatchQueries q) {
        int n = 0;
        for (int handle : q.unitHandles()) {
            UnitView u = q.unit(handle);
            if (u != null && u.state() == UnitState.GARRISONED) {
                n++;
            }
        }
        return n;
    }

    @Override
    public void placementResult(boolean accepted) {}

    @Override
    public long placed() {
        return completed;
    }

    @Override
    public long refusedPlacements() {
        return refused;
    }

    @Override
    public int waves() {
        return 0;
    }

    /** Churn events so far: completions, demolitions and transfers asked for. */
    public long churnEvents() {
        return churnEvents;
    }

    public long completed() {
        return completed;
    }

    public long destroyed() {
        return destroyed;
    }

    public long transferred() {
        return transferred;
    }

    public long garrisonOrders() {
        return garrisonOrders;
    }

    private static Issue admin(dev.strategos.core.protocol.Command c) {
        return new Issue(PlayerSlot.ADMIN, c);
    }
}
