package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.vision.VisionSystem;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;

/** Fog of war GameTests (FOW-07): pairing respects the visibility index. */
public final class FogTests {
    private static final FactionSlot FACTION_0 = new FactionSlot(0);
    private static final FactionSlot FACTION_1 = new FactionSlot(1);

    private FogTests() {}

    /**
     * FOW-07.2: an enemy unit that was never in vision is never paired with the viewer. Faction 0 at (4, 4), faction 1
     * at (28, 28), default vision 12 blocks — they never see each other. The viewer seated as faction 0 must receive
     * its own faction's body but never faction 1's.
     */
    @StrategosTest(timeoutTicks = 200)
    public static void aHiddenEnemyIsNeverPaired(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, FACTION_0, new Vec2d(4, 4), 1, 0));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, FACTION_1, new Vec2d(28, 28), 1, 0));
        ServerLevel level = h.getLevel();
        var state = new Object() {
            MockViewer viewer;
        };
        h.startSequence()
                .thenWaitUntil(() -> {
                    int[] own = core(rt).queries().unitHandlesOf(FACTION_0);
                    int[] enemy = core(rt).queries().unitHandlesOf(FACTION_1);
                    check(own.length == 1 && enemy.length == 1, "not spawned yet");
                    check(rt.bodies().get(own[0]) != null && rt.bodies().get(enemy[0]) != null, "no bodies yet");
                })
                .thenExecute(() -> {
                    state.viewer = MockViewer.join(
                            level,
                            "fogviewer",
                            rt.space().origin().getX() + 4.0,
                            rt.space().origin().getY() + 2,
                            rt.space().origin().getZ() + 4.0,
                            2);
                    NetFixtures.seat(rt, state.viewer, 0);
                })
                .thenExecuteAfter(40, () -> {
                    state.viewer.poll();
                    state.viewer.moved();
                })
                .thenExecuteAfter(20, () -> {
                    state.viewer.poll();
                    int[] enemy = core(rt).queries().unitHandlesOf(FACTION_1);
                    UnitEntity enemyBody = rt.bodies().get(enemy[0]);
                    check(enemyBody != null, "enemy body missing");
                    int enemyId = enemyBody.getId();
                    Set<Integer> added = new HashSet<>();
                    for (ClientboundAddEntityPacket p : state.viewer.capture().of(ClientboundAddEntityPacket.class)) {
                        added.add(p.getId());
                    }
                    check(!added.contains(enemyId), "the hidden enemy was paired with the viewer");
                    int[] own = core(rt).queries().unitHandlesOf(FACTION_0);
                    UnitEntity ownBody = rt.bodies().get(own[0]);
                    check(ownBody != null && added.contains(ownBody.getId()), "own body was not paired");
                    VisionSystem vision = core(rt).vision();
                    check(vision != null, "no vision");
                    check(!vision.visibility(0).isVisible(enemy[0]), "the enemy should not be visible to faction 0");
                    state.viewer.close();
                })
                .thenSucceed();
    }

    // AFTER-T10: MATCH-07 adds pairsWithin6UnpairsWithin15 once the fog gate filters by visibility.
}
