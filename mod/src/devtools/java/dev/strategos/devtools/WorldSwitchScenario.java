package dev.strategos.devtools;

import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.jspecify.annotations.Nullable;

/**
 * CORE-01.5: a saved match in world A, Save and Quit, then world B: the holder is a new instance, and no match and no
 * unit body carried over.
 */
final class WorldSwitchScenario implements Scenario {
    private final AtomicReference<@Nullable StrategosServer> holderA = new AtomicReference<>();
    private final AtomicInteger bodiesA = new AtomicInteger();
    private volatile @Nullable String verdictB;
    private volatile boolean inB;
    private @Nullable MatchRuntime matchA;
    private boolean switched;

    @Override
    public String name() {
        return "world_switch";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        if (!switched) {
            matchA = ScenarioWorld.matchAtPlayer(server, 16, 3, true);
            holderA.set(StrategosServer.get());
            return;
        }
        StrategosServer holderB = StrategosServer.get();
        int matches = holderB.matches().all().size();
        int bodies = server.overworld()
                .getEntities(EntityTypeTest.forClass(UnitEntity.class), e -> true)
                .size();
        if (holderB == holderA.get()) {
            verdictB = "world B kept world A's holder";
        } else if (matches != 0 || bodies != 0) {
            verdictB = "world B has " + matches + " matches and " + bodies + " unit bodies";
        } else {
            verdictB = "";
        }
        inB = true;
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        if (!switched) {
            IntegratedServer server = mc.getSingleplayerServer();
            MatchRuntime m = matchA;
            if (server != null && m != null) {
                server.execute(() -> bodiesA.set(m.bodies().count()));
            }
            if (bodiesA.get() == 3) {
                switched = true;
                ctx.switchWorld("strategos-world-b", 43);
            }
            return false;
        }
        if (!inB) {
            return false;
        }
        String verdict = verdictB;
        ctx.result.addProperty("ok", verdict != null && verdict.isEmpty());
        if (verdict != null && !verdict.isEmpty()) {
            ctx.result.addProperty("reason", verdict);
        }
        return true;
    }
}
