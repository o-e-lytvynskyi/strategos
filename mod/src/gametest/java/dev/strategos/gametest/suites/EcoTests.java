package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.sim.econ.NodeView;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/** The economy in a real level (M3): resource nodes, gathering, fields. */
public final class EcoTests {
    private EcoTests() {}

    /**
     * ECO-02.4: blocks the {@code strategos:resource_yield} data map names become nodes when the match prepares: a
     * three-log oak a tree of 60 wood, a berry bush a forage node of 60 food; a melon, which the map does not name,
     * none. The map is the only source: the material table holds the log's entry and nothing for the melon.
     */
    @StrategosTest(batch = "eco_nodes", timeoutTicks = 100)
    public static void aResourceYieldEntryMakesANode(GameTestHelper h) {
        for (int y = 2; y <= 4; y++) {
            h.setBlock(new BlockPos(8, y, 8), Blocks.OAK_LOG);
        }
        h.setBlock(new BlockPos(20, 1, 20), Blocks.DIRT); // relative y 1 is the floor layer
        h.setBlock(new BlockPos(20, 2, 20), Blocks.SWEET_BERRY_BUSH);
        h.setBlock(new BlockPos(26, 2, 8), Blocks.MELON);
        var table = StrategosServer.get().materials();
        check(table.of(Blocks.OAK_LOG.defaultBlockState()) != null, "the oak log has no material");
        check(table.of(Blocks.MELON.defaultBlockState()) == null, "the melon has a material");
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        h.startSequence()
                .thenWaitUntil(() -> check(!core(rt).queries().nodes().isEmpty(), "no node yet"))
                .thenExecute(() -> {
                    List<NodeView> nodes = core(rt).queries().nodes();
                    check(nodes.size() == 2, "nodes: " + nodes);
                    NodeView tree = nodes.stream()
                            .filter(n -> n.kind() == MaterialKind.LOG)
                            .findFirst()
                            .orElseThrow();
                    check(
                            tree.blocks() == 3
                                    && tree.amount() == 60_000
                                    && tree.resource().equals("wood"),
                            "the tree " + tree);
                    check(
                            tree.anchorX() == 8.5 && tree.anchorZ() == 8.5 && tree.slots() == 8,
                            "the tree's base " + tree);
                    NodeView bush = nodes.stream()
                            .filter(n -> n.kind() == MaterialKind.FORAGE)
                            .findFirst()
                            .orElseThrow();
                    check(
                            bush.amount() == 60_000
                                    && bush.resource().equals("food")
                                    && bush.subtype().equals("fruit"),
                            "the bush " + bush);
                })
                .thenSucceed();
    }
}
