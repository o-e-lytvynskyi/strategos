package dev.strategos.devtools;

import dev.strategos.core.protocol.MatchResults;
import dev.strategos.core.protocol.MatchStateMsg;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.DiplomacySetup;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.client.StrategosClient;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

/** Terminal-state DTO presentation after actual admission as faction 1: non-first allied win, defeat, and draw. */
final class CoalitionResultScenario implements Scenario {
    private static final String[] FRAMES = {"allied_victory", "defeat", "draw"};
    private int fixture = -1;
    private int age;
    private int captures;
    private int calm;
    private boolean frozen;
    private boolean waitingCapture;
    private boolean failed;

    @Override
    public String name() {
        return "m7-results";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        var rt = ScenarioWorld.matchAtAnchor(server, 32, 0, false);
        rt.initializeDiplomacy(new DiplomacySetup(DiplomacyState.identity(2)
                .withTeam(new dev.strategos.core.shared.FactionSlot(1), 0)
                .lockTeams()
                .snapshot()));
        var player = ScenarioWorld.player(server);
        StrategosServer.get().matches().join(rt, player.getUUID(), new PlayerSlot(1));
        StrategosServer.get().net().joined(rt, player, new PlayerSlot(1));
        var anchor = ScenarioWorld.anchor(server);
        ScenarioWorld.camera(server, anchor.getX() + 0.5, anchor.getY() + 24, anchor.getZ() + 0.5, 0, 80);
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        var join = StrategosClient.match();
        if (tick > 600) {
            ctx.result.addProperty("failure", "terminal presentation stalled");
            ctx.result.addProperty("ok", false);
            return true;
        }
        if (join == null || tick < 40) return false;
        if (!StrategosClient.rts().active()) {
            StrategosClient.rts().enter();
            return false;
        }
        if (!frozen) {
            var server = mc.getSingleplayerServer();
            if (server == null) return false;
            server.execute(() -> ScenarioWorld.run(server, "tick freeze"));
            frozen = true;
        }
        calm = mc.levelRenderer.hasRenderedAllSections() ? calm + 1 : 0;
        if (calm < 20 || ctx.captureName != null) return false;
        if (waitingCapture) {
            waitingCapture = false;
            if (age >= 20) {
                fixture++;
                age = 0;
            }
        }
        if (fixture < 0) fixture = 0;
        if (fixture >= FRAMES.length) {
            ctx.result.addProperty("frames", captures);
            ctx.result.addProperty("primaryFaction", join.slot().value());
            ctx.result.addProperty("source", "decoded terminal-state presentation fixtures after actual admission");
            ctx.result.addProperty("ok", !failed && captures == 6 && join.slot().value() == 1);
            return true;
        }
        if (age == 0) {
            List<Integer> winners = fixture == 0 ? List.of(0, 1) : fixture == 1 ? List.of(0) : List.of();
            var stats = new MatchResults.FactionStats(12, 2, 4, 1, List.of(100L, 80L, 60L, 40L), List.of(0L, 1200L));
            var results = new MatchResults(winners, 2400, List.of(stats, stats));
            var state = new MatchStateMsg(join.matchId(), MatchStateMsg.Lifecycle.ENDED, 2400, results, List.of());
            StrategosClient.state(WireCodec.decode(MatchStateMsg.WIRE, WireCodec.encode(MatchStateMsg.WIRE, state)));
            var end = StrategosClient.model().end();
            failed |= end == null || !end.winners().equals(winners);
            mc.setScreen(null);
        }
        age++;
        if (age == 10 || age == 20) {
            ctx.capture(FRAMES[fixture] + (age == 20 ? "_2" : ""));
            captures++;
            waitingCapture = true;
        }
        return false;
    }
}
