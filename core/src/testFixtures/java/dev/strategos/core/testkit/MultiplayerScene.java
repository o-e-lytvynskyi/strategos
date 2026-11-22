package dev.strategos.core.testkit;

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
import java.util.ArrayList;
import java.util.List;

/** The PERF-10 map has one town and a full MVP roster for each of four primary factions. */
public final class MultiplayerScene implements SoakScene {
    public static final int FACTIONS = 4;
    private static final int CAMP_SEPARATION = 230;
    private static final int CAMP_START = 20;
    private static final int BUILD_GRID = 7;
    private static final int BUILD_STEP = 13;
    private final Config config;
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
            for (int i = 0; i < layout.size(); i++) {
                out.add(new Issue(
                        PlayerSlot.ADMIN,
                        new SpawnBuilding(
                                layout.get(i),
                                faction,
                                x + i % BUILD_GRID * BUILD_STEP,
                                z + i / BUILD_GRID * BUILD_STEP,
                                0)));
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
        // Start the bots after the roster and construction are present, so the measurement can verify the required
        // scale.
        if (startedAi || config.scale().ai().isEmpty() || q.unitCount() < config.unitsAtStart()) return List.of();
        startedAi = true;
        var out = new ArrayList<Issue>();
        for (int f = 0; f < FACTIONS; f++) {
            var faction = new FactionSlot(f);
            out.add(new Issue(
                    PlayerSlot.ADMIN,
                    new SetAi(faction, Levels.PROFILE, config.scale().ai())));
        }
        return out;
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
