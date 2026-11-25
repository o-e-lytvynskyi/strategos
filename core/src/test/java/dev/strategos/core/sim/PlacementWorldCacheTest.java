package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.PlacementRules;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.build.Placing;
import dev.strategos.core.sim.econ.NodeStore;
import dev.strategos.core.testkit.FirstSkirmish;
import dev.strategos.core.testkit.MvpScene;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.SoakScene;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * A site search asks the placement rules of thousands of corners in one tick, so the match's read for them (every
 * footprint, every standing node block) is kept while nothing it is made of moves. The cached answer is always the
 * one a fresh read gives: after a node loses a block within the same tick, and after the tick moves on.
 */
final class PlacementWorldCacheTest {
    private static final TemplateId HOUSE = new TemplateId("strategos:building/house");

    /** What the rules say with a read taken now, as {@code QueriesImpl.placementRefusal} maps it. */
    private static @Nullable String fresh(MatchState s, UnitTemplate t, int x, int z, int faction) {
        var b = s.templates().blueprint(t.netIndex());
        var failed = PlacementRules.check(
                        s.civs().rules(faction).placement(),
                        Placing.world(s, faction),
                        b,
                        Footprint.of(b, x, z, 0),
                        faction,
                        Placing.params(s, t.netIndex(), faction))
                .failed();
        return failed == null || failed == PlacementRules.Rule.AFFORDABLE ? null : failed.name();
    }

    private static List<String> asked(MatchQueries q, MatchState s, UnitTemplate t, List<int[]> corners) {
        List<String> out = new ArrayList<>();
        for (int[] c : corners) {
            out.add(String.valueOf(q.placementRefusal(t.netIndex(), c[0], c[1], 0)));
        }
        return out;
    }

    private static List<String> freshAll(MatchState s, UnitTemplate t, List<int[]> corners) {
        List<String> out = new ArrayList<>();
        for (int[] c : corners) {
            out.add(String.valueOf(fresh(s, t, c[0], c[1], 0)));
        }
        return out;
    }

    @Test
    void theCachedReadFollowsNodesThatLoseBlocksWithinATick() throws IOException {
        FirstSkirmish map = FirstSkirmish.generate();
        String skirmish = Files.readString(Path.of(System.getProperty("strategos.repoRoot"))
                .resolve("mod/src/main/resources/data/strategos/strategos/ai/skirmish.json"));
        SimHarness h = SimHarness.builder()
                .size(FirstSkirmish.SIZE, FirstSkirmish.SIZE)
                .yBand(0, FirstSkirmish.HEIGHT, 16)
                .seed(SimHarness.DEFAULT_SEED)
                .rules(MatchRules.DEFAULTS.withPlay(PlayRules.DEFAULTS))
                .starts(map.startSlots())
                .build();
        h.publishAll(map.voxels());
        h.step();
        MvpScene scene = new MvpScene(MvpScene.Config.SOAK.withAi(skirmish), map.startSlots());
        for (SoakScene.Issue i : scene.setup(TemplateSet.builtin())) {
            h.submit(i.issuer(), i.command());
        }
        for (int t = 0; t < 120; t++) {
            h.step();
            for (SoakScene.Issue i : scene.next(h.core().queries())) {
                h.submit(i.issuer(), i.command());
            }
        }
        MatchState s = KernelAccess.state(h.core());
        MatchQueries q = h.core().queries();
        UnitTemplate house = s.templates().byId(HOUSE);
        assertThat(house).isNotNull();

        // corners over a grid of the first start's ground, and on every node block that stands
        List<int[]> corners = new ArrayList<>();
        NodeStore nodes = s.nodes();
        for (int id = 1; id <= nodes.count(); id++) {
            long[] blocks = nodes.blocks(id);
            for (int k = nodes.gone(id); k < blocks.length; k++) {
                corners.add(new int[] {NodeStore.x(blocks[k]), NodeStore.z(blocks[k])});
            }
        }
        int cx = (int) map.startSlots().get(0).x();
        int cz = (int) map.startSlots().get(0).z();
        for (int dx = -40; dx <= 40; dx += 4) {
            for (int dz = -40; dz <= 40; dz += 4) {
                corners.add(new int[] {cx + dx, cz + dz});
            }
        }

        List<String> before = asked(q, s, house, corners);
        assertThat(before).isEqualTo(freshAll(s, house, corners));

        long moved = nodes.blockChanges();
        for (int id = 1; id <= nodes.count(); id++) {
            nodes.removeTop(id); // the same tick: no step between the two asks
        }
        assertThat(nodes.blockChanges()).isGreaterThan(moved);
        List<String> after = asked(q, s, house, corners);
        assertThat(after).isEqualTo(freshAll(s, house, corners));
        assertThat(after)
                .as("felling opened ground the cache must not keep closed")
                .isNotEqualTo(before);

        h.step();
        assertThat(asked(q, s, house, corners)).isEqualTo(freshAll(s, house, corners));
    }
}
