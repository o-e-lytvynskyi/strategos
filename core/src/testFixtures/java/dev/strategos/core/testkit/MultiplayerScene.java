package dev.strategos.core.testkit;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.SetAi;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnDeposit;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.ai.Levels;
import dev.strategos.core.sim.build.BuildingStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The PERF-10 map has one town and a full MVP roster for each of four primary factions. */
public final class MultiplayerScene implements SoakScene {
    public static final int FACTIONS = 4;
    private static final int CAMP_SEPARATION = 230;
    private static final int CAMP_START = 20;
    private static final int BUILD_GRID = 7;
    private static final int BUILD_STEP = 13;
    private static final int REFILL_TICKS = 5;
    private final Config config;
    private final List<RosterPart> roster;
    private final List<List<TownSite>> sites = new ArrayList<>();
    private long unitReplacements;
    private long buildingReplacements;

    private record RosterPart(TemplateId template, int count, boolean workers) {}

    private static final class TownSite {
        final SpawnBuilding spawn;
        private final int[] columns;

        TownSite(SpawnBuilding spawn, int[] columns) {
            this.spawn = spawn;
            this.columns = columns;
        }

        boolean free(MatchQueries q) {
            for (int cell : columns) {
                if (q.footprintAt(cell >>> 16, cell & 0xFFFF)) return false;
            }
            return true;
        }
    }

    private boolean startedAi;

    public record Config(MvpScene.Config scale) implements SoakScene.Spec {
        @Override
        public SoakScene create(List<StartSlot> starts) {
            return new MultiplayerScene(this);
        }

        @Override
        public int units() {
            return FACTIONS * scale.unitsPerFaction();
        }

        @Override
        public int unitsAtStart() {
            return units();
        }
    }

    private MultiplayerScene(Config config) {
        this.config = config;
        var scale = config.scale();
        roster = List.of(
                new RosterPart(TemplateSet.INFANTRY, scale.spearmen(), false),
                new RosterPart(BattleScene.ARCHER, scale.archers(), false),
                new RosterPart(BattleScene.HORSEMAN, scale.horsemen(), false),
                new RosterPart(MvpScene.RAM, scale.rams(), false),
                new RosterPart(TemplateSet.WORKER, scale.workers(), true));
    }

    public static Vec2d viewer(int faction) {
        return new Vec2d(campX(faction) + 39.5, campZ(faction) + 39.5);
    }

    private static int campX(int faction) {
        return CAMP_START + faction % 2 * CAMP_SEPARATION;
    }

    private static int campZ(int faction) {
        return CAMP_START + faction / 2 * CAMP_SEPARATION;
    }

    @Override
    public List<Issue> setup(TemplateSet templates) {
        var layout = new ArrayList<TemplateId>();
        var scale = config.scale();
        layout.add(BattleScene.CIVIC_CENTRE);
        for (int i = 0; i < scale.barracks(); i++) layout.add(MvpScene.BARRACKS);
        for (int i = 0; i < scale.arsenals(); i++) layout.add(MvpScene.ARSENAL);
        for (int i = 0; i < scale.storehouses(); i++) layout.add(MvpScene.STOREHOUSE);
        for (int i = 0; i < scale.houses(); i++) layout.add(MvpScene.HOUSE);
        var out = new ArrayList<Issue>();
        for (int f = 0; f < FACTIONS; f++) {
            var faction = new FactionSlot(f);
            int x = campX(f);
            int z = campZ(f);
            var town = new ArrayList<TownSite>();
            sites.add(town);
            for (int i = 0; i < layout.size(); i++) {
                TemplateId id = layout.get(i);
                int bx = x + i % BUILD_GRID * BUILD_STEP;
                int bz = z + i / BUILD_GRID * BUILD_STEP;
                var spawn = new SpawnBuilding(id, faction, bx, bz, 0);
                var template = Objects.requireNonNull(templates.byId(id));
                var blueprint = Objects.requireNonNull(templates.blueprint(template.netIndex()));
                town.add(new TownSite(spawn, Footprint.of(blueprint, bx, bz, 0).columns(blueprint)));
                out.add(new Issue(PlayerSlot.ADMIN, spawn));
            }
            out.add(new Issue(PlayerSlot.ADMIN, new SpawnDeposit(MvpScene.QUARRY, x + 8, z + 119)));
            out.add(new Issue(PlayerSlot.ADMIN, new SpawnDeposit(MvpScene.IRON_MINE, x + 20, z + 119)));
            out.add(new Issue(PlayerSlot.ADMIN, new Grant(faction, "food", 300)));
            out.add(new Issue(PlayerSlot.ADMIN, new Grant(faction, "stone", 100)));
            out.add(new Issue(PlayerSlot.ADMIN, new Grant(faction, "iron", 100)));
            MvpScene.spawnRoster(out, scale, faction, new Vec2d(x + 52, z + 122), new Vec2d(x + 32, z + 122));
        }
        return out;
    }

    @Override
    public List<Issue> next(MatchQueries q) {
        if (!startedAi && !config.scale().ai().isEmpty() && q.unitCount() >= config.unitsAtStart()) {
            startedAi = true;
            var out = new ArrayList<Issue>();
            for (int f = 0; f < FACTIONS; f++) {
                out.add(new Issue(
                        PlayerSlot.ADMIN,
                        new SetAi(
                                new FactionSlot(f),
                                Levels.PROFILE,
                                config.scale().ai())));
            }
            return out;
        }
        if (q.tick() % REFILL_TICKS != 0) return List.of();

        var out = new ArrayList<Issue>();
        int[] complete = finishedBuildings(q);
        for (int f = 0; f < FACTIONS; f++) {
            var faction = new FactionSlot(f);
            var counts = new java.util.HashMap<TemplateId, Integer>();
            for (int handle : q.unitHandlesOf(faction)) {
                var unit = q.unit(handle);
                if (unit != null) counts.merge(unit.template(), 1, Integer::sum);
            }
            for (RosterPart part : roster) {
                // One worker stays in reserve while the AI is active, so losses between refills
                // do not reduce the time-weighted workload below the exact starting scale.
                int reserve = startedAi && part.workers() ? 1 : 0;
                int need = part.count() + reserve - counts.getOrDefault(part.template(), 0);
                if (need <= 0) continue;
                var yard = new Vec2d(campX(f) + (part.workers() ? 32 : 52), campZ(f) + 122);
                MvpScene.spawn(out, part.template(), faction, yard, need, part.workers() ? 7 : 9);
                unitReplacements += need;
            }
            int missingBuildings = config.scale().buildings() - complete[f];
            for (TownSite site : sites.get(f)) {
                if (missingBuildings <= 0) break;
                if (site.free(q)) {
                    out.add(new Issue(PlayerSlot.ADMIN, site.spawn));
                    missingBuildings--;
                    buildingReplacements++;
                }
            }
        }
        return out;
    }

    public static int[] finishedBuildings(MatchQueries queries) {
        int[] result = new int[FACTIONS];
        for (var building : queries.buildings()) {
            if (building.state() == BuildingStore.COMPLETE) result[building.faction()]++;
        }
        return result;
    }

    public long unitReplacements() {
        return unitReplacements;
    }

    public long buildingReplacements() {
        return buildingReplacements;
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
}
