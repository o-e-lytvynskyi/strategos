package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.SetStance;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Stance;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A small scene for the late-game managers' L1 tests (AI-09): a flat 160 by 160 map, the own civic centre at (20, 20)
 * and the enemy's at (120, 120), the AI seeing all. The scripts are a bare one plus a {@code depth} block each test
 * names; the shipped script carries no depth block for now.
 */
final class DepthScene {
    static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    static final TemplateId STOREHOUSE = new TemplateId("strategos:building/storehouse");
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    static final TemplateId BARRACKS = new TemplateId("strategos:building/barracks");
    static final TemplateId TEMPLE = new TemplateId("strategos:building/temple");
    static final TemplateId HEALER = new TemplateId("strategos:unit/healer");
    static final TemplateId INFANTRY = new TemplateId("strategos:unit/infantry");
    static final TemplateId WORKER = new TemplateId("strategos:unit/worker");
    static final TemplateId HORSEMAN = new TemplateId("strategos:unit/horseman");
    static final FactionSlot OWN = new FactionSlot(0);
    static final FactionSlot ENEMY = new FactionSlot(1);

    /** A depth block of the shipped script's shape (its values, but the tree's list cut to what a test names). */
    static String depth(String techs) {
        return """
                "depth": {
                  "techs": %s,
                  "healers": 3,
                  "charge": { "ability": "charge", "range": 14 },
                  "march": 70,
                  "veterans": { "rank": 1, "below": 0.35, "resume": 0.8, "near": 60 },
                  "hold": 6000, "phaseHold": 6000, "techHold": 100
                }""".formatted(techs);
    }

    static StrategyScript script(String depthBlock) {
        return script(depthBlock, "");
    }

    /** As {@link #script(String)} with the build order's {@code steps} (JSON objects, comma-separated). */
    static StrategyScript script(String depthBlock, String steps) {
        // a bare script with the battle's tactics on, and the depth block (if any) at its end
        return StrategyScript.parse("""
            { "maxWorkers": 0, "ratio": {}, "build": [%s], "housingMargin": 0, "soldierQueue": 3, "reserve": true,
              "attack": { "first": 100000, "every": 600, "min": 1, "muster": 24, "announce": 30 },
              "battle": { "focus": "wounded", "behind": 4, "retreat": 0.6, "reach": 28, "defence": 600 },
              "sites": []%s }""".formatted(steps, depthBlock.isEmpty() ? "" : "," + depthBlock));
    }

    /** The script without any depth block, as the shipped one is for now. */
    static StrategyScript plain() {
        return script("");
    }

    final SimHarness h = SimHarness.builder()
            .size(160, 160)
            .yBand(0, 8, 1)
            .factions(2)
            .rules(MatchRules.DEFAULTS.withPlay(HeadlessMatch.EXPLORED))
            .build();

    DepthScene() {
        h.publishAll(VoxelFixtures.flat(160, 160, 8, 1));
        h.step();
        building(CENTRE, OWN, 20, 20);
        building(CENTRE, ENEMY, 120, 120);
    }

    void building(TemplateId t, FactionSlot f, int x, int z) {
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(t, f, x, z, 0));
        h.step();
    }

    /** {@code n} passive units of {@code t} for {@code f} around ({@code x}, {@code z}); their handles. */
    int[] spawn(TemplateId t, FactionSlot f, double x, double z, int n) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(t, f, new Vec2d(x, z), n, 2));
        List<Integer> out = new ArrayList<>();
        h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .forEach(e -> out.add(((UnitCreated) e).handle()));
        int[] handles = out.stream().mapToInt(Integer::intValue).toArray();
        assertThat(handles).hasSize(n);
        h.submit(new PlayerSlot(f.value()), new SetStance(UnitSet.of(handles), Stance.PASSIVE));
        h.step();
        return handles;
    }

    void grant(long food, long wood, long stone, long iron) {
        String[] names = {"food", "wood", "stone", "iron"};
        long[] amounts = {food, wood, stone, iron};
        for (int r = 0; r < 4; r++) {
            if (amounts[r] > 0) {
                h.submit(PlayerSlot.ADMIN, new Grant(OWN, names[r], amounts[r]));
            }
        }
        h.step();
    }

    void ticks(int n) {
        for (int i = 0; i < n; i++) {
            h.step();
        }
    }

    /** What a think of a manager does to the scene. */
    interface Think {
        void run(AiView view, AiBudget budget, CommandSink sink);
    }

    /** The commands one think of the own computer player sends, over the queries as they are. */
    List<Command> think(Think think) {
        return think(think, Map.of(), Map.of());
    }

    /**
     * The same over queries that say {@code ranks} (unit handle to rank) and {@code health} (handle to a fraction of its
     * most) for the units they name: the sim's own experience and damage are other tests' business.
     */
    List<Command> think(Think think, Map<Integer, Integer> ranks, Map<Integer, Double> health) {
        MatchQueries real = h.core().queries();
        MatchQueries q = (MatchQueries) Proxy.newProxyInstance(
                MatchQueries.class.getClassLoader(), new Class<?>[] {MatchQueries.class}, (proxy, m, args) -> {
                    if (m.getName().equals("rank") && args != null && ranks.containsKey((Integer) args[0])) {
                        return ranks.get((Integer) args[0]);
                    }
                    if (m.getName().equals("hitPoints") && args != null && health.containsKey((Integer) args[0])) {
                        return health.get((Integer) args[0]) * real.maxHitPoints((Integer) args[0]);
                    }
                    return m.invoke(real, args);
                });
        List<Command> sent = new ArrayList<>();
        AiBudget budget = new AiBudget(AiBudget.CAP);
        think.run(new AiView(q, OWN.value(), budget), budget, sent::add);
        return sent;
    }

    static Map<Integer, Integer> ranked(int rank, int... handles) {
        Map<Integer, Integer> m = new HashMap<>();
        for (int u : handles) {
            m.put(u, rank);
        }
        return m;
    }

    static Map<Integer, Double> healthy(double fraction, int... handles) {
        Map<Integer, Double> m = new HashMap<>();
        for (int u : handles) {
            m.put(u, fraction);
        }
        return m;
    }
}
